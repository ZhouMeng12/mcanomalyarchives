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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;

/** Quiet, slow anomaly that wanders and watches nearby players but never attacks. */
public final class RemnantEntity extends PathfinderMob implements AnomalyGeoAnimatable {
	public RemnantEntity(EntityType<? extends RemnantEntity> type, Level level) { super(type, level); }

	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
	@Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animatableInstanceCache; }
	@Override protected void registerGoals() { }
	@Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
		if (!level().isClientSide) {
			ItemStack remnant = new ItemStack(Items.BONE);
			remnant.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("???"));
			if (!player.getInventory().add(remnant)) player.drop(remnant, false);
			this.discard();
		}
		return InteractionResult.sidedSuccess(level().isClientSide);
	}
	public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.FOLLOW_RANGE, 16.0); }

	/** Required by MCreator's registry; these creatures have no natural spawn rule. */
	public static void init(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) { }
}
