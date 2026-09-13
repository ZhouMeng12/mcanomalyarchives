package net.mcreator.mcanomalyarchives.anomaly.nametag;

/**
 * 代价常量。
 *
 * 【设定依据】正片 BV1YiGt6vEbK："所有属性转化均存在明显的代价与损耗"（【旁白 3:07-3:11】）。
 * - 物品：木铲被命名成"下界合金镐"后"仅仅经过五次使用后，木铲便损毁"（【旁白 2:34-2:37】）。
 *   我们把它形式化成"**耐久上限 = 名字字符数**"——更好玩，也更好解释。
 * - 实体：改名后的生物按新身份"行动"若干次后死亡（牛：多次产蛋后在痛苦挣扎中猝死，【旁白 1:56-2:00】）。
 * - 方块：流体类改写有存活时间（原石→水，"11 分钟后便彻底消散殆尽"，【旁白 2:58-3:03】）。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameTagCosts {

	/**
	 * 物品耐久上限 = 名字字符数（"下界合金镐"5 字 → 5 次）。
	 *
	 * ⚠️ 已废弃：作者 2026-09-13 改为"工具/护甲用源物品原本的耐久，源物品没有耐久则一律 100 点"。
	 * 见 {@link #DURABILITY_FALLBACK}。这里保留常量只为记录历史，不要再使用。
	 */
	@Deprecated
	public static final boolean DURABILITY_FROM_NAME_LENGTH = false;

	/** 源物品没有耐久时，被命名出来的工具/护甲给多少耐久。 */
	public static final int DURABILITY_FALLBACK = 100;

	public static final int DURABILITY_MIN = 1;
	public static final int DURABILITY_MAX = 64;

	/** 实体寿命 = 名字字符数，即按新身份"行动"多少次后死亡。 */
	public static final int ENTITY_LIFESPAN_PER_CHAR = 1;
	public static final int ENTITY_LIFESPAN_MIN = 1;
	public static final int ENTITY_LIFESPAN_MAX = 64;

	/** 跨类命名结算失败时的爆炸威力上限（实际威力由质料差额决定）。 */
	public static final float EXPLOSION_MAX_POWER = 6.0f;
	/** 跨类爆炸是否破坏地形（与陨石共用同一个舒适度开关）。 */
	public static final boolean EXPLOSION_BREAKS_TERRAIN = true;

	/**
	 * 掠夺范围：**水平 ±32 格 = 64×64 的面积**（作者要求"周围 64*64 范围内全部"）。
	 * 逐列推进，不会一次扫完。
	 */
	public static final int DRAIN_HALF_EXTENT = 32;
	/** 掠夺扫描的垂直范围（±24 格）。 */
	public static final int DRAIN_VERTICAL = 24;
	/** 每次掠夺结算最多处理多少列（一列 = 一条 49 格高的竖直扫描），控制单 tick 开销。 */
	public static final int DRAIN_COLUMNS_PER_PASS = 96;
	/** 每多少次掠夺结算一次。 */
	public static final int DRAIN_INTERVAL_TICKS = 20;

	/** 同一次命名行为的冷却，防连点。 */
	public static final int USE_COOLDOWN_TICKS = 20;

	/**
	 * 被命名成别的生物的个体，**活多久之后力竭而死**。
	 *
	 * 作者 2026-09-13："变成实体的生物将在一段时间后死亡。"
	 * 正片也是这么演的：牛在被命名成"鸡"、产了几次蛋之后"在痛苦的挣扎中猝死"（【旁白 1:56-2:00】），
	 * 猪被命名成"钻石镐"之后也是"啃咬不久后很快就力竭死亡"（【旁白 4:52-4:54】）。
	 *
	 * 60 秒。想长一点/短一点就改这一个数。
	 */
	public static final int NAMED_ENTITY_LIFESPAN_TICKS = 1200;

	private NameTagCosts() {
	}

	public static int durabilityFor(String name) {
		return clamp(name == null ? 1 : name.codePointCount(0, name.length()), DURABILITY_MIN, DURABILITY_MAX);
	}

	public static int lifespanFor(String name) {
		int raw = (name == null ? 1 : name.codePointCount(0, name.length())) * ENTITY_LIFESPAN_PER_CHAR;
		return clamp(raw, ENTITY_LIFESPAN_MIN, ENTITY_LIFESPAN_MAX);
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
