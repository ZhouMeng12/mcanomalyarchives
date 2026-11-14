package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.mcreator.mcanomalyarchives.anomaly.rewind.RewindService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class RewindChunkMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void rewind$change(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> ci) {
        LevelChunk chunk = (LevelChunk)(Object)this;
        if (!(chunk.getLevel() instanceof ServerLevel level)) return;
        if (RewindService.frozenCore(level, pos)) { ci.setReturnValue(null); return; }
        RewindService.beforeBlockChange(level, pos, chunk.getBlockState(pos), state);
    }
}
