package net.mcreator.mcanomalyarchives.anomaly.negative;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.mcreator.mcanomalyarchives.entity.NegativeReturnArrowEntity;
import net.mcreator.mcanomalyarchives.init.UO007Entities;
import net.mcreator.mcanomalyarchives.runtime.ServerTasks;
import net.mcreator.mcanomalyarchives.runtime.ServerTaskQueue;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Short-lived player-owned tasks; never load remote chunks or retain debt across player sessions. */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class NegativeBowReturn {
    public static final int DELAY_TICKS = 100;
    public static final double DISTANCE = 32;
    private static final String EPOCH = "UO007ReturnArrowEpoch";

    private NegativeBowReturn() { }

    public static int epoch(ServerPlayer player) {
        return player.getPersistentData().getInt(EPOCH);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().putInt(EPOCH, epoch(player) + 1);
        }
    }

    public static boolean queue(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int epoch = epoch(player);
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        HitResult hit = level.clip(new ClipContext(eye, eye.add(direction.scale(DISTANCE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        // Return from just in front of the aimed-at surface, along the unobstructed shot path.
        Vec3 source = hit.getType() == HitResult.Type.MISS ? hit.getLocation()
                : hit.getLocation().subtract(direction.scale(.4));
        return ServerTasks.scheduleForPlayer(player, DELAY_TICKS, () -> {
            if (player.serverLevel() != level || epoch(player) != epoch || player.isSpectator()
                    || player.distanceToSqr(source) > 96 * 96
                    || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(source))) return;
            NegativeReturnArrowEntity arrow = UO007Entities.RETURN_ARROW.get().create(level);
            if (arrow == null) return;
            arrow.aimAt(player, source);
            if (level.addFreshEntity(arrow)) {
                Vec3 axis = player.getBoundingBox().getCenter().subtract(source).normalize();
                Vec3 side = axis.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < .01) side = axis.cross(new Vec3(1, 0, 0));
                side = side.normalize();
                Vec3 up = side.cross(axis).normalize();
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.72f, .45f, 1f), 1f);
                for (int i = 0; i < 24; i++) {
                    double angle = Math.PI * 2 * i / 24;
                    Vec3 point = source.add(side.scale(Math.cos(angle) * .65)).add(up.scale(Math.sin(angle) * .65));
                    level.sendParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                }
            }
        }) != ServerTaskQueue.rejected();
    }
}
