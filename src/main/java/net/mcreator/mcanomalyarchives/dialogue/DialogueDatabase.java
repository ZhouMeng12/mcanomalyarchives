package net.mcreator.mcanomalyarchives.dialogue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * =====================================================================
 *  所有对话台词都集中在这个文件里 —— 改台词只改这里。
 *  图片路径约定：相对 textures/ 目录、不带 .png 后缀，
 *  例如 "mcanomalyarchives:item/orange" → assets/mcanomalyarchives/textures/item/orange.png
 * =====================================================================
 */
public final class DialogueDatabase {

	private DialogueDatabase() {
	}

	/** 打招呼台词：实体注册名 → 台词列表（每次随机抽一条） */
	public static final Map<ResourceLocation, List<DialogueLine>> GREETINGS = new LinkedHashMap<>();

	/** 送礼台词：实体注册名 → (物品 → 台词列表)（每次随机抽一条） */
	public static final Map<ResourceLocation, Map<Item, List<DialogueLine>>> GIFT_LINES = new LinkedHashMap<>();

	private static ResourceLocation key(String name) {
		return ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, name);
	}

	static {
		// ==================== 打招呼台词 ====================

		GREETINGS.put(key("david"), List.of(
				DialogueLine.text("你好，我是大卫。"),
				DialogueLine.text("今天天气不错。"),
				DialogueLine.text("有什么事吗？")
		));

		GREETINGS.put(key("yifulin"), List.of(
				DialogueLine.text("你好，我是伊芙琳。"),
				DialogueLine.text("欢迎回来。"),
				DialogueLine.text("你看起来有点累，注意休息。")
		));

		GREETINGS.put(key("potter"), List.of(
				DialogueLine.text("你好，我是波特。"),
				DialogueLine.text("别离虞美人太近……"),
				DialogueLine.text("这里的雾总是散不去。")
		));

		GREETINGS.put(key("svan"), List.of(
				DialogueLine.withImage("你好，我是斯万。", "mcanomalyarchives:item/orange"),
				DialogueLine.text("要尝尝橘子吗？"),
				DialogueLine.text("你看起来是个好人。")
		));

		GREETINGS.put(key("anbula"), List.of(
				DialogueLine.text("……"),
				DialogueLine.text("你来了。"),
				DialogueLine.text("别惹我。")
		));

		// ==================== 送礼台词 ====================

		Map<Item, List<DialogueLine>> svanGifts = new LinkedHashMap<>();
		svanGifts.put(McanomalyarchivesModItems.ORANGE.get(), List.of(
				DialogueLine.withImage("谢谢你的橘子！", "mcanomalyarchives:item/orange"),
				DialogueLine.text("这橘子真甜！"),
				DialogueLine.text("橘子真好吃~"),
				DialogueLine.text("好久没吃过这么新鲜的橘子了"),
				DialogueLine.text("你人真好！")
		));
		GIFT_LINES.put(key("svan"), svanGifts);

		// ==================== 见闻录：斯万剧情 ====================
		// 拿一本普通的书去找斯万，他会把书"做"成见闻录。
		// 流程与分支的调度在 codex/CodexOriginStory.java，这里只存台词。

		CODEX_INTRO = List.of(
				DialogueLine.withImage("……你手里那本，是空白的。", "mcanomalyarchives:item/anomalycodex"),
				DialogueLine.text("你见过它们了，对吧？"));

		CODEX_BRANCH_A = List.of(
				// 云
				List.of(
						DialogueLine.text("那不是云。"),
						DialogueLine.text("它有三副样子：温顺的、会变的，还有一种专挑低处出现。"),
						DialogueLine.text("它不怕你，它怕被记下来。")),
				// 树
				List.of(
						DialogueLine.text("山里那棵黑的，是活的。"),
						DialogueLine.text("你砍得越多，它离你越近。"),
						DialogueLine.text("砍一棵，种一棵——这是唯一的规矩。")),
				// 花
				List.of(
						DialogueLine.text("虞美人。别直视它。"),
						DialogueLine.text("被它迷住的人，最后死在花边上，脸上还会长出新的花。"),
						DialogueLine.text("看一眼就够，别停。")));

		CODEX_MIDDLE = List.of(
				DialogueLine.text("……问得好。"),
				DialogueLine.text("但你问到的，都只是碎片。"),
				DialogueLine.text("真正的麻烦不是它们。是没人记得。"));

		CODEX_BRANCH_B = List.of(
				List.of(DialogueLine.text("好。")),
				List.of(DialogueLine.text("至少你不会死得不明不白。")));

		CODEX_GIVE = List.of(
				DialogueLine.text("把你的书给我。"),
				DialogueLine.text("……成了。"),
				DialogueLine.withImage("《见闻录》。往后你见过什么，它自己会写。", "mcanomalyarchives:item/anomalycodex"),
				DialogueLine.text("别弄丢了。它不是只给你一个人的。"));

		CODEX_ALREADY = List.of(
				DialogueLine.text("书在你这儿，我就放心了。"),
				DialogueLine.text("别弄丢了。"));
	}

	// ==================== 见闻录剧情台词 ====================

	/** 开场（拿书找斯万） */
	public static final List<DialogueLine> CODEX_INTRO;
	/** 第一个选项：想问哪一件（下标与 CODEX_BRANCH_A 对应） */
	public static final String CODEX_CHOICE_A_PROMPT = "你想先听哪一件？";
	public static final List<String> CODEX_CHOICE_A_OPTIONS = List.of("天上那些云", "会走路的树", "那朵花");
	/** 三个分支的回答 */
	public static final List<List<DialogueLine>> CODEX_BRANCH_A;
	/** 汇合段 */
	public static final List<DialogueLine> CODEX_MIDDLE;
	/** 第二个选项：你打算怎么办 */
	public static final String CODEX_CHOICE_B_PROMPT = "……那你打算怎么办？";
	public static final List<String> CODEX_CHOICE_B_OPTIONS = List.of("我来记。", "记得住又怎样？");
	/** 两个回答 */
	public static final List<List<DialogueLine>> CODEX_BRANCH_B;
	/** 交付段（此时消耗一本普通书并给见闻录） */
	public static final List<DialogueLine> CODEX_GIVE;
	/** 已经拿过见闻录时的短句 */
	public static final List<DialogueLine> CODEX_ALREADY;

	/** 取实体的打招呼台词（无则空列表） */
	public static List<DialogueLine> greetingsFor(Entity entity) {
		if (entity == null) return List.of();
		ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return GREETINGS.getOrDefault(typeKey, List.of());
	}

	/** 取实体收到某物品的送礼台词（无则空列表） */
	public static List<DialogueLine> giftLinesFor(Entity entity, Item gift) {
		if (entity == null || gift == null) return List.of();
		ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		Map<Item, List<DialogueLine>> gifts = GIFT_LINES.getOrDefault(typeKey, Map.of());
		return gifts.getOrDefault(gift, List.of());
	}
}
