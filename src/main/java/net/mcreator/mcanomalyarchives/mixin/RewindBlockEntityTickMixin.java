package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.mcreator.mcanomalyarchives.anomaly.rewind.RewindService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class RewindBlockEntityTickMixin {
    @Shadow @Final private BlockEntity blockEntity;
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void rewind$freeze(CallbackInfo ci) {
        if (RewindService.frozen(blockEntity.getLevel(), blockEntity.getBlockPos())) ci.cancel();
    }
}
