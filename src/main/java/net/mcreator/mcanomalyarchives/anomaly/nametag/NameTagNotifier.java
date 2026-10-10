package net.mcreator.mcanomalyarchives.anomaly.nametag;

/**
 * 命名牌用到的全部文案键。
 *
 * 【界面不解释】作者要求玩法靠玩家自己发现，提示语一律不要：
 * unknown / applied / locked / erased / planned / unstable / exploded / not_for_player /
 * no_output / draining / dry 十一条最早删掉；2026-10-11 又把最后两条
 * （need_name"牌上没写字"、not_for_anomaly"对异常实体无效"）一并删掉。
 * 所有失败路径都只取消交互 + 进冷却，不再弹字（见 {@link NameTagHandler#denyQuietly}）。
 * 词条的增删要四处同步（工作区 language_map、守卫基线、manifest、lang json），
 * 用 {@code tools/lang/add_keys.py} / {@code remove_keys.py}，别手改单个文件。
 *
 * 这里只剩下 {@link #EGG}：它是产物名字、不属于提示。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameTagNotifier {

	/** 命名蛋的产物名字（例：牛蛋）。 */
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
}
