package net.mcreator.mcanomalyarchives.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.mcanomalyarchives.client.model.ModelStrangeCloud;
import net.mcreator.mcanomalyarchives.entity.RedStangeCloudEntity;

/**
 * 红伪云的渲染器：与小伪云**同一个模型、同一套缩放**，只有贴图换成虞美人主色那一张红。
 *
 * <p>贴图是 16×16 纯色 #E02020、alpha 130（`tools/cloud/make_red_texture.py` 生成，
 * 颜色是从虞美人贴图里统计出来的主色，保证两者看起来是一族）。
 */
public class RedStangeCloudRenderer extends MobRenderer<RedStangeCloudEntity, ModelStrangeCloud<RedStangeCloudEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/cloud_red.png");

	public RedStangeCloudRenderer(EntityRendererProvider.Context context) {
		super(context, new ModelStrangeCloud(context.bakeLayer(ModelStrangeCloud.LAYER_LOCATION)), 0f);
	}

	@Override
	public ResourceLocation getTextureLocation(RedStangeCloudEntity entity) {
		return entityTexture;
	}

	@Override
	protected void scale(RedStangeCloudEntity entity, PoseStack poseStack, float partialTickTime) {
		float s = RedStangeCloudEntity.RENDER_SCALE; // 与小伪云同一个缩放：短横边 2 格
		poseStack.scale(s, s, s);
	}

	/** 半透明：与大小伪云同一套（贴图本身也是半透明的）。 */
	@Override
	protected RenderType getRenderType(RedStangeCloudEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (translucent) {
			return RenderType.itemEntityTranslucentCull(getTextureLocation(entity));
		}
		if (!bodyVisible) {
			return glowing ? RenderType.outline(getTextureLocation(entity)) : null;
		}
		return RenderType.entityTranslucent(getTextureLocation(entity));
	}

	@Override
	protected int getBlockLightLevel(RedStangeCloudEntity entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}
}
