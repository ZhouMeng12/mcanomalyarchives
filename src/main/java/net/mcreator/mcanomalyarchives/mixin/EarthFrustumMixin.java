package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.mcreator.mcanomalyarchives.client.earth.EarthClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep tall geometry that can bend into the view from being culled at its original bounds. */
@Mixin(Frustum.class)
public abstract class EarthFrustumMixin {
    @Unique private boolean earth$expandedCheck;
    @Inject(method = "isVisible", at = @At("RETURN"), cancellable = true)
    private void earth$expandedBounds(AABB box, CallbackInfoReturnable<Boolean> ci) {
        if (earth$expandedCheck || ci.getReturnValueZ() || !EarthClient.active()) return;
        float age = EarthClient.age(0);
        if (age <= 0 || age >= 612) return;
        var center = EarthClient.center();
        double x = center.getX() + .5, y = center.getY() + .5, z = center.getZ() + .5;
        double dx = x - Math.max(box.minX, Math.min(box.maxX, x));
        double dz = z - Math.max(box.minZ, Math.min(box.maxZ, z));
        if (dx * dx + dz * dz > 128 * 128 || box.minY > y + 256 || box.maxY < y - 256) return;
        earth$expandedCheck = true;
        try {
            ci.setReturnValue(((Frustum)(Object)this).isVisible(new AABB(box.minX - 80, box.minY - 128,
                box.minZ - 80, box.maxX + 80, box.maxY, box.maxZ + 80)));
        } finally { earth$expandedCheck = false; }
    }
}
