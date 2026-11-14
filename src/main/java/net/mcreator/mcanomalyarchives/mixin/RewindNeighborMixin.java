package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.mcreator.mcanomalyarchives.anomaly.rewind.RewindService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CollectingNeighborUpdater.class)
public abstract class RewindNeighborMixin {
    @Shadow @Final private Level level;
    // Historical block states already contain their redstone state. Do not run half-restored neighbours.
    @Inject(method = "addAndRun", at = @At("HEAD"), cancellable = true)
    private void rewind$restoreWithoutCascades(CallbackInfo ci) {
        if (!level.isClientSide && RewindService.restoring()) ci.cancel();
    }
}
