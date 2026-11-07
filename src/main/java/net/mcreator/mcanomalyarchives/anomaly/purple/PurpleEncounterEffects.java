package net.mcreator.mcanomalyarchives.anomaly.purple;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/** Small, world-space cues shared by natural encounters and stalkers. */
public final class PurpleEncounterEffects {
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(.40f, .12f, .60f), .8f);

    private PurpleEncounterEffects() { }

    public static void warning(ServerPlayer player, int ticksRemaining) {
        if (ticksRemaining != 80 && ticksRemaining != 40 && ticksRemaining != 10) return;
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT,
                .22f, ticksRemaining == 10 ? .55f : .8f);
        var level = player.serverLevel();
        var look = player.getLookAngle();
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            level.sendParticles(player, VIOLET, false,
                    player.getX() + Math.cos(angle) * 1.6 - look.x * .6,
                    player.getY() + .2 + i * .06,
                    player.getZ() + Math.sin(angle) * 1.6 - look.z * .6,
                    1, .05, .08, .05, 0);
        }
    }

    public static void materialize(Entity actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            double radius = .35 + (i % 3) * .15;
            level.sendParticles(VIOLET, actor.getX() + Math.cos(angle) * radius,
                    actor.getY() + (i % 8) * .20, actor.getZ() + Math.sin(angle) * radius,
                    1, .04, .08, .04, 0);
        }
        level.playSound(null, actor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.AMBIENT, .4f, .55f);
    }

    public static void dissolve(Entity actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, actor.getX(), actor.getY() + .9, actor.getZ(),
                20, .22, .55, .22, .015);
        level.sendParticles(VIOLET, actor.getX(), actor.getY() + .6, actor.getZ(), 12, .3, .45, .3, 0);
        level.playSound(null, actor.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, .35f, .65f);
    }

    public static void watched(Entity actor, int ticks) {
        if ((ticks != 20 && ticks != 40 && ticks != 60) || !(actor.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, actor.getX(), actor.getY() + .9, actor.getZ(),
                5, .18, .4, .18, .005);
        if (ticks == 60) level.playSound(null, actor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.AMBIENT, .2f, .65f);
    }
}
