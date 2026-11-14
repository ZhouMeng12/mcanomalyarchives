package net.mcreator.mcanomalyarchives.anomaly.cloud;

import net.mcreator.mcanomalyarchives.entity.StangeCloudEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;

/** A clouded animal keeps its offset from one nearby cloud rather than chasing its position. */
public final class CloudDriftBinding {
    private static final String TARGET = "CloudDriftTarget";
    private static final String NEXT_SEARCH = "CloudDriftNextSearch";
    private CloudDriftBinding() {}

    public static Vec3 velocity(LivingEntity animal) {
        if (!(animal.level() instanceof ServerLevel level)) return Vec3.ZERO;
        var data = animal.getPersistentData();
        if (data.hasUUID(TARGET)) {
            var entity = level.getEntity(data.getUUID(TARGET));
            if (entity instanceof StangeCloudEntity cloud && cloud.isAlive() && cloud.distanceToSqr(animal) <= 160 * 160) {
                return cloud.cloudDrift();
            }
            data.remove(TARGET);
        }
        long now = level.getGameTime();
        long next = data.getLong(NEXT_SEARCH);
        // Also recover immediately after crossing into a dimension with a different clock.
        if (now >= next || next > now + 40) {
            data.putLong(NEXT_SEARCH, now + 40);
            var nearest = level.getEntitiesOfClass(StangeCloudEntity.class, animal.getBoundingBox().inflate(128),
                    cloud -> cloud.isAlive() && cloud.distanceToSqr(animal) <= 128 * 128).stream()
                    .min(Comparator.comparingDouble(animal::distanceToSqr));
            if (nearest.isPresent()) {
                data.putUUID(TARGET, nearest.get().getUUID());
                return nearest.get().cloudDrift();
            }
        }
        return new Vec3(.04, 0, 0); // Existing drift when no loaded cloud is available.
    }

    public static void clear(LivingEntity animal) {
        animal.getPersistentData().remove(TARGET);
        animal.getPersistentData().remove(NEXT_SEARCH);
    }
}
