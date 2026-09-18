package net.mcreator.mcanomalyarchives.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.mcanomalyarchives.client.model.ModelStrangeCloud;
import net.mcreator.mcanomalyarchives.entity.BlackStangeCloudEntity;

/**
 * 黑吸伪云的渲染器：与小伪云**同一个模型、同一套缩放**，只有贴图换成乌黑那张。
 *
 * <p>贴图是 16×16 纯色 #141419、alpha 130（`tools/cloud/make_black_texture.py` 生成）——
 * 不是纯黑：纯黑在游戏里会变成没有层次的剪影，加一丝冷调 + 模型各面的方向光之后，
 * 实际渲染在 #0F0F13 ~ #2A2A31 之间浮动，是一团有厚度的黑雾。
 */
public class BlackStangeCloudRenderer extends MobRenderer<BlackStangeCloudEntity, ModelStrangeCloud<BlackStangeCloudEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/cloud_black.png");

	public BlackStangeCloudRenderer(EntityRendererProvider.Context context) {
		super(context, new ModelStrangeCloud(context.bakeLayer(ModelStrangeCloud.LAYER_LOCATION)), 0f);
	}

	@Override
	public ResourceLocation getTextureLocation(BlackStangeCloudEntity entity) {
		return entityTexture;
	}

	@Override
	protected void scale(BlackStangeCloudEntity entity, PoseStack poseStack, float partialTickTime) {
		float s = BlackStangeCloudEntity.RENDER_SCALE; // 与小伪云同一个缩放：短横边 2 格
		poseStack.scale(s, s, s);
	}

	/** 半透明：与大小伪云同一套（贴图本身也是半透明的）。 */
	@Override
	protected RenderType getRenderType(BlackStangeCloudEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (translucent) {
			return RenderType.itemEntityTranslucentCull(getTextureLocation(entity));
		}
		if (!bodyVisible) {
			return glowing ? RenderType.outline(getTextureLocation(entity)) : null;
		}
		return RenderType.entityTranslucent(getTextureLocation(entity));
	}

	// 与大小伪云一样：无碰撞会穿过地形，方块光照拉满，避免在地下渲染成一团看不见的黑
	@Override
	protected int getBlockLightLevel(BlackStangeCloudEntity entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}
}
