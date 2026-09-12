package net.mcreator.mcanomalyarchives.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.client.TreeQuakeClientHandler;

/** 怪树钻地震动：每次相机 setup 后叠加抖动偏移（HUD 不受影响），帧末递减计时 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {

    @Shadow
    private Vec3 position;

    @Inject(method = "setup", at = @At("RETURN"))
    private void mcanomalyarchives$shake(BlockGetter level, Entity entity, boolean detached, boolean thirdPerson, float partialTick, CallbackInfo ci) {
        if (TreeQuakeClientHandler.isShaking()) {
            this.position = this.position.add(TreeQuakeClientHandler.getOffset());
        }
    }
}
