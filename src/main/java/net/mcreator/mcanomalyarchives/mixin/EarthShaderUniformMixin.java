package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import net.mcreator.mcanomalyarchives.client.earth.EarthTerrainWarp;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderInstance.class)
public abstract class EarthShaderUniformMixin {
    @Unique private int earth$strength = -2, earth$center = -1, earth$up = -1;
    @Inject(method = "apply", at = @At("TAIL"))
    private void earth$uniforms(CallbackInfo ci) {
        if (earth$strength == -2) {
            int program = ((ShaderInstance)(Object)this).getId();
            earth$strength = GL20.glGetUniformLocation(program, "McaEarthStrength");
            if (earth$strength >= 0) {
                earth$center = GL20.glGetUniformLocation(program, "McaEarthCenter");
                earth$up = GL20.glGetUniformLocation(program, "McaEarthUp");
            }
        }
        if (earth$strength < 0) return;
        float amount = EarthTerrainWarp.strength();
        GL20.glUniform1f(earth$strength, amount);
        if (amount > 0) {
            var c = EarthTerrainWarp.CENTER; var u = EarthTerrainWarp.UP;
            GL20.glUniform3f(earth$center, c.x, c.y, c.z);
            GL20.glUniform3f(earth$up, u.x, u.y, u.z);
        }
    }
}
