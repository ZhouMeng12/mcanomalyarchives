package net.mcreator.mcanomalyarchives.entity;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.mcreator.mcanomalyarchives.entity.AnomalyGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.animal.Salmon;

/** Pale-eyed, red-finned salmon variant; inherits vanilla schooling and aquatic navigation. */
public final class RedfinEntity extends Salmon implements AnomalyGeoAnimatable {
	public RedfinEntity(EntityType<? extends Salmon> type, Level level) { super(type, level); }

	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
	@Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animatableInstanceCache; }
	public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 3.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 16.0); }

	/** Required by MCreator's registry; these creatures have no natural spawn rule. */
	public static void init(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) { }
}
