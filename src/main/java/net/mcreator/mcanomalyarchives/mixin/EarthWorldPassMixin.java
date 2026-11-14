package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.mcreator.mcanomalyarchives.client.earth.EarthTerrainWarp;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class EarthWorldPassMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void earth$begin(DeltaTracker timer, boolean outline, Camera camera, GameRenderer renderer,
                             LightTexture light, Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        EarthTerrainWarp.begin(camera, view, timer.getGameTimeDeltaPartialTick(false));
    }
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void earth$end(CallbackInfo ci) { EarthTerrainWarp.end(); }
}
