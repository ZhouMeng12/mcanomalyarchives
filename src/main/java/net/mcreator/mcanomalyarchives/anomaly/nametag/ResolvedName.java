package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * 一次名字解析的结果：这个名字指向"哪个注册表里的哪一个"。
 *
 * 【设定依据】百变命名牌倒置了命名的因果——正常是按事物特性起名，
 * 它是按名字赋予特性（正片 BV1YiGt6vEbK，弹幕 614s 观众总结）。
 * 所以整条玩法的第一步永远是"把玩家输入的文本解析成一个注册表对象"。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public record ResolvedName(Kind kind, ResourceLocation id) {

	/** 三类可被命名的目标。顺序即"同名时的优先顺序"。 */
	public enum Kind {
		ITEM,
		BLOCK,
		ENTITY
	}

	/** 索引文件里的一行形如 {@code item:minecraft:netherite_pickaxe}。 */
	public static ResolvedName parse(String token) {
		if (token == null) {
			return null;
		}
		int sep = token.indexOf(':');
		if (sep <= 0) {
			return null;
		}
		Kind kind = switch (token.substring(0, sep)) {
			case "item" -> Kind.ITEM;
			case "block" -> Kind.BLOCK;
			case "entity" -> Kind.ENTITY;
			default -> null;
		};
		if (kind == null) {
			return null;
		}
		ResourceLocation id = ResourceLocation.tryParse(token.substring(sep + 1));
		if (id == null || !exists(kind, id)) {
			return null;
		}
		return new ResolvedName(kind, id);
	}

	/** 索引是静态生成的，注册表里不存在的一律丢弃（避免加载后报错）。 */
	public static boolean exists(Kind kind, ResourceLocation id) {
		return switch (kind) {
			case ITEM -> BuiltInRegistries.ITEM.containsKey(id);
			case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id);
			case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
		};
	}

	/** 存进 NBT 的形式。 */
	public String token() {
		return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + id;
	}

	/**
	 * 解析出来的对象在游戏里显示成什么名字。
	 * 用于提示语（"已命名：下界合金镐"），不参与判定。
	 */
	public String defaultDisplay() {
		return switch (kind) {
			case ITEM -> new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
			case BLOCK -> BuiltInRegistries.BLOCK.get(id).getName().getString();
			case ENTITY -> BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString();
		};
	}
}
