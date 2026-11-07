package net.mcreator.mcanomalyarchives.client.renderer;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer for the six authored Blockbench anomaly fauna models. */
public class AnomalyWildlifeRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
	public AnomalyWildlifeRenderer(EntityRendererProvider.Context context, String key, float shadowRadius) {
		super(context, new WildlifeModel<>(key));
		this.shadowRadius = shadowRadius;
	}

	private static final class WildlifeModel<T extends GeoAnimatable> extends GeoModel<T> {
		private final String key;

		private WildlifeModel(String key) {
			this.key = key;
		}

		@Override
		public ResourceLocation getModelResource(T animatable) {
			return ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "geo/anomaly/" + key + ".geo.json");
		}

		@Override
		public ResourceLocation getTextureResource(T animatable) {
			return ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "textures/entity/anomaly/" + key + ".png");
		}

		@Override
		public ResourceLocation getAnimationResource(T animatable) {
			return ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "animations/anomaly/" + key + ".animation.json");
		}

		@Override
		public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
			super.setCustomAnimations(animatable, instanceId, state);
			float swing = state.getLimbSwing();
			float amount = Mth.clamp(state.getLimbSwingAmount(), 0.0F, 1.0F);
			if (animatable instanceof Cow) {
				float phase = swing * 0.6662F;
				float stride = Mth.cos(phase) * 1.4F * amount;
				float opposite = Mth.cos(phase + Mth.PI) * 1.4F * amount;
				setX("leg__3__5", stride);
				setX("leg_3_5", stride);
				setX("leg__3_5", opposite);
				setX("leg_3__5", opposite);
			} else if (animatable instanceof EnderMan) {
				float phase = swing * 0.6662F;
				float stride = Mth.cos(phase) * 1.0F * amount;
				float opposite = Mth.cos(phase + Mth.PI) * 1.0F * amount;
				setX("leg_l", stride);
				setX("leg_r", opposite);
				setX("arm_l", -0.2F + opposite * 0.35F);
				setX("arm_r", -0.2F + stride * 0.35F);
			} else if (animatable instanceof Salmon salmon) {
				float swim = salmon.isInWater() ? 0.6F : 0.2F;
				setY("tail", Mth.sin(salmon.tickCount * 0.6F) * swim);
				setZ("fin_l", -0.08F + Mth.sin(salmon.tickCount * 0.35F) * 0.08F);
				setZ("fin_r", 0.08F - Mth.sin(salmon.tickCount * 0.35F) * 0.08F);
			}
		}

		private void setX(String name, float value) { getBone(name).ifPresent(bone -> bone.setRotX(value)); }
		private void setY(String name, float value) { getBone(name).ifPresent(bone -> bone.setRotY(value)); }
		private void setZ(String name, float value) { getBone(name).ifPresent(bone -> bone.setRotZ(value)); }
	}
}
