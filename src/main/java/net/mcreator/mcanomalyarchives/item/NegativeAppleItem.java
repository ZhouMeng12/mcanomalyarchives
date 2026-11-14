package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.mcreator.mcanomalyarchives.anomaly.negative.NegativeItemValues;

/** UO-007 apple: eating moves the signed quantity toward zero and reverses ordinary apple nutrition. */
public final class NegativeAppleItem extends Item implements NegativeCountItem {
	private static final FoodProperties FOOD = new FoodProperties.Builder()
			.nutrition(4).saturationModifier(0.3F).alwaysEdible().build();

	public NegativeAppleItem() {
		super(new Item.Properties().food(FOOD).stacksTo(64));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        int food=entity instanceof Player player?player.getFoodData().getFoodLevel():0;
        float saturation=entity instanceof Player player?player.getFoodData().getSaturationLevel():0;
		ItemStack result = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide && entity instanceof Player player) {
			if (player instanceof net.minecraft.server.level.ServerPlayer witness) {
				net.mcreator.mcanomalyarchives.codex.CodexObservations.record(witness, "negative_apple");
			}
            // Use the pre-meal values: vanilla's cap at 20 otherwise doubles the
            // penalty when eating at full hunger.
            int nextFood=NegativeItemValues.foodAfter(food,FOOD.nutrition());
            player.getFoodData().setFoodLevel(nextFood);
            player.getFoodData().setSaturation(NegativeItemValues.saturationAfter(saturation,FOOD.saturation(),nextFood));
		}
		return result;
	}

}
