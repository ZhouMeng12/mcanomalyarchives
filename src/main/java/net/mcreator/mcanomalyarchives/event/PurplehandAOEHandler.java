package net.mcreator.mcanomalyarchives.event;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.joml.Vector3f;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public class PurplehandAOEHandler {

	private static final Map<UUID, Long> lastSweep = new ConcurrentHashMap<>();
	private static final double RANGE = 5.0;
	private static final double HALF_ANGLE = Math.toRadians(70);
	private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(new Vector3f(0.65F, 0.15F, 0.85F), 1.1F);

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
			sweep(player, event.getTarget());
		}
	}

	public static void swingAtAir(ServerPlayer player) {
		if (sweep(player, null)) {
			player.resetAttackStrengthTicker();
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		lastSweep.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		lastSweep.clear();
	}

	private static boolean sweep(ServerPlayer attacker, Entity primaryTarget) {
		if (!attacker.isAlive() || attacker.isSpectator() || attacker.isUsingItem()
				|| !attacker.getMainHandItem().is(McanomalyarchivesModItems.PURPLEHAND.get())) {
			return false;
		}
		ServerLevel level = attacker.serverLevel();
		long now = level.getGameTime();
		Long previous = lastSweep.get(attacker.getUUID());
		if (previous != null && now >= previous && now - previous < 3) {
			return false;
		}
		lastSweep.put(attacker.getUUID(), now);
		// 每次左键均横扫，快速连点仍沿用原版的蓄力伤害曲线。
		float charge = attacker.getAttackStrengthScale(0.5F);
		float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + charge * charge * 0.8F);
		Vec3 forward = Vec3.directionFromRotation(0, attacker.getYRot());
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(RANGE, 1.5, RANGE))) {
			if (target == attacker || target == primaryTarget || !target.isAlive() || !target.isAttackable()
					|| target.isSpectator() || attacker.isAlliedTo(target)) {
				continue;
			}
			Vec3 offset = target.position().subtract(attacker.position()).multiply(1, 0, 1);
			if (offset.length() > RANGE + target.getBbWidth() * 0.5
					|| (offset.lengthSqr() > 0.01 && forward.dot(offset.normalize()) < Math.cos(HALF_ANGLE))
					|| !attacker.hasLineOfSight(target)) {
				continue;
			}
			if (target.hurt(attacker.damageSources().playerAttack(attacker), damage)) {
				target.knockback(0.25 + 0.15 * charge, -offset.x, -offset.z);
				impact(level, target);
			}
		}
		level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
				SoundSource.PLAYERS, 0.65F, 1.05F + level.random.nextFloat() * 0.15F);
		for (int i = 0; i <= 12; i++) {
			double angle = -HALF_ANGLE + 2 * HALF_ANGLE * i / 12.0;
			Vec3 point = attacker.position().add(forward.yRot((float) angle).scale(2.8)).add(0, 1, 0);
			level.sendParticles(PURPLE_DUST, point.x, point.y, point.z, 1, 0.05, 0.08, 0.05, 0);
		}
		return true;
	}

	public static void impact(ServerLevel level, LivingEntity target) {
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.55,
				target.getZ(), 5, target.getBbWidth() * 0.25, 0.15, target.getBbWidth() * 0.25, 0.08);
	}
}
