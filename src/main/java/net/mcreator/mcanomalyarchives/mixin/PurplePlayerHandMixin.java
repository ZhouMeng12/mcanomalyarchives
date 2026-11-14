package net.mcreator.mcanomalyarchives.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.mcreator.mcanomalyarchives.client.purple.PurpleAssimilationClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PurplePlayerHandMixin {
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void purple$arm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                            ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        int color = PurpleAssimilationClient.color(player, false);
        if ((color >>> 24) == 0) return;
        var buffer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        arm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, color);
        sleeve.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, color);
    }
}
