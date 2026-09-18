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

	/**
	 * 半透明：与 {@code SmallStangeCloudRenderer} 同一套（伪云系列的整只半透明，作者 2026-09-18）。
	 * 贴图本身也是半透明的，见 tools/cloud/make_cloud_translucent.py。
	 */
	@Override
	protected net.minecraft.client.renderer.RenderType getRenderType(StangeCloudEntity entity, boolean bodyVisible, boolean translucent,
			boolean glowing) {
		if (translucent) {
			return net.minecraft.client.renderer.RenderType.itemEntityTranslucentCull(getTextureLocation(entity));
		}
		if (!bodyVisible) {
			return glowing ? net.minecraft.client.renderer.RenderType.outline(getTextureLocation(entity)) : null;
		}
		return net.minecraft.client.renderer.RenderType.entityTranslucent(getTextureLocation(entity));
	}

	// 伪云无碰撞会穿过地形/树冠：方块光照拉满，避免在方块内渲染变黑
	@Override
	protected int getBlockLightLevel(StangeCloudEntity entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}
}
