package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;
import net.mcreator.mcanomalyarchives.compat.TaczCompat;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 安布拉冲锋技能：锁定 → 冲刺（路径伤害）→ 跳劈（范围伤害+击退）。
 * 冷却 240 ticks (12 秒)，仅在狂暴模式下对 5-15 格范围内的目标使用。
 */
public class AnbulaChargeGoal extends Goal {
	private final AnbulaEntity anbula;

	private static final int COOLDOWN_TICKS = 240;
	private static final double MIN_RANGE = 5.0;
	private static final double MAX_RANGE = 15.0;
	private static final double SPRINT_SPEED = 2.5;
	private static final float PATH_DAMAGE = 6.0F;
	private static final float LEAP_DAMAGE = 10.0F;
	private static final float KNOCKBACK = 0.5F;

	private static final int LOCK_ON_TICKS = 8;
	private static final int MAX_SPRINT_TICKS = 20;
	private static final int LEAP_TICKS = 10;

	private enum Phase { LOCK_ON, SPRINT, LEAP }
	private Phase phase;
	private int phaseTick;
	private int lastChargeTick = Integer.MIN_VALUE / 2;

	private Vec3 sprintDirection;
	private final Set<Integer> hitEntities = new HashSet<>();

	public AnbulaChargeGoal(AnbulaEntity anbula) {
		this.anbula = anbula;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!anbula.isBerserk() || anbula.isFaint()) return false;
		if (anbula.tickCount - lastChargeTick < COOLDOWN_TICKS) return false;

		LivingEntity target = anbula.getTarget();
		if (target == null || !target.isAlive()) return false;

		// 手持 TACZ 枪械时优先射击，不冲锋
		if (TaczCompat.isGun(anbula.getMainHandItem())) return false;

		double dist = anbula.distanceTo(target);
		return dist >= MIN_RANGE && dist <= MAX_RANGE;
	}

	@Override
	public boolean canContinueToUse() {
		// 冲锋一旦开始不可中断（技能总时长仅 1.5s，避免状态波动导致卡顿）
		// 但进入晕厥/脱离狂暴时必须终止：setNoAi(true) 不会回调 stop()，
		// 若不在此终止，chargeSprinting/chargeLeaping 标志会残留
		if (anbula.isFaint() || !anbula.isBerserk()) return false;
		return phaseTick < getPhaseDuration();
	}

	@Override
	public void start() {
		phase = Phase.LOCK_ON;
		phaseTick = 0;
		anbula.chargeSprinting = false;
		anbula.chargeLeaping = false;
		hitEntities.clear();

		LivingEntity target = anbula.getTarget();
		if (target != null) {
			Vec3 dir = target.position().subtract(anbula.position());
			sprintDirection = new Vec3(dir.x, 0, dir.z).normalize();
		}
	}

	@Override
	public void stop() {
		anbula.chargeSprinting = false;
		anbula.chargeLeaping = false;
		lastChargeTick = anbula.tickCount;
		anbula.setDeltaMovement(0, anbula.getDeltaMovement().y, 0);
	}

	@Override
	public void tick() {
		phaseTick++;

		switch (phase) {
			case LOCK_ON:
				tickLockOn();
				break;
			case SPRINT:
				tickSprint();
				break;
			case LEAP:
				tickLeap();
				break;
		}
	}

	private void tickLockOn() {
		LivingEntity target = anbula.getTarget();
		if (target != null) {
			anbula.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
		anbula.setDeltaMovement(0, anbula.getDeltaMovement().y, 0);

		if (phaseTick >= LOCK_ON_TICKS) {
			transitionTo(Phase.SPRINT);
		}
	}

	private void tickSprint() {
		anbula.chargeSprinting = true;
		anbula.chargeLeaping = false;

		// 高速移动
		Vec3 move = sprintDirection.scale(SPRINT_SPEED * 0.05);
		anbula.setDeltaMovement(move.x, anbula.getDeltaMovement().y, move.z);

		// 路径碰撞检测
		AABB sweepBox = anbula.getBoundingBox().inflate(1.5, 0.5, 1.5);
		for (Entity entity : anbula.level().getEntities(anbula, sweepBox)) {
			if (entity instanceof LivingEntity living && entity != anbula && living.isAlive()) {
				if (hitEntities.add(entity.getId())) {
					living.hurt(anbula.damageSources().mobAttack(anbula), PATH_DAMAGE);
					// 轻微击退
					Vec3 knock = living.position().subtract(anbula.position()).normalize().scale(KNOCKBACK);
					living.push(knock.x, 0.2, knock.z);
				}
			}
		}

		// 碰到目标或超时则进入跳劈
		LivingEntity target = anbula.getTarget();
		if (target != null && anbula.getBoundingBox().inflate(1.0).intersects(target.getBoundingBox())) {
			transitionTo(Phase.LEAP);
		} else if (phaseTick >= MAX_SPRINT_TICKS) {
			transitionTo(Phase.LEAP);
		}
	}

	private void tickLeap() {
		anbula.chargeSprinting = false;
		anbula.chargeLeaping = true;

		if (phaseTick == 1) {
			// 起跳
			anbula.setDeltaMovement(anbula.getDeltaMovement().x, 0.5, anbula.getDeltaMovement().z);
		}

		if (phaseTick >= LEAP_TICKS) {
			// 落地范围伤害
			if (anbula.level() instanceof ServerLevel serverLevel) {
				AABB aoeBox = anbula.getBoundingBox().inflate(2.0, 2.0, 2.0);
				for (Entity entity : anbula.level().getEntities(anbula, aoeBox)) {
					if (entity instanceof LivingEntity living && entity != anbula && living.isAlive()) {
						living.hurt(anbula.damageSources().mobAttack(anbula), LEAP_DAMAGE);
						Vec3 knock = living.position().subtract(anbula.position()).normalize().scale(KNOCKBACK * 1.5);
						living.push(knock.x, 0.3, knock.z);
					}
				}
			}
			stop();
		}
	}

	private void transitionTo(Phase newPhase) {
		phase = newPhase;
		phaseTick = 0;
	}

	private int getPhaseDuration() {
		return switch (phase) {
			case LOCK_ON -> LOCK_ON_TICKS;
			case SPRINT -> MAX_SPRINT_TICKS;
			case LEAP -> LEAP_TICKS;
		};
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}
}
