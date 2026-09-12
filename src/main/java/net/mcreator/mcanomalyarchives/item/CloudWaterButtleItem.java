package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;

import net.mcreator.mcanomalyarchives.procedures.CloudWaterButtleWanJiaWanChengShiYongWuPinShiProcedure;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;

public class CloudWaterButtleItem extends Item {
	public CloudWaterButtleItem() {
		super(new Item.Properties().food((new FoodProperties.Builder()).nutrition(4).saturationModifier(0.3f).alwaysEdible().build()));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack itemstack, Level world, LivingEntity entity) {
		ItemStack retval = super.finishUsingItem(itemstack, world, entity);
		CloudWaterButtleWanJiaWanChengShiYongWuPinShiProcedure.execute(entity);
		if (entity instanceof Player player && !player.getAbilities().instabuild) {
			player.addItem(new ItemStack(Items.GLASS_BOTTLE));
		}
		return retval;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		// 不对敌对生物施加
		if (target instanceof Mob mob && mob.isAggressive()) {
			return InteractionResult.PASS;
		}
		if (!player.level().isClientSide()) {
			target.addEffect(new MobEffectInstance(
				McanomalyarchivesModMobEffects.CLOUDING, 3600, 1)); // 3 分钟 (二级：不可落地)
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
				player.addItem(new ItemStack(Items.GLASS_BOTTLE));
			}
		}
		return InteractionResult.SUCCESS;
	}
}