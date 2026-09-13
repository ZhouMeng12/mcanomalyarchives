package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 提示语。全部走翻译键，中英各一份（见 lang/zh_cn.json 与 en_us.json）。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameTagNotifier {

	public static final String NEED_NAME = "nametag.mcanomalyarchives.need_name";
	public static final String UNKNOWN = "nametag.mcanomalyarchives.unknown";
	public static final String APPLIED = "nametag.mcanomalyarchives.applied";
	public static final String LOCKED = "nametag.mcanomalyarchives.locked";
	public static final String ERASED = "nametag.mcanomalyarchives.erased";
	public static final String PLANNED = "nametag.mcanomalyarchives.planned";
	public static final String UNSTABLE = "nametag.mcanomalyarchives.unstable";
	public static final String EXPLODED = "nametag.mcanomalyarchives.exploded";
	public static final String NOT_FOR_PLAYER = "nametag.mcanomalyarchives.not_for_player";
	public static final String NO_OUTPUT = "nametag.mcanomalyarchives.no_output";
	public static final String DRAINING = "nametag.mcanomalyarchives.draining";
	public static final String DRY = "nametag.mcanomalyarchives.dry";
	public static final String EGG = "nametag.mcanomalyarchives.egg";

	/**
	 * 蛋物品里记"这是谁下的"用的键（写进物品的 CUSTOM_DATA）。
	 *
	 * 正片【1:42-1:46】：牛被命名成"鸡"后下的牛蛋"可以孵出正常的牛幼仔"。
	 * 所以蛋必须记得下蛋那只生物的物种，砸出来才是它的幼体而不是小鸡。
	 */
	public static final String EGG_SPECIES_KEY = "McanomalyEggOf";

	private NameTagNotifier() {
	}

	public static void actionBar(Player player, String key, Object... args) {
		player.displayClientMessage(Component.translatable(key, args), true);
	}

	public static void chat(Player player, String key, Object... args) {
		player.displayClientMessage(Component.translatable(key, args), false);
	}
}
