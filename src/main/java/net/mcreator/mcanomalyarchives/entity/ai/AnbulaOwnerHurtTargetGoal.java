package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;

/**
 * 忠诚安布拉：主人正在攻击的目标，转攻同一目标（类似狗）。
 * 有主人且主人在线时生效；触发时自动进入狂暴帮战。
 */
public class AnbulaOwnerHurtTargetGoal extends Goal {
	private final AnbulaEntity anbula;
	private LivingEntity owner;

	public AnbulaOwnerHurtTargetGoal(AnbulaEntity anbula) {
		this.anbula = anbula;
	}

	@Override
	public boolean canUse() {
		if (anbula.isFaint() || !anbula.hasOwner()) return false;
		owner = anbula.level().getPlayerByUUID(anbula.getOwnerUUID());
		if (owner == null || !owner.isAlive()) return false;
		LivingEntity target = owner.getLastHurtMob();
		// 与 canContinueToUse() 保持一致：排除创造模式玩家。
		// 否则 canUse() 恒真 + canContinueToUse() 排除创造 → 每 tick 重复 start() → 反复 enterBerserk()
		return target != null && target.isAlive() && target != anbula
				&& !(target instanceof Player p && p.getAbilities().instabuild);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = anbula.getTarget();
		return target != null && target.isAlive() && target != anbula
				&& !(target instanceof Player p && p.getAbilities().instabuild);
	}

	@Override
	public void start() {
		LivingEntity target = owner != null ? owner.getLastHurtMob() : null;
		if (target != null && target.isAlive() && target != anbula) {
			if (!anbula.isBerserk()) {
				anbula.enterBerserk(); // 进入狂暴帮战
			}
			anbula.setTarget(target);
		}
	}
}
