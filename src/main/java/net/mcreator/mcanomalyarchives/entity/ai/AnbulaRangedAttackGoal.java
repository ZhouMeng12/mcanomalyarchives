package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;
import net.mcreator.mcanomalyarchives.compat.TaczCompat;

import java.util.EnumSet;

/**
 * 安布拉远程攻击 Goal。
 * - 手持弓时：快速蓄力射箭（0.4秒）+ 风筝走位
 * - 手持弩时：发射冷却 1.5 秒，冷却期间保持走位
 * - 无限箭矢
 */
public class AnbulaRangedAttackGoal extends Goal {
	private final AnbulaEntity anbula;
	private static final int BOW_CHARGE_TIME = 8;       // 0.4秒蓄力
	private static final double KITE_MIN_RANGE = 16.0;    // 平方距离 < 4格
	private static final double KITE_MAX_RANGE = 196.0;   // 平方距离 14格（原名25格太远，1.6速箭会落地）
	private static final double MIN_ATTACK_DISTANCE = 5.0; // 目标 < 5格交给近战
	private int crossbowCooldown = 0;
	private int bowChargeTime = 0;
	private boolean bowCharging = false;
	private int seeTime;
	private int strafeDirection = 0;  // 1=右, -1=左
	private int strafeTimer = 0;

	public AnbulaRangedAttackGoal(AnbulaEntity mob, double speedModifier, int attackInterval, float range) {
		this.anbula = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		// 冷却在 canUse() 中递减：canContinueToUse() 委托 canUse()，若只在 tick() 中递减，
		// 发射后冷却>0 会让 canContinueToUse() 立即返回 false → goal 被 stop → tick() 不再执行 → 冷却永久锁死
		if (crossbowCooldown > 0) crossbowCooldown--;
		if (!anbula.isBerserk() || anbula.isFaint()) return false;
		LivingEntity target = anbula.getTarget();
		if (target == null || !target.isAlive()) return false;

		if (!AnbulaEntity.isBow(anbula.getMainHandItem()) && !AnbulaEntity.isCrossbow(anbula.getMainHandItem())) {
			return false;
		}

		// TACZ 枪械由 AnbulaTaczGoal 处理，此处互斥
		if (TaczCompat.isGun(anbula.getMainHandItem())) {
			return false;
		}

		// 目标太近，交给近战AI处理
		if (anbula.distanceToSqr(target) < MIN_ATTACK_DISTANCE * MIN_ATTACK_DISTANCE && anbula.hasMeleeWeapon()) {
			return false;
		}

		if (AnbulaEntity.isCrossbow(anbula.getMainHandItem()) && crossbowCooldown > 0) {
			return false;
		}

		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		bowCharging = false;
		bowChargeTime = 0;
		seeTime = 0;
		strafeDirection = 0;
		strafeTimer = 0;
	}

	@Override
	public void tick() {
		LivingEntity target = anbula.getTarget();
		if (target == null) return;

		boolean canSee = anbula.getSensing().hasLineOfSight(target);

		if (canSee) {
			seeTime++;
		} else {
			seeTime = 0;
		}

		anbula.getLookControl().setLookAt(target, 30.0F, 30.0F);

		if (AnbulaEntity.isBow(anbula.getMainHandItem())) {
			tickBow(target, canSee);
		} else if (AnbulaEntity.isCrossbow(anbula.getMainHandItem())) {
			tickCrossbow(target, canSee);
		}
	}

	private void tickBow(LivingEntity target, boolean canSee) {
		double dist = anbula.distanceToSqr(target);

		// 风筝移动逻辑
		if (dist < KITE_MIN_RANGE) {
			// 太近：后退
			Vec3 away = anbula.position().subtract(target.position()).normalize().scale(0.5);
			anbula.getNavigation().moveTo(
				anbula.getX() + away.x,
				anbula.getY(),
				anbula.getZ() + away.z,
				1.0);
		} else if (dist > KITE_MAX_RANGE) {
			// 太远：追击
			anbula.getNavigation().moveTo(target, 1.0);
		} else {
			// 最佳距离内：侧向移动（风筝）
			strafeTimer++;
			if (strafeTimer >= 15) {
				strafeTimer = 0;
				strafeDirection = anbula.getRandom().nextBoolean() ? 1 : -1;
			}

			Vec3 toTarget = target.position().subtract(anbula.position()).normalize();
			Vec3 side = new Vec3(-toTarget.z * strafeDirection, 0, toTarget.x * strafeDirection).scale(0.4);
			anbula.getNavigation().moveTo(
				anbula.getX() + side.x,
				anbula.getY(),
				anbula.getZ() + side.z,
				0.8);
		}

		if (!canSee) return;

		// 弓蓄力
		if (!bowCharging) {
			bowCharging = true;
			bowChargeTime = 0;
			anbula.startUsingItem(InteractionHand.MAIN_HAND);
		}

		bowChargeTime++;

		if (bowChargeTime >= BOW_CHARGE_TIME) {
			performBowShot(target);
			bowCharging = false;
			bowChargeTime = 0;
			anbula.stopUsingItem();
		}
	}

	private void performBowShot(LivingEntity target) {
		float power = BowItem.getPowerForTime(bowChargeTime);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(anbula, anbula.getMainHandItem(), power, null);
		double dx = target.getX() - anbula.getX();
		double dy = target.getY(0.33333) - arrow.getY();
		double dz = target.getZ() - anbula.getZ();
		double horiz = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + horiz * 0.4, dz, 1.6F, 
			anbula.level().getCurrentDifficultyAt(anbula.blockPosition()).getEffectiveDifficulty() > 2.0F ? 1.0F : 3.0F);
		anbula.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (anbula.getRandom().nextFloat() * 0.4F + 0.8F));
		anbula.level().addFreshEntity(arrow);
	}

	private void tickCrossbow(LivingEntity target, boolean canSee) {
		double dist = anbula.distanceToSqr(target);

		// 移动：保持距离
		if (dist < KITE_MIN_RANGE) {
			// 太近：后退
			Vec3 away = anbula.position().subtract(target.position()).normalize().scale(0.5);
			anbula.getNavigation().moveTo(
				anbula.getX() + away.x, anbula.getY(), anbula.getZ() + away.z, 1.0);
		} else if (dist > KITE_MAX_RANGE) {
			// 太远：追击
			anbula.getNavigation().moveTo(target, 1.0);
		}

		if (crossbowCooldown > 0 || !canSee) return;

		performCrossbowShot(target);
		crossbowCooldown = 30;
	}

	private void performCrossbowShot(LivingEntity target) {
		AbstractArrow arrow = ProjectileUtil.getMobArrow(anbula, anbula.getMainHandItem(), 1.0F, null);
		double dx = target.getX() - anbula.getX();
		double dy = target.getY(0.33333) - arrow.getY();
		double dz = target.getZ() - anbula.getZ();
		double horiz = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + horiz * 0.4, dz, 1.6F,
			anbula.level().getCurrentDifficultyAt(anbula.blockPosition()).getEffectiveDifficulty() > 2.0F ? 1.0F : 3.0F);
		anbula.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0F, 1.0F / (anbula.getRandom().nextFloat() * 0.4F + 0.8F));
		anbula.level().addFreshEntity(arrow);
	}

	@Override
	public void stop() {
		if (bowCharging) {
			anbula.stopUsingItem();
			bowCharging = false;
			bowChargeTime = 0;
		}
	}
}
