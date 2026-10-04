package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 提示语。全部走翻译键，中英各一份（见 lang/zh_cn.json 与 en_us.json）。
 *
 * 【只留这两条提示】作者要求去掉全部解释性提示（unknown / applied / locked / erased / planned /
 * unstable / exploded / not_for_player / no_output / draining / dry 十一条已删除，
 * 中英两侧与守卫基线一并用 {@code tools/lang/remove_keys.py} 清掉）：
 * 玩法靠玩家自己发现，界面不解释。剩下的两条提示是"牌上没写字""对异常实体无效"，
 * 另有一条 {@link #EGG} 是产物名字、不属于提示。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameTagNotifier {

	public static final String NEED_NAME = "nametag.mcanomalyarchives.need_name";
	public static final String NOT_FOR_ANOMALY = "nametag.mcanomalyarchives.not_for_anomaly";
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
