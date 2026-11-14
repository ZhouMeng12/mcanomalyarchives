package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.client.MouseHandler;
import net.mcreator.mcanomalyarchives.events.AddictionClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class PoppyMouseMixin {
    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void poppy$afterMouse(CallbackInfo ci) { AddictionClientHandler.controlGaze(); }
}
