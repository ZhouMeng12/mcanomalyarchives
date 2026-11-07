package net.mcreator.mcanomalyarchives.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;

/** Shared low-impact idle/walk animation contract for the new anomaly fauna. */
public interface AnomalyGeoAnimatable extends GeoAnimatable {
	default String anomalyKey() {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(((Entity) this).getType());
		return id.getPath();
	}

	@Override
	default void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		String key = anomalyKey();
		if (key.equals("redfin") || key.equals("bloomcow") || key.equals("pale")) {
			// Their locomotion is driven from vanilla limb swing in the GeoModel so pivots
			// follow the entity's real gait instead of an unsynchronized looping clip.
			return;
		}
		RawAnimation idle = RawAnimation.begin().thenLoop("animation." + key + ".idle");
		RawAnimation walk = RawAnimation.begin().thenLoop("animation." + key + ".walk");
		controllers.add(new AnimationController<>(this, "anomaly_motion", 3, state -> {
			state.setAnimation(state.isMoving() ? walk : idle);
			return PlayState.CONTINUE;
		}));
	}

	@Override
	default double getTick(Object entity) {
		return entity instanceof Entity e ? e.tickCount : 0.0;
	}
}
