package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;

/**
 * 安布拉近战 AI：绕圈走位 + combo连击。
 * 继承 MeleeAttackGoal 复用攻击逻辑，覆写移动行为。
 */
public class AnbulaMeleeGoal extends MeleeAttackGoal {
	private final AnbulaEntity anbula;
	private static final double CIRCLE_RADIUS = 1.5;
	private static final int CIRCLE_STEP_INTERVAL = 20;
	private static final int COMBO_TIMEOUT = 40; // 2秒未命中归零

	private int circleAngle = 0;
	private int circleTimer = 0;
	private int comboTimer = 0;
	private int comboCount = 0;
	private int tickUntilAttack = 0;
	private int retreatTicks = 0;
	private final double pursueSpeed;

	public AnbulaMeleeGoal(AnbulaEntity mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
		super(mob, speedModifier, followingTargetEvenIfNotSeen);
		this.anbula = mob;
		this.pursueSpeed = speedModifier;
	}

	@Override
	public boolean canUse() {
		if (!anbula.isBerserk() || anbula.isFaint()) return false;
		return super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		if (!anbula.isBerserk() || anbula.isFaint()) return false;
		return super.canContinueToUse();
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target != null) {
			this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

			// 攻击后退
			if (retreatTicks > 0) {
				retreatTicks--;
				Vec3 away = this.mob.position().subtract(target.position()).normalize();
				double retreatSpeed = 0.15;
				this.mob.setPos(
					this.mob.getX() + away.x * retreatSpeed,
					this.mob.getY(),
					this.mob.getZ() + away.z * retreatSpeed
				);
				return; // 后退期间不攻击也不绕圈，专注位移
			}

			// 自定义攻击冷却
			if (tickUntilAttack > 0) {
				tickUntilAttack--;
			}

			// 执行攻击
			double dist = this.mob.distanceToSqr(target);
			double attackRange = this.mob.getBbWidth() * 2.0 * this.mob.getBbWidth() * 2.0 + target.getBbWidth();
			if (tickUntilAttack <= 0
					&& dist < attackRange
					&& this.mob.getSensing().hasLineOfSight(target)) {
				tickUntilAttack = getBaseInterval();
				this.mob.swing(InteractionHand.MAIN_HAND);
				if (this.mob.level() instanceof net.minecraft.server.level.ServerLevel) {
				this.mob.doHurtTarget(target);
			}
				// 攻击后后退
				retreatTicks = 8;
			} else if (dist >= attackRange) {
				// 超出攻击范围：主动追击，避免站在攻击范围外发呆
				// （原实现完全无追击逻辑，且本 goal 的 MOVE 标志会阻塞兜底的 MoveTowardsTargetGoal）
				this.mob.getNavigation().moveTo(target, this.pursueSpeed);
			}
		}

		// combo 超时归零
		if (target != null) {
			comboTimer++;
			if (comboTimer > COMBO_TIMEOUT) {
				comboCount = 0;
			}

			// 绕圈走位（更紧、更激进）
			double dist = this.mob.distanceToSqr(target);
			if (dist <= CIRCLE_RADIUS * CIRCLE_RADIUS && retreatTicks <= 0) {
				circleTimer++;
				if (circleTimer >= CIRCLE_STEP_INTERVAL) {
					circleTimer = 0;
					circleAngle = (circleAngle + 60) % 360;
					double rad = Math.toRadians(circleAngle);
					double targetX = target.getX() + Math.cos(rad) * CIRCLE_RADIUS;
					double targetZ = target.getZ() + Math.sin(rad) * CIRCLE_RADIUS;
					this.mob.getNavigation().moveTo(targetX, target.getY(), targetZ, this.mob.getSpeed());
				}
			}
		}
	}

	private int getBaseInterval() {
		return switch (comboCount) {
			case 0 -> 20;
			case 1 -> 17;
			case 2 -> 14;
			default -> 12; // combo 3+
		};
	}

	/** 由 AnbulaEntity 在攻击命中时调用，增加 combo 计数 */
	public void onAttackHit() {
		comboCount = Math.min(comboCount + 1, 5);
		comboTimer = 0;
	}
}
