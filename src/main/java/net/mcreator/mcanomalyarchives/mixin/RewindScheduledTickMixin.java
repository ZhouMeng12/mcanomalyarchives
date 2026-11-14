package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.mcreator.mcanomalyarchives.anomaly.rewind.RewindService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class RewindScheduledTickMixin {
    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void rewind$block(BlockPos pos, Block block, CallbackInfo ci) {
        if (RewindService.frozenCore((ServerLevel)(Object)this, pos)) ci.cancel();
    }
    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void rewind$fluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        if (RewindService.frozenCore((ServerLevel)(Object)this, pos)) ci.cancel();
    }
}
