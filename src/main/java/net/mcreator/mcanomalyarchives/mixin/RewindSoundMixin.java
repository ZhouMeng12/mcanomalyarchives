package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.mcreator.mcanomalyarchives.client.rewind.RewindClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class RewindSoundMixin {
    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void rewind$muffle(float volume, SoundSource source, CallbackInfoReturnable<Float> ci) {
        if (RewindClient.localFrozen() && source != SoundSource.RECORDS && source != SoundSource.MASTER)
            ci.setReturnValue(ci.getReturnValue() * .18f);
    }
}
