package net.mcreator.mcanomalyarchives.entity;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.mcreator.mcanomalyarchives.entity.AnomalyGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.PathfinderMob;

/** Non-hostile drifting shell with only slow wandering; it has no attack or extra drops. */
public final class FoldEntity extends PathfinderMob implements AnomalyGeoAnimatable {
	public FoldEntity(EntityType<? extends FoldEntity> type, Level level) { super(type, level); }

	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
	@Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animatableInstanceCache; }
	@Override protected void registerGoals() { }
	public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.1).add(Attributes.FOLLOW_RANGE, 12.0); }

	/** Required by MCreator's registry; these creatures have no natural spawn rule. */
	public static void init(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) { }
}
