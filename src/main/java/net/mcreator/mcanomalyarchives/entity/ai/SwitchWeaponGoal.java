package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;
import net.mcreator.mcanomalyarchives.compat.TaczCompat;

import java.util.EnumSet;

/**
 * 智能切换武器（无冷却）：
 * - TACZ 枪械：仅在弹药耗尽时切近战
 * - 弓/弩：原逻辑（目标近时切近战、远时切远程）
 */
public class SwitchWeaponGoal extends Goal {
	private final AnbulaEntity anbula;
	private static final double RANGED_THRESHOLD_SQ = 25.0; // 25 格平方
	private static final double MELEE_SWITCH_SQ = 9.0;      // 3 格平方贴脸

	public SwitchWeaponGoal(AnbulaEntity anbula) {
		this.anbula = anbula;
		this.setFlags(EnumSet.noneOf(Flag.class));
	}

	@Override
	public boolean canUse() {
		if (!anbula.isBerserk() || anbula.isFaint()) return false;
		if (anbula.getTarget() == null || !anbula.getTarget().isAlive()) return false;

		double dist = anbula.distanceToSqr(anbula.getTarget());
		boolean isMelee = AnbulaEntity.isMeleeWeapon(anbula.getMainHandItem());
		boolean isHoldingTacz = TaczCompat.isGun(anbula.getMainHandItem());

		// TACZ 枪械：弹药耗尽 + 贴脸 → 切近战
		if (isHoldingTacz) {
			if (TaczCompat.getAmmoCount(anbula.getMainHandItem()) <= 0 && dist < MELEE_SWITCH_SQ && anbula.hasMeleeWeapon())
				return true;
			return false; // 有弹药时绝不切近战
		}

		// 弓/弩：目标很近 → 切近战（必须真有近战武器，否则条件恒真导致每 tick 空转）
		if (anbula.isHoldingRanged() && dist <= RANGED_THRESHOLD_SQ && anbula.hasMeleeWeapon()) return true;
		// 近战但目标太远且有远程武器 → 切远程
		if (isMelee && dist > RANGED_THRESHOLD_SQ && anbula.hasRangedWeapon()) return true;
		// 没拿武器但有近战武器 → 切近战
		if (!isMelee && !anbula.isHoldingRanged() && anbula.hasMeleeWeapon()) return true;

		return false;
	}

	@Override
	public boolean canContinueToUse() {
		// 委托 canUse()：start() 只在条件首次成立时执行一次，切武器后条件自然失效而停止。
		// 原实现恒返回 false，会每 tick start/stop 空转，反复触发 setActiveWeaponSlot 同步
		return canUse();
	}

	@Override
	public void start() {
		double dist = anbula.distanceToSqr(anbula.getTarget());
		boolean isHoldingTacz = TaczCompat.isGun(anbula.getMainHandItem());

		// TACZ 枪械弹尽 → 切近战
		if (isHoldingTacz && dist < MELEE_SWITCH_SQ) {
			int meleeSlot = anbula.findBestMeleeSlot();
			if (meleeSlot >= 0) {
				anbula.setActiveWeaponSlot(meleeSlot);
			}
			return;
		}

		// 弓/弩原逻辑
		if (dist > RANGED_THRESHOLD_SQ) {
			if (!anbula.isHoldingRanged()) {
				int rangedSlot = anbula.findRangedSlot();
				if (rangedSlot >= 0) {
					anbula.setActiveWeaponSlot(rangedSlot);
				}
			}
		} else {
			if (anbula.isHoldingRanged() || !AnbulaEntity.isMeleeWeapon(anbula.getMainHandItem())) {
				int meleeSlot = anbula.findBestMeleeSlot();
				if (meleeSlot >= 0) {
					anbula.setActiveWeaponSlot(meleeSlot);
				}
			}
		}
	}
}
