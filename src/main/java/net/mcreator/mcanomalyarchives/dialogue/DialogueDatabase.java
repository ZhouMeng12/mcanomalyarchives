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
	}

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
