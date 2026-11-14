package net.mcreator.mcanomalyarchives.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.mcreator.mcanomalyarchives.client.purple.PurpleAssimilationClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PurplePlayerBodyMixin {
    @Shadow protected EntityModel<?> model;
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", shift = At.Shift.AFTER))
    private void purple$skin(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                             int light, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player) || !(model instanceof PlayerModel<?> skin)) return;
        int arms = PurpleAssimilationClient.color(player, false), body = PurpleAssimilationClient.color(player, true);
        if ((arms >>> 24) == 0) return;
        var buffer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        skin.leftArm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, arms);
        skin.rightArm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, arms);
        skin.leftSleeve.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, arms);
        skin.rightSleeve.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, arms);
        if ((body >>> 24) > 0) {
            for (var part : java.util.List.of(skin.body, skin.jacket, skin.head, skin.hat,
                    skin.leftLeg, skin.rightLeg, skin.leftPants, skin.rightPants))
                part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, body);
        }
    }
}
