package net.mcreator.mcanomalyarchives.anomaly.fishing;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.item.StrangeFishingRodItem;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

/** 跨维钓线的空间裂口：仅使用世界内的声音与粒子，不改变战利品或事件骰子。 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class CrossDimensionFishingEffects {
	private CrossDimensionFishingEffects() { }

	private static boolean hasRod(Player player) {
		return player != null && (player.getMainHandItem().getItem() instanceof StrangeFishingRodItem
				|| player.getOffhandItem().getItem() instanceof StrangeFishingRodItem);
	}

	@SubscribeEvent
	public static void tickHook(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof FishingHook hook) || !(hook.level() instanceof ServerLevel level)
				|| hook.isRemoved() || hook.tickCount % 10 != 0 || !hook.isInWater()) return;
		Player player = hook.getPlayerOwner();
		if (!hasRod(player) || player.level() != level || !player.isAlive()) return;
		double intensity = Math.clamp((hook.distanceTo(player) - 2) / 13.0, 0, 1);
		if (intensity < .15) return;
		double radius = .2 + intensity * .6;
		for (int i = 0; i < 10; i++) {
			double angle = i * Math.PI / 5 + hook.tickCount * .12;
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, hook.getX() + Math.cos(angle) * radius,
					hook.getY() + .12 + Math.sin(angle * 2) * .1, hook.getZ() + Math.sin(angle) * radius,
					1, .015, .02, .015, .015);
		}
		if (hook.tickCount % 80 == 0)
			level.playSound(null, hook.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT,
					(float) (.2 + intensity * .45), (float) (1.3 - intensity * .6));
	}

	public static void open(ServerLevel level, FishingHook hook, Player player, double weirdness) {
		Vec3 center = hook.position().add(0, .45, 0);
		DustParticleOptions edge = new DustParticleOptions(new Vector3f(.55f, .2f, .95f), 1.2f);
		double radius = .5 + weirdness * 1.1;
		Vec3 axis = player.position().subtract(center).multiply(1, 0, 1).normalize();
		if (axis.lengthSqr() < .01) axis = new Vec3(1, 0, 0);
		Vec3 side = new Vec3(-axis.z, 0, axis.x);
		for (int i = 0; i < 40; i++) {
			double angle = i * Math.PI / 20;
			Vec3 p = center.add(side.scale(Math.cos(angle) * radius)).add(0, Math.sin(angle) * radius * .65, 0);
			level.sendParticles(edge, p.x, p.y, p.z, 1, .02, .02, .02, 0);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
				20, radius * .4, radius * .3, radius * .4, .08);
		// 收线方向的残留让物品直接进背包时，仍能看到它从裂口被拉过来。
		Vec3 end = player.position().add(0, 1, 0);
		for (int i = 0; i < 24; i++) {
			Vec3 p = center.lerp(end, i / 24.0);
			level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 1, .04, .04, .04, .02);
		}
		level.playSound(null, hook.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
				.9f, (float) (1.25 - weirdness * .5));
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
				.45f, (float) (.8 + weirdness * .5));
	}

	/** 来物的残留特征：下界余烬、末地星光、模组异物的苍白云气。 */
	public static void arrival(ServerLevel level, FishingHook hook, ResourceLocation id) {
		if (id == null) return;
		String path = id.getPath();
		boolean nether = path.contains("nether") || path.contains("blaze") || path.contains("ghast")
				|| path.contains("magma") || path.contains("piglin") || path.contains("wither");
		boolean end = path.contains("ender") || path.contains("dragon") || path.contains("shulker")
				|| path.equals("elytra") || path.contains("end_stone");
		ParticleOptions particle = nether ? ParticleTypes.FLAME : end ? ParticleTypes.END_ROD
				: id.getNamespace().equals(McanomalyarchivesMod.MODID) ? ParticleTypes.CLOUD : ParticleTypes.PORTAL;
		level.sendParticles(particle, hook.getX(), hook.getY() + .6, hook.getZ(), 24, .5, .5, .5, .035);
		if (nether) level.playSound(null, hook.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.AMBIENT, .8f, .7f);
		else if (end) level.playSound(null, hook.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, .8f, .7f);
	}
}
