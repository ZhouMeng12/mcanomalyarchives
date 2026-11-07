package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Player-name identity used by UO-008. The named creature mirrors the online player's
 * position, facing, movement state, pose, and swing animation, while retaining its own
 * model and doing no copied damage or inventory actions.
 */
public final class NameImitation {
	private static final String TARGET_UUID = "mcanomalyarchives_name_mimic_uuid";
	private static final String SWINGING = "mcanomalyarchives_name_mimic_swinging";
	private static final double TRAIL_DISTANCE = 2.2;

	private NameImitation() {
	}

	public static void bind(LivingEntity mimic, ServerPlayer target) {
		mimic.getPersistentData().putString(TARGET_UUID, target.getUUID().toString());
		mimic.getPersistentData().putBoolean(SWINGING, false);
		mimic.setCustomName(target.getGameProfile().getName().isBlank()
				? target.getName() : net.minecraft.network.chat.Component.literal(target.getGameProfile().getName()));
		mimic.setCustomNameVisible(true);
		if (mimic instanceof Mob mob) {
			mob.setTarget(null);
			mob.setNoAi(true);
		}
	}

	public static boolean isMimic(LivingEntity entity) {
		return entity.getPersistentData().hasUUID(TARGET_UUID);
	}

	/** Runs every entity tick for exact, no-delay action mirroring. */
	public static void tick(ServerLevel level, LivingEntity mimic) {
		if (!isMimic(mimic) || mimic.isDeadOrDying()) {
			return;
		}
		UUID targetId = mimic.getPersistentData().getUUID(TARGET_UUID);
		ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetId);
		if (target == null || target.level() != level || !target.isAlive()) {
			return;
		}

		Vec3 look = target.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0, look.z);
		if (horizontal.lengthSqr() < 1.0e-6) {
			horizontal = Vec3.directionFromRotation(0, target.getYRot());
		}
		horizontal = horizontal.normalize();
		Vec3 desired = target.position().subtract(horizontal.scale(TRAIL_DISTANCE));
		moveToOpenPosition(mimic, desired);

		mimic.setYRot(target.getYRot());
		mimic.setXRot(target.getXRot());
		mimic.setYHeadRot(target.getYHeadRot());
		mimic.setYBodyRot(target.getYRot());
		mimic.setSprinting(target.isSprinting());
		mimic.setPose(target.getPose());
		mimic.setDeltaMovement(target.getDeltaMovement());

		boolean swingNow = target.getAttackAnim(0.0f) > 0.0f;
		boolean wasSwinging = mimic.getPersistentData().getBoolean(SWINGING);
		if (swingNow && !wasSwinging) {
			mimic.swing(InteractionHand.MAIN_HAND);
		}
		mimic.getPersistentData().putBoolean(SWINGING, swingNow);
	}

	private static void moveToOpenPosition(LivingEntity mimic, Vec3 desired) {
		Vec3[] offsets = {
				Vec3.ZERO,
				new Vec3(0.9, 0, 0), new Vec3(-0.9, 0, 0),
				new Vec3(0, 0, 0.9), new Vec3(0, 0, -0.9),
				new Vec3(0.65, 0, 0.65), new Vec3(-0.65, 0, 0.65),
				new Vec3(0.65, 0, -0.65), new Vec3(-0.65, 0, -0.65)
		};
		for (Vec3 offset : offsets) {
			Vec3 candidate = desired.add(offset);
			AABB candidateBox = mimic.getBoundingBox().move(candidate.subtract(mimic.position()));
			if (mimic.level().noCollision(mimic, candidateBox)) {
				mimic.setPos(candidate.x, candidate.y, candidate.z);
				return;
			}
		}
	}
}
