package net.mcreator.mcanomalyarchives.entity.ai;

import net.mcreator.mcanomalyarchives.entity.ControllableMonster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

public class FollowPlayerGoal extends Goal {
	private final ControllableMonster mob;
	private Player targetPlayer;
	private static final double MIN_DISTANCE = 3.0;
	private static final double MAX_DISTANCE = 5.0;
	private static final double TELEPORT_DISTANCE = 24.0;

	public FollowPlayerGoal(ControllableMonster mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (mob.getMobState() != ControllableMonster.STATE_FOLLOW) {
			return false;
		}
		UUID targetUUID = mob.getFollowTargetUUID();
		if (targetUUID == null) {
			return false;
		}
		targetPlayer = mob.level().getPlayerByUUID(targetUUID);
		if (targetPlayer == null || !targetPlayer.isAlive() || targetPlayer.isSpectator()) {
			demoteToWander();
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (mob.getMobState() != ControllableMonster.STATE_FOLLOW) {
			return false;
		}
		if (targetPlayer == null || !targetPlayer.isAlive() || targetPlayer.isSpectator()) {
			demoteToWander();
			return false;
		}
		return true;
	}

	@Override
	public void tick() {
		if (targetPlayer == null) return;
		double dist = mob.distanceToSqr(targetPlayer);
		if (dist > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
			// 离远了瞬移到玩家身边；找不到安全落点则继续步行追赶
			if (!teleportNearPlayer()) {
				mob.getNavigation().moveTo(targetPlayer, 1.2);
			}
		} else if (dist > MAX_DISTANCE * MAX_DISTANCE) {
			mob.getNavigation().moveTo(targetPlayer, 1.0);
		} else if (dist < MIN_DISTANCE * MIN_DISTANCE) {
			mob.getNavigation().stop();
		}
		mob.getLookControl().setLookAt(targetPlayer, 10.0f, (float) mob.getMaxHeadXRot());
	}

	/**
	 * 瞬移到玩家附近 2~3.5 格的可行走安全落点；找不到返回 false。
	 * 优先贴近玩家当前高度（地下/洞穴也能跟住），避免瞬移到地表导致"消失"。
	 */
	private boolean teleportNearPlayer() {
		int py = (int) Math.floor(targetPlayer.getY());
		for (int i = 0; i < 10; i++) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2;
			double radius = 2.0 + mob.getRandom().nextDouble() * 1.5;
			int x = (int) Math.floor(targetPlayer.getX() + Math.cos(angle) * radius);
			int z = (int) Math.floor(targetPlayer.getZ() + Math.sin(angle) * radius);
			// 从玩家高度向下最多 3 格找落点
			for (int dy = 0; dy >= -3; dy--) {
				int y = py + dy;
				if (y <= mob.level().getMinBuildHeight()) continue;
				if (isSafeLanding(x, y, z)) {
					mob.teleportTo(x + 0.5, y, z + 0.5);
					mob.getNavigation().stop();
					if (mob.level() instanceof ServerLevel serverLevel) {
						serverLevel.sendParticles(ParticleTypes.POOF,
								mob.getX(), mob.getY() + 0.5, mob.getZ(),
								8, 0.3, 0.3, 0.3, 0.02);
					}
					return true;
				}
			}
		}
		return false;
	}

	/** 落点需 2 格高空间且脚下是固体（排除空气/水面/岩浆） */
	private boolean isSafeLanding(int x, int y, int z) {
		BlockPos feet = new BlockPos(x, y, z);
		return mob.level().getBlockState(feet.below()).isSolid()
				&& !mob.level().getBlockState(feet).isSolid()
				&& !mob.level().getBlockState(feet.above()).isSolid();
	}

	private void demoteToWander() {
		mob.setMobState(ControllableMonster.STATE_WANDER);
		targetPlayer = null;
	}

	@Override
	public void stop() {
		targetPlayer = null;
		mob.getNavigation().stop();
	}
}
