package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.mcreator.mcanomalyarchives.anomaly.rewind.RewindService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxBlockEntity.class)
public abstract class RewindJukeboxMixin {
    @Inject(method = "setTheItem", at = @At("TAIL"))
    private void rewind$insert(CallbackInfo ci) { RewindService.inserted((JukeboxBlockEntity)(Object)this); }
}
