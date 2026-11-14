package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.mcreator.mcanomalyarchives.anomaly.rewind.RewindService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class RewindRandomTickMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void rewind$random(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (RewindService.frozenCore(level, pos)) ci.cancel();
    }
}
