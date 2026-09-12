package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.block.CornPoppyBlock;

import java.util.EnumSet;

public class WatchCornPoppyGoal extends Goal {
	private final Mob mob;
	private BlockPos targetPoppy;
	private int searchCooldown;
	private static final int SEARCH_RANGE = 6;
	private static final int LOSE_RANGE = 14;

	public WatchCornPoppyGoal(Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		// 战斗中不去看花
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
		return mob.getTarget() == null && targetPoppy != null && isPoppyValid(targetPoppy);
	}

	@Override
	public void tick() {
		if (targetPoppy == null) return;
		
		// 看向虞美人
		mob.getLookControl().setLookAt(
			targetPoppy.getX() + 0.5, targetPoppy.getY() + 0.5, targetPoppy.getZ() + 0.5
		);
		
		// 记录注视时间，触发注视系统
		long currentGameTime = mob.level().getGameTime();
		CornPoppyBlock.setLastWatchedTime(targetPoppy, currentGameTime);
		CornPoppyBlock.clearAngryState(targetPoppy);
	}

	@Override
	public void stop() {
		targetPoppy = null;
	}

	private boolean isPoppyValid(BlockPos pos) {
		Block block = mob.level().getBlockState(pos).getBlock();
		return block == McanomalyarchivesModBlocks.CORN_POPPY.get() || 
			   block == McanomalyarchivesModBlocks.SAD_POPPY.get();
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
					if (block == McanomalyarchivesModBlocks.CORN_POPPY.get() || 
						block == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
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
