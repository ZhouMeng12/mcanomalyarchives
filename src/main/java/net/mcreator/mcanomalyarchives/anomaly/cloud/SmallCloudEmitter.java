package net.mcreator.mcanomalyarchives.anomaly.cloud;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;

/**
 * 「云室传送门往外吐小伪云」这件事的全部逻辑。
 *
 * <p>作者 2026-09-16 定的触发方式是「**只要门存在就吐**」，所以这里不去扫描方块、也不维护门户位置表，
 * 而是挂在**门自己的随机刻**上（{@link SmallCloudEmittingPortalBlock#randomTick}）：
 * <ul>
 *   <li>随机刻只在有玩家的已加载区块里跑（原版规则），所以远方的门不会白刷 —— 这是免费拿到的"有人看才演"；</li>
 *   <li>原版的随机刻是**每个 16³ 区块段每 tick 抽 3 个位置**
 *       （{@code ServerLevel.tickChunk}：段里有随机刻方块就抽 {@code randomTickSpeed=3} 次），
 *       所以单个门方块每 tick 被选中的概率是 3/4096；一扇 2×3 的门有 6 格 → ≈1/228 每 tick。
 *       再除以 {@link #EMIT_ONE_IN}，就是**平均每扇门约 68 秒吐一只**。</li>
 * </ul>
 *
 * <p>顺带说清楚为什么不用另外两种做法：扫描附近方块找门要对玩家周围做上万次 {@code getBlockState}；
 * 维护门户位置表则会让**老存档里已有的门在被人用之前一只都不吐**。
 */
public final class SmallCloudEmitter {

	/**
	 * 每次随机刻只有 1/EMIT_ONE_IN 的概率真的吐。
	 *
	 * <p>换算（一个 2×3 的门）：6 格 × 3/4096 = 1/228 每 tick，除以 6 → 平均 1368 tick ≈ **68 秒一只**。
	 * 想要更频繁就调小这个数，想更安静就调大。
	 */
	public static final int EMIT_ONE_IN = 6;
	/** 同一扇门附近最多同时存在几只小伪云（防止刷屏）。 */
	public static final int MAX_PER_PORTAL = 2;
	/** 统计"这扇门附近"的半径。 */
	public static final double PORTAL_NEIGHBOURHOOD = 8.0D;
	/** 出生点相对门方块的抬升：模型视觉悬在实体原点下方 0.667 格，抬 0.8 格云体就落在门内空里。 */
	public static final double SPAWN_Y_OFFSET = 0.8D;

	private SmallCloudEmitter() {
	}

	/**
	 * 由门方块在随机刻里调用：掷骰、挑出口侧、判并发上限，然后丢一只小伪云出来。
	 *
	 * @return 真的生成了一只就返回 true（自检脚本会用到）
	 */
	public static boolean tryEmit(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		if (random.nextInt(EMIT_ONE_IN) != 0) {
			return false;
		}

		// 门是竖着的一块平面：AXIS=X 表示门面横跨 X 轴、法线沿 Z；反之亦然
		Direction.Axis axis = state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
				? state.getValue(BlockStateProperties.HORIZONTAL_AXIS)
				: Direction.Axis.X;
		Direction normal = axis == Direction.Axis.X ? Direction.NORTH : Direction.WEST;

		Direction exit = pickExitSide(level, pos, normal);
		if (exit == null) {
			return false; // 两侧都被堵住：这次算了
		}

		AABB neighbourhood = new AABB(pos).inflate(PORTAL_NEIGHBOURHOOD);
		List<SmallStangeCloudEntity> existing = level.getEntitiesOfClass(SmallStangeCloudEntity.class, neighbourhood);
		if (existing.size() >= MAX_PER_PORTAL) {
			return false;
		}

		EntityType<SmallStangeCloudEntity> type = McanomalyarchivesModEntities.SMALL_STANGE_CLOUD.get();
		SmallStangeCloudEntity cloud = type.create(level);
		if (cloud == null) {
			return false;
		}

		BlockPos exitPos = pos.relative(exit);
		cloud.moveTo(exitPos.getX() + 0.5D, pos.getY() + SPAWN_Y_OFFSET, exitPos.getZ() + 0.5D, 0.0F, 0.0F);
		// 出场方向 = 门面法线。**只通过朝向传递**：实体朝向会随生成包同步到客户端，
		// 于是客户端也能算出同一个方向，不需要额外的网络包（见 SmallStangeCloudEntity.headingFromYaw）。
		cloud.setYRot(SmallStangeCloudEntity.yawFor(exit.getStepX(), exit.getStepZ()));
		cloud.setYHeadRot(cloud.getYRot());
		level.addFreshEntity(cloud);

		// 门口冒一下云气，像被咳出来
		level.sendParticles(ParticleTypes.CLOUD,
				exitPos.getX() + 0.5D, pos.getY() + 0.6D, exitPos.getZ() + 0.5D,
				8, 0.4D, 0.5D, 0.4D, 0.01D);
		return true;
	}

	/** 出口侧：优先取空气那一侧；两侧都通就随机挑一侧；都不通返回 null。 */
	private static Direction pickExitSide(ServerLevel level, BlockPos pos, Direction normal) {
		boolean firstPassable = isPassable(level, pos.relative(normal));
		boolean secondPassable = isPassable(level, pos.relative(normal.getOpposite()));
		if (firstPassable && secondPassable) {
			return level.random.nextBoolean() ? normal : normal.getOpposite();
		}
		if (firstPassable) {
			return normal;
		}
		if (secondPassable) {
			return normal.getOpposite();
		}
		return null;
	}

	private static boolean isPassable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}
}
