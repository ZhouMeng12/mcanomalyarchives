package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import net.mcreator.mcanomalyarchives.entity.StangeCloudEntity;
import net.mcreator.mcanomalyarchives.client.model.ModelStrangeCloud;

import com.mojang.blaze3d.vertex.PoseStack;

public class StangeCloudRenderer extends MobRenderer<StangeCloudEntity, ModelStrangeCloud<StangeCloudEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/texture.png");

	public StangeCloudRenderer(EntityRendererProvider.Context context) {
		super(context, new ModelStrangeCloud(context.bakeLayer(ModelStrangeCloud.LAYER_LOCATION)), 0f);
	}

	@Override
	public ResourceLocation getTextureLocation(StangeCloudEntity entity) {
		return entityTexture;
	}

	@Override
	protected void scale(StangeCloudEntity entity, PoseStack poseStack, float partialTickTime) {
		poseStack.scale(20f, 20f, 20f);
	}

	// 伪云无碰撞会穿过地形/树冠：方块光照拉满，避免在方块内渲染变黑
	@Override
	protected int getBlockLightLevel(StangeCloudEntity entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}
}
