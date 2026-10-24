package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.mcreator.mcanomalyarchives.network.FortunePulsePacket;
import net.mcreator.mcanomalyarchives.runtime.ServerTasks;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** 只表现已经触发的幸运/厄运，不加伤害、药水、概率或预警等待。 */
public final class PinkSheepFeedback {
	private PinkSheepFeedback() { }

	public static void lucky(ServerPlayer player, Entity sheep) {
		ServerLevel level = player.serverLevel();
		Vec3 from = sheep.position().add(0, sheep.getBbHeight() * .7, 0);
		Vec3 to = player.position().add(0, 1, 0);
		DustParticleOptions gold = new DustParticleOptions(new Vector3f(1f, .78f, .25f), 1.25f);
		for (int i = 0; i <= 24; i++) {
			double t = i / 24.0;
			Vec3 p = from.lerp(to, t).add(0, Math.sin(t * Math.PI) * .8, 0);
			level.sendParticles(gold, p.x, p.y, p.z, 1, .03, .03, .03, 0);
		}
		ring(level, player.position(), false, 1);
		PacketDistributor.sendToPlayer(player, new FortunePulsePacket(0));
		for (int note = 0; note < 3; note++) {
			float pitch = new float[] { .8f, 1f, 1.25f }[note];
			ServerTasks.scheduleForPlayer(player, note * 5, () -> {
				if (player.serverLevel() != level) return;
				level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
						SoundSource.PLAYERS, .8f, pitch);
			});
		}
	}

	public static void unlucky(ServerPlayer player, int severity) {
		ServerLevel level = player.serverLevel();
		ring(level, player.position(), true, severity);
		level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1, player.getZ(),
				12 + severity * 6, .7, .8, .7, .015);
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
				SoundSource.HOSTILE, 1f, .85f - severity * .1f);
		PacketDistributor.sendToPlayer(player, new FortunePulsePacket(severity));
	}

	private static void ring(ServerLevel level, Vec3 center, boolean dark, int severity) {
		DustParticleOptions dust = new DustParticleOptions(dark ? new Vector3f(.3f, .08f, .16f)
				: new Vector3f(.65f, 1f, .3f), 1.4f);
		for (int i = 0; i < 32; i++) {
			double angle = i * Math.PI / 16;
			double radius = dark ? 1 + severity * .2 : 1.25;
			level.sendParticles(dust, center.x + Math.cos(angle) * radius, center.y + .15,
					center.z + Math.sin(angle) * radius, 1, .02, .08, .02, 0);
		}
		if (!dark) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 1,
				center.z, 20, .6, .6, .6, .02);
	}
}
