package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

/** UO-007 apple: eating lowers hunger and returns two apples for one consumed. */
public final class NegativeAppleItem extends Item {
	private static final FoodProperties FOOD = new FoodProperties.Builder()
			.nutrition(4).saturationModifier(0.3F).alwaysEdible().build();

	public NegativeAppleItem() {
		super(new Item.Properties().food(FOOD).stacksTo(64));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack result = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide && entity instanceof Player player) {
			if (player instanceof net.minecraft.server.level.ServerPlayer witness) {
				net.mcreator.mcanomalyarchives.codex.CodexObservations.record(witness, "negative_apple");
			}
			// Vanilla food restores four hunger. Subtract eight afterward so the net
			// result is the exact inverse of an ordinary apple: -4 hunger.
			player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 8));
			player.getFoodData().setSaturation(0.0F);
		}
		return result;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal("数量：-" + stack.getCount()).withStyle(ChatFormatting.DARK_RED));
	}
}
