package net.mcreator.mcanomalyarchives.codex;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.dialogue.DialogueDatabase;
import net.mcreator.mcanomalyarchives.dialogue.DialogueLine;
import net.mcreator.mcanomalyarchives.dialogue.DialogueManager;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.network.DialogueChoicePacket;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 见闻录的获取途径：**拿着空白的书去找斯万，过完剧情拿到《见闻录》**。
 *
 * <p>台词全部在 {@link DialogueDatabase} 里（照该文件"台词集中存放"的约定），
 * 这里只负责调度：播完一段 → 需要选择就发 {@link DialogueChoicePacket} 等玩家点 →
 * 拿到回答继续下一段 → 最后消耗一本普通的书，给出见闻录。
 *
 * <p>流程（两处选择，都在屏幕中间弹按钮）：
 * <pre>
 *   开场 → 选项A（云 / 树 / 花）→ 分支回答 → 汇合 → 选项B（我来记 / 记得住又怎样）
 *        → 「把你的书给我」→ 消耗 1 本书 → 交付《见闻录》
 * </pre>
 *
 * 【工程层归属】codex 包（非 MCreator 生成区）。
 */
public final class CodexOriginStory {

	/** 玩家 persistentData：是否已经拿到过见闻录 */
	private static final String TAG_GIVEN = "McanomalyCodexGiven";

	/** 等待中的选择：玩家 → 选择编号 */
	private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();
	/** 正在过剧情的玩家（防止连点开第二遍，两段对话交错） */
	private static final java.util.Set<UUID> IN_STORY = ConcurrentHashMap.newKeySet();
	/** 选择编号自增 */
	private static final AtomicInteger NEXT_CHOICE_ID = new AtomicInteger(1);
	/** 选择超时（tick）：3 分钟没点就当放弃，避免状态永久挂着 */
	private static final int CHOICE_TIMEOUT = 3600;

	private CodexOriginStory() {
	}

	// ===== 入口 =====

	/** 玩家手里拿的是不是"可以写成见闻录的书" */
	public static boolean isBlankBook(ItemStack stack) {
		return !stack.isEmpty()
				&& (stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK));
	}

	/** 是否已经拿过见闻录 */
	public static boolean hasReceived(ServerPlayer player) {
		return player.getPersistentData().getBoolean(TAG_GIVEN);
	}

	/**
	 * 玩家拿着书点了斯万：开始剧情（或走"已经拿过"的短台词）。
	 *
	 * @return true 表示这次交互被本剧情接管了
	 */
	public static boolean tryStart(ServerPlayer player, Entity svan) {
		// 剧情进行中：忽略再次右键，否则会开第二遍、两段对话交错
		if (IN_STORY.contains(player.getUUID())) {
			return true;
		}
		// 已经拿过、且书还在身上 → 只打个招呼
		if (hasReceived(player) && player.getInventory().contains(new ItemStack(McanomalyarchivesModItems.ANOMALY_CODEX.get()))) {
			speakTo(player, svan, DialogueDatabase.CODEX_ALREADY, null);
			return true;
		}
		// 已经拿过但书丢了 → 直接再给一本（不让玩家因为弄丢而卡住）
		if (hasReceived(player)) {
			speakTo(player, svan, DialogueDatabase.CODEX_ALREADY, () -> giveCodex(player, svan, false));
			return true;
		}
		// 首次：完整剧情
		IN_STORY.add(player.getUUID());
		speakTo(player, svan, DialogueDatabase.CODEX_INTRO, () -> askChoiceA(player, svan));
		return true;
	}

	// ===== 剧情推进 =====

	private static void askChoiceA(ServerPlayer player, Entity svan) {
		askChoice(player, DialogueDatabase.CODEX_CHOICE_A_PROMPT, DialogueDatabase.CODEX_CHOICE_A_OPTIONS,
				index -> {
					List<DialogueLine> branch = DialogueDatabase.CODEX_BRANCH_A
							.get(Math.max(0, Math.min(DialogueDatabase.CODEX_BRANCH_A.size() - 1, index)));
					speakTo(player, svan, branch, () -> speakTo(player, svan, DialogueDatabase.CODEX_MIDDLE,
							() -> askChoiceB(player, svan)));
				});
	}

	private static void askChoiceB(ServerPlayer player, Entity svan) {
		askChoice(player, DialogueDatabase.CODEX_CHOICE_B_PROMPT, DialogueDatabase.CODEX_CHOICE_B_OPTIONS,
				index -> {
					List<DialogueLine> reply = DialogueDatabase.CODEX_BRANCH_B
							.get(Math.max(0, Math.min(DialogueDatabase.CODEX_BRANCH_B.size() - 1, index)));
					speakTo(player, svan, reply, () -> giveCodex(player, svan, true));
				});
	}

	/**
	 * 交付见闻录。
	 *
	 * @param consumeBook 首次剧情为 true：先播「把你的书给我」→ 真收走一本书（带附魔台音效）
	 *                    → 再播剩下的交付台词 → 给书；补发（书弄丢了）为 false：直接给
	 */
	private static void giveCodex(ServerPlayer player, Entity svan, boolean consumeBook) {
		List<DialogueLine> give = DialogueDatabase.CODEX_GIVE;
		if (!consumeBook) {
			handOverBook(player, svan, false);
			finalizeGift(player);
			return;
		}
		// 先播「把你的书给我」，播完才动手（不然台词刚出口书就没了，观感很怪）
		speakTo(player, svan, give.subList(0, 1), () -> {
			handOverBook(player, svan, true);
			speakTo(player, svan, give.subList(1, give.size()), () -> finalizeGift(player));
		});
	}

	/** 收走一本普通的书（首次剧情），并播放"书被改造"的音效 */
	private static void handOverBook(ServerPlayer player, Entity svan, boolean consumeBook) {
		if (!consumeBook) {
			return;
		}
		consumeOneBook(player);
		if (svan.level() instanceof ServerLevel level) {
			level.playSound(null, svan.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1.0F,
					1.2F);
		}
	}

	/** 给出见闻录 + 记录标志 + 提示音 */
	private static void finalizeGift(ServerPlayer player) {
		ItemStack codex = new ItemStack(McanomalyarchivesModItems.ANOMALY_CODEX.get());
		if (!player.getInventory().add(codex)) {
			player.drop(codex, false, false); // 背包满了就掉在脚边
		}
		player.getPersistentData().putBoolean(TAG_GIVEN, true);
		player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
		IN_STORY.remove(player.getUUID());
	}

	/** 收走一本普通的书 / 成书（创造模式不收） */
	private static void consumeOneBook(ServerPlayer player) {
		if (player.getAbilities().instabuild) {
			return;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (isBlankBook(stack)) {
				stack.shrink(1);
				return;
			}
		}
	}

	// ===== 选择：发送 + 收取 =====

	private interface ChoiceHandler {
		void accept(int index);
	}

	private static void askChoice(ServerPlayer player, String prompt, List<String> options, ChoiceHandler handler) {
		int id = NEXT_CHOICE_ID.incrementAndGet();
		PENDING.put(player.getUUID(), id);
		PacketDistributor.sendToPlayer(player, DialogueChoicePacket.of(id, prompt, options));
		// 超时兜底：到点还没回答就清掉状态（剧情就地停住，玩家可以再找他重新开始）
		McanomalyarchivesMod.queueServerWork(CHOICE_TIMEOUT, () -> {
			Integer current = PENDING.get(player.getUUID());
			if (current != null && current == id) {
				PENDING.remove(player.getUUID());
				IN_STORY.remove(player.getUUID()); // 超时视为放弃，解锁让他能重新开始
			}
		});
		HANDLERS.put(id, handler);
	}

	/** 选择编号 → 后续剧情 */
	private static final Map<Integer, ChoiceHandler> HANDLERS = new ConcurrentHashMap<>();

	/** 由网络包调用：玩家点了第 index 个选项（-1 = 取消） */
	public static void onAnswer(ServerPlayer player, int choiceId, int index) {
		Integer pending = PENDING.get(player.getUUID());
		if (pending == null || pending != choiceId) {
			return; // 不是当前等待的那次选择（超时/重复点击）
		}
		PENDING.remove(player.getUUID());
		ChoiceHandler handler = HANDLERS.remove(choiceId);
		if (handler == null) {
			return;
		}
		if (index < 0) {
			IN_STORY.remove(player.getUUID()); // 取消：剧情停下，可以重新右键开始
			return;
		}
		handler.accept(index);
	}

	// ===== 工具 =====

	/** 播一段台词，播完（按台词总时长估算）后执行 next */
	private static void speakTo(ServerPlayer player, Entity speaker, List<DialogueLine> lines, Runnable next) {
		if (lines == null || lines.isEmpty()) {
			if (next != null) {
				next.run();
			}
			return;
		}
		DialogueManager.speakTo(player, speaker, lines.toArray(new DialogueLine[0]));
		if (next == null) {
			return;
		}
		int total = 0;
		for (DialogueLine line : lines) {
			total += line.durationTicks();
		}
		McanomalyarchivesMod.queueServerWork(total + 12, next); // +12 tick 让最后一条多留一会儿
	}
}
