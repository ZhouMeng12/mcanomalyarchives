package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;

import net.mcreator.mcanomalyarchives.entity.PurpleArrowEntity;
import net.mcreator.mcanomalyarchives.custommodel.ModelpurplechushouGeo;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

public class PurpleArrowRenderer extends EntityRenderer<PurpleArrowEntity> {
	private static final ResourceLocation texture = ResourceLocation.parse("mcanomalyarchives:textures/entities/purpletexture.png");
	private final ModelpurplechushouGeo model;

	public PurpleArrowRenderer(EntityRendererProvider.Context context) {
		super(context);
		model = new ModelpurplechushouGeo(context.bakeLayer(ModelpurplechushouGeo.LAYER_LOCATION));
	}

	@Override
	public void render(PurpleArrowEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferIn, int packedLightIn) {
		VertexConsumer vb = bufferIn.getBuffer(RenderType.entityCutout(texture));
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot() - 90));
		poseStack.mulPose(Axis.ZP.rotationDegrees(90 + entity.getXRot()));
		model.setupAnim(entity, 0, 0, 0, 0, 0);
		model.renderToBuffer(poseStack, vb, packedLightIn, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
		super.render(entity, entityYaw, partialTicks, poseStack, bufferIn, packedLightIn);
	}

	@Override
	public ResourceLocation getTextureLocation(PurpleArrowEntity entity) {
		return texture;
	}
}
