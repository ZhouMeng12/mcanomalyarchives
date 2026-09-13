package net.mcreator.mcanomalyarchives.dialogue;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.codex.AnomalyCodex;
import net.mcreator.mcanomalyarchives.codex.CodexOriginStory;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.network.DialogueChoicePacket;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 剧情运行时：按 {@link Story} 逐节点推进（台词 / 跳转 / 选项 / 动作 / 停顿）。
 *
 * <p>台词交给 {@link DialogueManager} 播放；需要玩家选择时发
 * {@link DialogueChoicePacket}（客户端弹屏幕中间的按钮），等 {@link #onAnswer} 回来继续。
 *
 * <p>动作目前支持：{@code take_book}（收走一本普通的书）、{@code give_codex}（给出《见闻录》）、
 * {@code sound:<音效id>}、{@code unlock:<UO编号>}（解锁见闻录里的一条档案）。
 *
 * 【工程层归属】dialogue 包（非 MCreator 生成区）。
 */
public final class StoryPlayer {

	/** 等待中的选项：玩家 → (选项编号, 回调) */
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
	/** 正在看剧情的玩家（防连点开第二遍） */
	private static final java.util.Set<UUID> RUNNING = ConcurrentHashMap.newKeySet();
	private static final AtomicInteger NEXT_CHOICE_ID = new AtomicInteger(1);
	/** 选项超时（tick）：3 分钟没点就当放弃 */
	private static final int CHOICE_TIMEOUT = 3600;

	private record Pending(int choiceId, List<Story.Option> options, String storyName, Entity speaker) {
	}

	private StoryPlayer() {
	}

	/** 这个玩家是不是正在过剧情 */
	public static boolean isRunning(ServerPlayer player) {
		return RUNNING.contains(player.getUUID());
	}

	/**
	 * 给某个玩家播一个故事。
	 *
	 * @return true = 已开始（false 表示同名剧情已在跑，或脚本不可用）
	 */
	public static boolean play(ServerPlayer player, Entity speaker, String storyName) {
		if (RUNNING.contains(player.getUUID())) {
			return false;
		}
		Story story = StoryLoader.get(storyName);
		if (story == null || !story.isUsable()) {
			DialogueLog.warn(storyName, "脚本不可用，剧情未启动");
			return false;
		}
		RUNNING.add(player.getUUID());
		run(player, speaker, storyName, story, story.nodes(Story.START_LABEL), 0);
		return true;
	}

	/** 强制停止（玩家取消 / 超时） */
	public static void stop(ServerPlayer player) {
		PENDING.remove(player.getUUID());
		RUNNING.remove(player.getUUID());
	}

	// ===== 节点推进 =====

	private static void run(ServerPlayer player, Entity speaker, String storyName, Story story,
			List<Story.Node> nodes, int index) {
		if (!player.isAlive()) {
			stop(player);
			return;
		}
		for (int i = index; i < nodes.size(); i++) {
			Story.Node node = nodes.get(i);
			final int next = i + 1;
			switch (node.kind()) {
				case LINE -> {
					DialogueLine line = node.image() != null && !node.image().isBlank()
							? DialogueLine.withImage(node.text(), node.image())
							: DialogueLine.text(node.text());
					DialogueManager.speakTo(player, speaker, line);
					// 这条播完再继续（+4 tick 让气泡衔接自然）
					McanomalyarchivesMod.queueServerWork(Math.max(20, line.durationTicks()) + 4,
							() -> run(player, speaker, storyName, story, nodes, next));
					return;
				}
				case WAIT -> {
					McanomalyarchivesMod.queueServerWork(node.ticks(),
							() -> run(player, speaker, storyName, story, nodes, next));
					return;
				}
				case JUMP -> {
					List<Story.Node> target = story.nodes(node.text());
					if (target.isEmpty()) {
						DialogueLog.warn(storyName, "跳转目标为空：" + node.text() + "，剧情结束");
						stop(player);
						return;
					}
					run(player, speaker, storyName, story, target, 0);
					return;
				}
				case CHOICE -> {
					askChoice(player, speaker, storyName, node);
					return;
				}
				case ACTION -> performAction(player, speaker, node.text());
				case END -> {
					stop(player);
					return;
				}
			}
		}
		// 段落跑完没有显式 [end]：视为结束
		stop(player);
	}

	// ===== 选项 =====

	private static void askChoice(ServerPlayer player, Entity speaker, String storyName, Story.Node node) {
		if (node.options().isEmpty()) {
			DialogueLog.warn(storyName, "选项块没有任何选项，已跳过");
			return;
		}
		int id = NEXT_CHOICE_ID.incrementAndGet();
		PENDING.put(player.getUUID(), new Pending(id, node.options(), storyName, speaker));
		PacketDistributor.sendToPlayer(player,
				DialogueChoicePacket.of(id, node.prompt(), node.options().stream().map(Story.Option::text).toList()));
		McanomalyarchivesMod.queueServerWork(CHOICE_TIMEOUT, () -> {
			Pending pending = PENDING.get(player.getUUID());
			if (pending != null && pending.choiceId() == id) {
				DialogueLog.info("选项超时，剧情中止：" + storyName);
				stop(player);
			}
		});
	}

	/** 由网络包调用：玩家点了第 index 项（-1 = 取消） */
	public static void onAnswer(ServerPlayer player, int choiceId, int index) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null || pending.choiceId() != choiceId) {
			return; // 超时或重复点击
		}
		PENDING.remove(player.getUUID());
		if (index < 0 || index >= pending.options().size()) {
			stop(player); // 取消
			return;
		}
		Story.Option chosen = pending.options().get(index);
		Story story = StoryLoader.get(pending.storyName());
		run(player, pending.speaker(), pending.storyName(), story, story.nodes(chosen.target()), 0);
	}

	// ===== 动作 =====

	private static void performAction(ServerPlayer player, Entity speaker, String action) {
		String name = action;
		String arg = "";
		int colon = action.indexOf(':');
		if (colon > 0) {
			name = action.substring(0, colon).trim();
			arg = action.substring(colon + 1).trim();
		}
		switch (name) {
			case "take_book" -> consumeOneBook(player);
			case "give_codex" -> giveCodex(player);
			case "sound" -> playSound(speaker, arg);
			case "unlock" -> unlockEntry(player, arg);
			default -> DialogueLog.warn("action", "未知动作：" + action);
		}
	}

	private static void consumeOneBook(ServerPlayer player) {
		if (player.getAbilities().instabuild) {
			return;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && (stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK))) {
				stack.shrink(1);
				return;
			}
		}
	}

	private static void giveCodex(ServerPlayer player) {
		ItemStack codex = new ItemStack(McanomalyarchivesModItems.ANOMALY_CODEX.get());
		if (!player.getInventory().add(codex)) {
			player.drop(codex, false, false);
		}
		CodexOriginStory.markReceived(player);
	}

	private static void playSound(@Nullable Entity speaker, String id) {
		if (speaker == null || id.isBlank()) {
			return;
		}
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(id));
		if (sound == null) {
			DialogueLog.warn("sound", "找不到音效：" + id);
			return;
		}
		speaker.level().playSound(null, speaker.blockPosition(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	private static void unlockEntry(ServerPlayer player, String id) {
		int index = AnomalyCodex.indexOf(id);
		if (index < 0) {
			DialogueLog.warn("unlock", "见闻录里没有这个编号：" + id);
			return;
		}
		AnomalyCodex.unlock(player, index, player.serverLevel());
	}
}
