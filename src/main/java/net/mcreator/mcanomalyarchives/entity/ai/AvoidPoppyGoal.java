package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;

import java.util.EnumSet;

public class AvoidPoppyGoal extends Goal {
	private final Mob mob;
	private BlockPos targetPoppy;
	private int searchCooldown;
	private Vec3 fleeTarget;
	private static final int SEARCH_RANGE = 6;
	private static final int LOSE_RANGE = 14;
	private static final int FLEE_DISTANCE = 10;

	public AvoidPoppyGoal(Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		// 没有上瘾效果就不逃离
		if (!mob.hasEffect(McanomalyarchivesModMobEffects.ADDICTE)) return false;
		// 战斗中不逃离（战斗优先）
		if (mob.getTarget() != null) return false;

		if (searchCooldown > 0) {
			searchCooldown--;
			return targetPoppy != null && isPoppyValid(targetPoppy);
		}
		searchCooldown = 20;

		targetPoppy = findNearestPoppy();
		return targetPoppy != null;
	}

	@Override
	public boolean canContinueToUse() {
		return mob.hasEffect(McanomalyarchivesModMobEffects.ADDICTE)
			&& mob.getTarget() == null
			&& targetPoppy != null
			&& isPoppyValid(targetPoppy);
	}

	@Override
	public void start() {
		calculateFleeTarget();
	}

	@Override
	public void tick() {
		if (targetPoppy == null) return;

		// 接近目标点或导航结束后刷新逃离方向
		if (fleeTarget == null || mob.getNavigation().isDone()
				|| mob.distanceToSqr(fleeTarget) < 2.0) {
			calculateFleeTarget();
		}

		if (fleeTarget != null) {
			mob.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 1.0);
		}
	}

	@Override
	public void stop() {
		targetPoppy = null;
		fleeTarget = null;
		mob.getNavigation().stop();
	}

	private void calculateFleeTarget() {
		if (targetPoppy == null) return;
		Vec3 entityPos = mob.position();
		Vec3 poppyCenter = Vec3.atCenterOf(targetPoppy);
		Vec3 fleeDir = entityPos.subtract(poppyCenter).normalize();
		fleeTarget = entityPos.add(fleeDir.scale(FLEE_DISTANCE));
	}

	private boolean isPoppyValid(BlockPos pos) {
		Block block = mob.level().getBlockState(pos).getBlock();
		return block == McanomalyarchivesModBlocks.CORN_POPPY.get()
			|| block == McanomalyarchivesModBlocks.SAD_POPPY.get();
	}

	private BlockPos findNearestPoppy() {
		BlockPos mobPos = mob.blockPosition();
		BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
		BlockPos closest = null;
		double closestDist = Double.MAX_VALUE;

		for (int x = -SEARCH_RANGE; x <= SEARCH_RANGE; x++) {
			for (int y = -SEARCH_RANGE; y <= SEARCH_RANGE; y++) {
				for (int z = -SEARCH_RANGE; z <= SEARCH_RANGE; z++) {
					mutablePos.set(mobPos.getX() + x, mobPos.getY() + y, mobPos.getZ() + z);
					Block block = mob.level().getBlockState(mutablePos).getBlock();
					if (block == McanomalyarchivesModBlocks.CORN_POPPY.get()
						|| block == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
						double dist = mob.distanceToSqr(Vec3.atCenterOf(mutablePos));
						if (dist < closestDist) {
							closestDist = dist;
							closest = mutablePos.immutable();
						}
					}
				}
			}
		}
		return closest;
	}
}
