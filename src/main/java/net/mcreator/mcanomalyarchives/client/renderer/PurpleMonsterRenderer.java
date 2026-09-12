package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;

import com.mojang.blaze3d.vertex.PoseStack;

import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 紫怪混合渲染器：
 * - 常态（世界自然生成）：HumanoidMobRenderer + PLAYER_SLIM + purpleboy.png
 * - UI 流程（表演模式）：GeoEntityRenderer + GeckoLib 动画（hand/swayhand）
 */
public class PurpleMonsterRenderer extends HumanoidMobRenderer<PurpleMonsterEntity, HumanoidModel<PurpleMonsterEntity>> {
	private static final ResourceLocation NORMAL_TEXTURE = ResourceLocation.parse("mcanomalyarchives:textures/entities/purpleboy.png");
	private final PerformGeoRenderer performRenderer;

	public PurpleMonsterRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
		this.performRenderer = new PerformGeoRenderer(context);
	}

	@Override
	public ResourceLocation getTextureLocation(PurpleMonsterEntity entity) {
		return NORMAL_TEXTURE;
	}

	@Override
	public void render(PurpleMonsterEntity entity, float entityYaw, float partialTicks,
	                   PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		if (entity.isPerformMode()) {
			performRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
		} else {
			super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
		}
	}

	// ===== 内部类 =====

	/** GeckoLib 模型：保留现有 variant 切换逻辑 */
	private static final class PurpleMonsterGeoModel extends GeoModel<PurpleMonsterEntity> {
		@Override
		public ResourceLocation getModelResource(PurpleMonsterEntity animatable) {
			if (animatable.getModelVariant() == 1)
				return ResourceLocation.parse("mcanomalyarchives:geo/purplephasefive.geo.json");
			return ResourceLocation.parse("mcanomalyarchives:geo/purplemonster.geo.json");
		}

		@Override
		public ResourceLocation getTextureResource(PurpleMonsterEntity animatable) {
			if (animatable.getTextureVariant() == 1)
				return ResourceLocation.parse("mcanomalyarchives:textures/entities/purple.png");
			return ResourceLocation.parse("mcanomalyarchives:textures/entities/purpleboy.png");
		}

		@Override
		public ResourceLocation getAnimationResource(PurpleMonsterEntity animatable) {
			return ResourceLocation.parse("mcanomalyarchives:animations/purplemonster.animation.json");
		}
	}

	/** UI 流程专用 GeckoLib 渲染器 */
	private static final class PerformGeoRenderer extends GeoEntityRenderer<PurpleMonsterEntity> {
		public PerformGeoRenderer(EntityRendererProvider.Context context) {
			super(context, new PurpleMonsterGeoModel());
			this.shadowRadius = 0.5f;
		}
	}
}
