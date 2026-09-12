package net.mcreator.mcanomalyarchives.entity.ai;

import net.mcreator.mcanomalyarchives.entity.ControllableMonster;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;

import java.util.EnumSet;

public class ControlledWanderGoal extends RandomStrollGoal {
	private final ControllableMonster mob;

	public ControlledWanderGoal(ControllableMonster mob, double speedModifier) {
		super(mob, speedModifier, 10);
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (mob.getMobState() != ControllableMonster.STATE_WANDER) {
			return false;
		}
		return super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		if (mob.getMobState() != ControllableMonster.STATE_WANDER) {
			return false;
		}
		return super.canContinueToUse();
	}
}
