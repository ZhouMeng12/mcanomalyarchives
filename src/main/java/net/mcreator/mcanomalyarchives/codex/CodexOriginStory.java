package net.mcreator.mcanomalyarchives.codex;

import net.mcreator.mcanomalyarchives.dialogue.DialogueDatabase;
import net.mcreator.mcanomalyarchives.dialogue.DialogueLine;
import net.mcreator.mcanomalyarchives.dialogue.DialogueManager;
import net.mcreator.mcanomalyarchives.dialogue.StoryPlayer;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 《见闻录》的获取途径：**拿着空白的书去找斯万，过完剧情拿到手**。
 *
 * <p>本类现在只做三件事（剧情本体已经搬到可编辑的脚本文件里了）：
 * <ol>
 * <li>判断手上是不是"可以写成见闻录的书"；</li>
 * <li>已经拿过的人：书还在 → 一句短台词；书弄丢了 → 直接补发（不让玩家卡住）；</li>
 * <li>首次 → 交给 {@link StoryPlayer} 跑 {@code svan_codex} 剧情脚本。</li>
 * </ol>
 *
 * <p><b>剧情写在哪：</b>{@code config/mcanomalyarchives/stories/svan_codex.txt}
 * （首次启动自动生成，含全部台词与分支）。改完存盘，游戏里约 5 秒自动生效，
 * 不用重启也不用重新编译。语法见该文件开头的注释。
 *
 * 【工程层归属】codex 包（非 MCreator 生成区）。
 */
public final class CodexOriginStory {

	/** 斯万剧情脚本的文件名（config/mcanomalyarchives/stories/&lt;这个名字&gt;.txt） */
	public static final String STORY_NAME = "svan_codex";

	/** 玩家 persistentData：是否已经拿到过见闻录 */
	private static final String TAG_GIVEN = "McanomalyCodexGiven";

	private CodexOriginStory() {
	}

	/** 玩家手里拿的是不是"可以写成见闻录的书" */
	public static boolean isBlankBook(ItemStack stack) {
		return !stack.isEmpty()
				&& (stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK));
	}

	/** 是否已经拿过见闻录 */
	public static boolean hasReceived(ServerPlayer player) {
		return player.getPersistentData().getBoolean(TAG_GIVEN);
	}

	/** 记录"已经拿过"（由剧情脚本的 [give_codex] 动作经 StoryPlayer 调用） */
	public static void markReceived(ServerPlayer player) {
		player.getPersistentData().putBoolean(TAG_GIVEN, true);
		player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
	}

	/**
	 * 玩家拿着书点了斯万。
	 *
	 * @return true 表示这次交互被本剧情接管了
	 */
	public static boolean tryStart(ServerPlayer player, Entity svan) {
		// 剧情进行中：忽略再次右键（否则会开第二遍，两段对话交错）
		if (StoryPlayer.isRunning(player)) {
			return true;
		}
		// 已经拿过、且书还在身上 → 只打个招呼
		if (hasReceived(player)
				&& player.getInventory().contains(new ItemStack(McanomalyarchivesModItems.ANOMALY_CODEX.get()))) {
			List<DialogueLine> lines = DialogueDatabase.CODEX_ALREADY;
			DialogueManager.speakTo(player, svan, lines.toArray(new DialogueLine[0]));
			return true;
		}
		// 已经拿过但书丢了 → 直接补发一本，不再走一遍剧情
		if (hasReceived(player)) {
			ItemStack codex = new ItemStack(McanomalyarchivesModItems.ANOMALY_CODEX.get());
			if (!player.getInventory().add(codex)) {
				player.drop(codex, false, false);
			}
			player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
			return true;
		}
		// 首次：跑外部剧情脚本
		return StoryPlayer.play(player, svan, STORY_NAME);
	}
}
