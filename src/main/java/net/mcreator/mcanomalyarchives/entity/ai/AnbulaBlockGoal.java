package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;

import java.util.EnumSet;
import java.util.List;

/**
 * 安布拉举盾防御。
 * 副手有盾牌 + 附近有威胁（投射物或近战敌人）时自动举盾。
 */
public class AnbulaBlockGoal extends Goal {
	private final AnbulaEntity anbula;
	private static final double THREAT_RANGE = 3.0;
	private static final double PROJECTILE_RANGE = 8.0;
	private boolean isBlocking = false;
	private double originalSpeed;

	public AnbulaBlockGoal(AnbulaEntity anbula) {
		this.anbula = anbula;
		this.setFlags(EnumSet.of(Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!anbula.isBerserk() || anbula.isFaint()) return false;
		if (!hasShieldInOffhand()) return false;
		return isThreatNearby();
	}

	@Override
	public boolean canContinueToUse() {
		if (!anbula.isBerserk() || anbula.isFaint()) return false;
		if (!hasShieldInOffhand()) return false;
		return isThreatNearby();
	}

	private boolean hasShieldInOffhand() {
		ItemStack offhand = anbula.getItemBySlot(EquipmentSlot.OFFHAND);
		return !offhand.isEmpty() && AnbulaEntity.isShield(offhand);
	}

	@Override
	public void tick() {
		boolean threatNearby = isThreatNearby();

		if (threatNearby && !isBlocking) {
			// 开始举盾
			anbula.startUsingItem(InteractionHand.OFF_HAND);
			isBlocking = true;
			originalSpeed = anbula.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
			anbula.getAttribute(Attributes.MOVEMENT_SPEED)
				.setBaseValue(originalSpeed * 0.5); // 举盾减速
		} else if (!threatNearby && isBlocking) {
			// 取消举盾
			anbula.stopUsingItem();
			isBlocking = false;
			anbula.getAttribute(Attributes.MOVEMENT_SPEED)
				.setBaseValue(originalSpeed); // 恢复原速
		}
	}

	private boolean isThreatNearby() {
		// 检查附近是否有飞行中的投射物
		AABB projectileBox = anbula.getBoundingBox().inflate(PROJECTILE_RANGE);
		List<AbstractArrow> projectiles = anbula.level().getEntitiesOfClass(AbstractArrow.class, projectileBox,
				arrow -> arrow.getOwner() != anbula);
		if (!projectiles.isEmpty()) return true;

		// 检查附近是否有近战敌人在攻击范围内
		if (anbula.getTarget() != null && anbula.getTarget().isAlive()) {
			double dist = anbula.distanceToSqr(anbula.getTarget());
			if (dist <= THREAT_RANGE * THREAT_RANGE) return true;
		}

		return false;
	}

	@Override
	public void stop() {
		if (isBlocking) {
			anbula.stopUsingItem();
			isBlocking = false;
			anbula.getAttribute(Attributes.MOVEMENT_SPEED)
				.setBaseValue(originalSpeed);
		}
	}
}
