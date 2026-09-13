package net.mcreator.mcanomalyarchives.anomaly.nametag;

/**
 * 命名后的**转化时间轴**。
 *
 * 【作者定的机制】被命名的生物会**慢慢变成**对应的生物：
 * 行为立刻接管（正片：牛被命名成"鸡"后马上不能挤奶、马上开始下蛋），
 * 外形与本体随时间推进，走完 100% 时原地替换成目标。
 *
 * 【时长公式】{@code clamp(名字字符数 × 200 + 质料差额 × 4, 30秒, 5分钟)}
 * ——越长、越"重"的名字越慢，天然形成压力：
 * <ul>
 *   <li>牛 →「鸡」：5 字、质料盈余 → 1000 tick ≈ 50 秒</li>
 *   <li>羊 →「金块」：2 字、质料亏 1100 → 4800 tick ≈ 4 分钟</li>
 * </ul>
 *
 * 【阶段】0 / 25 / 50 / 75 / 100%，每跨过一个阶段派发一次表现（抽搐、粒子、惨叫、体型推进）。
 * 进度不进存档也能算：存"开始时刻 + 总时长"，进度由当前游戏刻减出来。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameTagTransform {

	/** 阶段数：0-25 / 25-50 / 50-75 / 75-100，外加完成。 */
	public static final int STAGES = 4;

	public static final int MIN_TICKS = 300; // 15 秒
	public static final int MAX_TICKS = 2400; // 2 分钟

	/** 每字符多少 tick。 */
	public static final int TICKS_PER_CHAR = 100;
	/** 每单位质料差额多少 tick。 */
	public static final int TICKS_PER_DEFICIT = 1;

	private NameTagTransform() {
	}

	public static int durationTicks(String name, int budget, int need) {
		int chars = name == null ? 1 : Math.max(1, name.codePointCount(0, name.length()));
		int deficit = Math.max(0, need - budget);
		return NameTagCosts.clamp(chars * TICKS_PER_CHAR + deficit * TICKS_PER_DEFICIT, MIN_TICKS, MAX_TICKS);
	}

	public static float progress(long gameTime, long startTick, int durationTicks) {
		if (durationTicks <= 0) {
			return 1.0f;
		}
		float p = (float) (gameTime - startTick) / (float) durationTicks;
		return Math.max(0.0f, Math.min(1.0f, p));
	}

	/** 已经走到第几个阶段（0 表示还没跨过第一个）。 */
	public static int stageOf(float progress) {
		if (progress >= 1.0f) {
			return STAGES;
		}
		return (int) (progress * STAGES);
	}

	// ===== 状态读写（跟身份标记一起存在 persistentData 里） =====

	public static void start(net.minecraft.world.entity.Entity entity, long gameTime, int duration) {
		net.minecraft.nbt.CompoundTag tag = entity.getPersistentData();
		tag.putLong(NamedState.K_TRANSFORM_START, gameTime);
		tag.putInt(NamedState.K_TRANSFORM_DURATION, duration);
		tag.putInt(NamedState.K_TRANSFORM_STAGE, -1);
	}

	public static boolean isTransforming(net.minecraft.world.entity.Entity entity) {
		return entity.getPersistentData().contains(NamedState.K_TRANSFORM_START);
	}

	public static long startTick(net.minecraft.world.entity.Entity entity) {
		return entity.getPersistentData().getLong(NamedState.K_TRANSFORM_START);
	}

	public static int duration(net.minecraft.world.entity.Entity entity) {
		return entity.getPersistentData().getInt(NamedState.K_TRANSFORM_DURATION);
	}

	public static int lastStage(net.minecraft.world.entity.Entity entity) {
		return entity.getPersistentData().getInt(NamedState.K_TRANSFORM_STAGE);
	}

	public static void setLastStage(net.minecraft.world.entity.Entity entity, int stage) {
		entity.getPersistentData().putInt(NamedState.K_TRANSFORM_STAGE, stage);
	}

	/** 供客户端/提示语用的当前进度。 */
	public static float progressOf(net.minecraft.world.entity.Entity entity, long gameTime) {
		if (!isTransforming(entity)) {
			return 0.0f;
		}
		return progress(gameTime, startTick(entity), duration(entity));
	}
}
