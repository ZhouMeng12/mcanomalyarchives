package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;

/**
 * 忠诚安布拉：主人被攻击时，攻击袭击者（类似狗）。
 * 有主人且主人在线时生效；触发时自动进入狂暴帮战。
 */
public class AnbulaOwnerHurtByTargetGoal extends Goal {
	private final AnbulaEntity anbula;
	private LivingEntity owner;

	public AnbulaOwnerHurtByTargetGoal(AnbulaEntity anbula) {
		this.anbula = anbula;
	}

	@Override
	public boolean canUse() {
		if (anbula.isFaint() || !anbula.hasOwner()) return false;
		owner = anbula.level().getPlayerByUUID(anbula.getOwnerUUID());
		if (owner == null || !owner.isAlive()) return false;
		// 主人 100 tick 内被攻击过
		LivingEntity attacker = owner.getLastHurtByMob();
		if (attacker == null || !attacker.isAlive() || attacker == anbula) return false;
		return owner.tickCount - owner.getLastHurtByMobTimestamp() <= 100;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = anbula.getTarget();
		return target != null && target.isAlive() && target != anbula
				&& !(target instanceof Player p && p.getAbilities().instabuild);
	}

	@Override
	public void start() {
		LivingEntity attacker = owner != null ? owner.getLastHurtByMob() : null;
		if (attacker != null && attacker.isAlive()) {
			if (!anbula.isBerserk()) {
				anbula.enterBerserk(); // 进入狂暴帮战
			}
			anbula.setTarget(attacker);
		}
	}
}
