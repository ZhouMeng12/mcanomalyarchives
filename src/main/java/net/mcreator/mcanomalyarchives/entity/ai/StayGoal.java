package net.mcreator.mcanomalyarchives.entity.ai;

import net.mcreator.mcanomalyarchives.entity.ControllableMonster;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class StayGoal extends Goal {
	private final ControllableMonster mob;

	public StayGoal(ControllableMonster mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return mob.getMobState() == ControllableMonster.STATE_STAY;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		mob.getNavigation().stop();
		mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
	}
}
