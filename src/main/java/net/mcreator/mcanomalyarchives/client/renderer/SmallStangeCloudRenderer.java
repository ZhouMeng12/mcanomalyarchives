package net.mcreator.mcanomalyarchives.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.mcanomalyarchives.client.model.ModelStrangeCloud;
import net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity;

/**
 * 小伪云的渲染器：**与大伪云共用同一个模型与同一张贴图**，只有缩放不同。
 *
 * <p>缩放 2.6667 = 20 × (2/15)：大伪云把同一个模型（短横边 0.75 格）放大 20 倍得到 15 格，
 * 这里要短横边正好 2 格。于是视觉尺寸是 长 4.0 × 高 0.667 × 短 2.0 格。
 *
 * <p>元素 JSON 里的 {@code visualScale} 留的是 1.0（MCreator 界面更稳），
 * 真正的缩放写在这里；这个文件是元素自己的文件、元素带 {@code locked_code: true}，
 * 所以 MCreator 重新生成不会把它改回去。
 */
public class SmallStangeCloudRenderer extends MobRenderer<SmallStangeCloudEntity, ModelStrangeCloud<SmallStangeCloudEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/texture.png");

	public SmallStangeCloudRenderer(EntityRendererProvider.Context context) {
		super(context, new ModelStrangeCloud(context.bakeLayer(ModelStrangeCloud.LAYER_LOCATION)), 0f);
	}

	@Override
	public ResourceLocation getTextureLocation(SmallStangeCloudEntity entity) {
		return entityTexture;
	}

	@Override
	protected void scale(SmallStangeCloudEntity entity, PoseStack poseStack, float partialTickTime) {
		float s = SmallStangeCloudEntity.RENDER_SCALE;
		poseStack.scale(s, s, s);
	}

	// 与大伪云一样：无碰撞会穿过地形/树冠，方块光照拉满，避免在方块内渲染变黑
	@Override
	protected int getBlockLightLevel(SmallStangeCloudEntity entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}
}
