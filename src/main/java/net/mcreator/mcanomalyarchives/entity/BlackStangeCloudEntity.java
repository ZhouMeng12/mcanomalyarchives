package net.mcreator.mcanomalyarchives.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.config.ServerGameplayConfig;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;

/**
 * 黑吸伪云（UO-001 的「黑色个体」）。
 *
 * <p>依据是见闻录里一直没实现的那条特征原文：**「黑色个体接触方块会令其离奇消失」**。
 * 作者 2026-09-18 定的规则：模型用小伪云那一套、贴图乌黑（不是纯黑）、
 * **碰到方块的一瞬间吞掉周围一大片随机方块（无掉落），然后自己当场消散**；
 * 只吞方块，不伤害生物、不染水。
 *
 * <p>它直接继承 {@link SmallStangeCloudEntity}：出场沿门口法线、直线匀速、永不换向、
 * 高度恒定、60 秒寿命、一生只过一次门、全免疫这些行为原样继承，
 * 只把 {@link #tickCloudEffects()}（父类默认是"伤害盒 + 染水"）整个换成"吞一口 + 消散"。
 * 顺带一个好处：大小伪云的伤害排除名单写的是 {@code instanceof SmallStangeCloudEntity}，
 * 黑吸伪云作为子类自动被排除，不用再去改那两处。
 */
public class BlackStangeCloudEntity extends SmallStangeCloudEntity {

	// ==================== 吞方块（作者：范围要大点） ====================

	/** 吞噬半径（格）：以云体视觉中心为球心的一次性球形范围 */
	public static final double ERASE_RADIUS = 6.0D;
	/** 一次至少吞多少块 */
	public static final int ERASE_MIN = 12;
	/** 一次最多吞多少块（写死上界："范围大"不等于无上限，免得变成拆家灾难） */
	public static final int ERASE_MAX = 24;

	public BlackStangeCloudEntity(EntityType<? extends BlackStangeCloudEntity> type, Level world) {
		super(type, world);
	}

	/**
	 * 不走出场阶段：作者 2026-09-18「黑吸云移动也要像伪云一样水平移动」——
	 * 一出生就进入与伪云同款的水平匀速漂移（朝向仍由传送门给的门口法线决定，所以还是从门里出来）。
	 */
	@Override
	protected boolean skipLaunchPhase() {
		return true;
	}

	/**
	 * 覆盖父类的"伤害盒 + 染水"：黑吸伪云只做一件事 ——
	 * 身体碰到实体方块就吞一口，然后自己消散。
	 */
	@Override
	protected void tickCloudEffects() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return; // 只在服务端改世界
		}
		// ⓪ 刚被门吐出来的那 40 tick 先不干活：此时身体还压在门框那一片，
		//    立刻开咬既会看起来像"门什么都没吐出来"，也会把自己的门咬坏
		if (isLaunching()) {
			return;
		}
		// ① 接触判定：身体范围内有没有"实体方块"（水、空气、草不算）
		if (!touchingSolidBlock(serverLevel)) {
			return;
		}
		// ② 两道闸门：任一关闭就只消散、不吞方块（与末影人一样尊重 mobGriefing）
		if (serverLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
				&& ServerGameplayConfig.blackCloudTerrainDamage()) {
			eraseAround(serverLevel);
		}
		// ③ 吞完自己消散
		vanish(serverLevel);
	}

	/**
	 * 身体（视觉那一段：模型悬在实体原点下方约 0.667 格）真的碰到实体方块了吗。
	 *
	 * <p>用**真正的方块碰撞箱求交**，不是"整数方块格"式的粗判：
	 * 上一版把 AABB 稍微往下多算了一点，`BlockPos.betweenClosed` 取整就把云**下面那一层地面**
	 * 也算了进来 —— 而云出生时本来就悬在地面上方约 0.13 格，于是"刚出现就触发黑吸消失"
	 * （作者 2026-09-18 报的正是这个）。
	 */
	private boolean touchingSolidBlock(ServerLevel level) {
		AABB body = bodyBox();
		for (BlockPos pos : BlockPos.betweenClosed(
				BlockPos.containing(body.minX, body.minY, body.minZ),
				BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			if (shape.bounds().move(pos.getX(), pos.getY(), pos.getZ()).intersects(body)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 云体实际占的那块空间：用父类（小伪云）的那一份 —— 长边沿运动方向、短边横着、垂直对齐视觉。
	 *
	 * <p>父类那份**垂直方向不往下给余量**：视觉底边就是它的"身体底"，
	 * 多给一点就会擦到地面（上一版"刚出现就触发黑吸消失"就是这么来的）。
	 */

	/**
	 * 吞掉周围随机方块：在 {@link #ERASE_RADIUS} 格球形范围内收集"实体、非免疫"的方块，
	 * 随机挑 {@link #ERASE_MIN}~{@link #ERASE_MAX} 块，逐块 {@code destroyBlock(pos, false)}。
	 *
	 * <p>{@code drops=false} 是关键：作者要的是"离奇消失"，不是被挖掉 —— 不掉任何东西；
	 * 破坏粒子与音效由原版 {@code destroyBlock} 自带，不用自己补。
	 */
	private void eraseAround(ServerLevel level) {
		Vec3 center = new Vec3(this.getX(), this.getY() - VISUAL_HEIGHT / 2.0D, this.getZ());
		int r = (int) Math.ceil(ERASE_RADIUS);
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
				BlockPos.containing(center.x - r, center.y - r, center.z - r),
				BlockPos.containing(center.x + r, center.y + r, center.z + r))) {
			if (center.distanceToSqr(Vec3.atCenterOf(pos)) > ERASE_RADIUS * ERASE_RADIUS) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
				continue; // 空气、水、草之类不算"方块"
			}
			if (isProtected(level, pos, state)) {
				continue;
			}
			candidates.add(pos.immutable());
		}
		if (candidates.isEmpty()) {
			return;
		}
		Collections.shuffle(candidates, new java.util.Random(level.random.nextLong()));
		int count = Math.min(candidates.size(), ERASE_MIN + level.random.nextInt(ERASE_MAX - ERASE_MIN + 1));
		for (int i = 0; i < count; i++) {
			level.destroyBlock(candidates.get(i), false); // 不掉落
		}
	}

	/**
	 * 免疫名单：这些东西不能被黑吸伪云吃掉。
	 *
	 * 基岩/屏障/命令方块/末地门是"世界骨架"；云室传送门是它自己出来的门（吃了门等于把路断了）；
	 * 怪树的原木与树叶按设定本来就免疫破坏，这里保持一致。
	 */
	private boolean isProtected(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.COMMAND_BLOCK)
				|| state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)
				|| state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)
				|| state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_GATEWAY)
				|| state.is(Blocks.NETHER_PORTAL)) {
			return true;
		}
		return state.is(McanomalyarchivesModBlocks.CLOUD_DEM_PORTAL.get())
				// 云室门的门框就是云石（元素里 portalFrame=CUSTOM:CloudStone）：
				// 不保护它的话，黑云刚出门那一口就能把门框啃掉 → 门shape不完整 → 门没了，
				// 于是"门里再也不生成云了"。顺便也保护云石建筑。
				|| state.is(McanomalyarchivesModBlocks.CLOUD_STONE.get())
				|| state.is(McanomalyarchivesModBlocks.STRANGETREELOG.get())
				|| state.is(McanomalyarchivesModBlocks.STRANGETREELEAVES.get());
	}

	/** 吞完（或闸门关闭时直接）散掉：一圈黑烟 + 一声低沉音效。 */
	private void vanish(ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE,
				this.getX(), this.getY() - VISUAL_HEIGHT / 2.0D, this.getZ(),
				24, VISUAL_LENGTH / 2.0D, VISUAL_HEIGHT, VISUAL_SHORT_EDGE / 2.0D, 0.02D);
		level.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
				net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.6F);
		this.discard();
	}
}
