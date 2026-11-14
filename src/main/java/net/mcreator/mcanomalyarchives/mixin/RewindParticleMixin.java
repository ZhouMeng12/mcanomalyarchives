package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.mcreator.mcanomalyarchives.client.rewind.RewindClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class RewindParticleMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void rewind$particles(CallbackInfo ci) { if (RewindClient.localFrozen()) ci.cancel(); }
}
