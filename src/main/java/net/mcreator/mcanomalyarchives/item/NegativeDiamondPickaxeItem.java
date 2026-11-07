package net.mcreator.mcanomalyarchives.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.component.CustomData;

/** Diamond pickaxe whose durability begins negative and counts upward to zero. */
public final class NegativeDiamondPickaxeItem extends PickaxeItem {
	private static final String REMAINING_KEY = "UO007NegativeDurability";
	private static final int INITIAL_DURABILITY = -Tiers.DIAMOND.getUses();

	public NegativeDiamondPickaxeItem() {
		super(Tiers.DIAMOND, new Properties().durability(Tiers.DIAMOND.getUses()));
	}

	private static int remaining(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(REMAINING_KEY);
	}

	private static void setRemaining(ItemStack stack, int value) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putInt(REMAINING_KEY, value);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static void useNegativeDurability(ItemStack stack) {
		int current = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(REMAINING_KEY)
				? remaining(stack) : INITIAL_DURABILITY;
		int next = Math.min(0, current + 1);
		if (next == 0) {
			stack.shrink(1);
		} else {
			setRemaining(stack, next);
		}
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
		if (!level.isClientSide && entity instanceof Player) useNegativeDurability(stack);
		return false; // Prevent vanilla positive durability damage.
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!attacker.level().isClientSide && attacker instanceof Player) useNegativeDurability(stack);
		return false; // Prevent vanilla positive durability damage.
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		int value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(REMAINING_KEY)
				? remaining(stack) : INITIAL_DURABILITY;
		return Math.max(1, Math.min(13, (int) Math.ceil(13.0 * -value / -INITIAL_DURABILITY)));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0x8B1E1E;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		int value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(REMAINING_KEY)
				? remaining(stack) : INITIAL_DURABILITY;
		tooltip.add(Component.literal("负耐久：" + value).withStyle(ChatFormatting.DARK_RED));
	}
}
