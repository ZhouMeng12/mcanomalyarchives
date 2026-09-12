package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.animation.AnimationDefinition;

import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.mcreator.mcanomalyarchives.client.model.animations.purpledogAnimation;
import net.mcreator.mcanomalyarchives.custommodel.ModelpurpledogGeo;

public class PurpleDogRenderer extends MobRenderer<PurpleDogEntity, ModelpurpledogGeo<PurpleDogEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/pasted.png");

	public PurpleDogRenderer(EntityRendererProvider.Context context) {
		super(context, new AnimatedModel(context.bakeLayer(ModelpurpledogGeo.LAYER_LOCATION)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(PurpleDogEntity entity) {
		return entityTexture;
	}

	private static final class AnimatedModel extends ModelpurpledogGeo<PurpleDogEntity> {
		private final AnimationDefinition keyframeAnimation0;

		public AnimatedModel(ModelPart root) {
			super(root);
			this.keyframeAnimation0 = purpledogAnimation.shout;
		}

		@Override
		public void setupAnim(PurpleDogEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
			this.root().getAllParts().forEach(ModelPart::resetPose);
			// 直接使用参数 entity：渲染管线先调用 setupAnim 再调用 getTextureLocation，
			// 原实现通过 getTextureLocation 回填 this.entity，导致读到上一帧/上一只实体的引用
			this.animate(entity.animationState0, this.keyframeAnimation0, ageInTicks, 1.0F);
			super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		}
	}
}
