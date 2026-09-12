package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;

import com.mojang.blaze3d.vertex.PoseStack;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 安布拉混合渲染器：
 * - 常态：HumanoidMobRenderer + PLAYER_SLIM（细臂原版模型）
 * - 晕厥：GeoEntityRenderer + facedown 动画（GeckoLib 模型）
 */
public class AnbulaRenderer extends HumanoidMobRenderer<AnbulaEntity, HumanoidModel<AnbulaEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/111.png");
	private final FaintGeoRenderer faintRenderer;

	public AnbulaRenderer(EntityRendererProvider.Context context) {
		super(context, new AnimatedModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
		this.faintRenderer = new FaintGeoRenderer(context);
	}

	@Override
	public ResourceLocation getTextureLocation(AnbulaEntity entity) {
		if (this.model instanceof AnimatedModel animatedModel) {
			animatedModel.setEntity(entity);
		}
		return entityTexture;
	}

	@Override
	public void render(AnbulaEntity entity, float entityYaw, float partialTicks,
	                   PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		if (entity.isFaint()) {
			faintRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
		} else {
			super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
		}
	}

	// ===== 内部类 =====

	/** 常态 HumanoidModel（保留现有细臂模型逻辑） */
	private static final class AnimatedModel extends HumanoidModel<AnbulaEntity> {
		private final ModelPart rootPart;
		private AnbulaEntity entity;

		public AnimatedModel(ModelPart root) {
			super(root);
			this.rootPart = root;
		}

		public void setEntity(AnbulaEntity entity) {
			this.entity = entity;
		}

		@Override
		public void setupAnim(AnbulaEntity entity, float limbSwing, float limbSwingAmount,
		                      float ageInTicks, float netHeadYaw, float headPitch) {
			this.rootPart.getAllParts().forEach(ModelPart::resetPose);
			super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
			// 晕厥时由 GeckoLib 接管渲染，此处不再需要前倾旋转
		}
	}

	/** GeckoLib 模型：指向已有的 geo + animation 资源 */
	private static final class AnbulaGeoModel extends GeoModel<AnbulaEntity> {
		@Override
		public ResourceLocation getModelResource(AnbulaEntity animatable) {
			return ResourceLocation.parse("mcanomalyarchives:geo/anbula.geo.json");
		}

		@Override
		public ResourceLocation getTextureResource(AnbulaEntity animatable) {
			return ResourceLocation.parse("mcanomalyarchives:textures/entities/111.png");
		}

		@Override
		public ResourceLocation getAnimationResource(AnbulaEntity animatable) {
			return ResourceLocation.parse("mcanomalyarchives:animations/anbula.animation.json");
		}
	}

	/** 晕厥状态专用 GeckoLib 渲染器 */
	private static final class FaintGeoRenderer extends GeoEntityRenderer<AnbulaEntity> {
		public FaintGeoRenderer(EntityRendererProvider.Context context) {
			super(context, new AnbulaGeoModel());
			this.shadowRadius = 0.5f;
		}
	}
}
