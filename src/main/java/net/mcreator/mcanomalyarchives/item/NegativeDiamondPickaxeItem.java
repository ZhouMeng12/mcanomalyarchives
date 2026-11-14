package net.mcreator.mcanomalyarchives.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.component.CustomData;
import net.mcreator.mcanomalyarchives.anomaly.negative.NegativeItemValues;

/** Diamond pickaxe whose durability begins negative and counts upward to zero. */
public final class NegativeDiamondPickaxeItem extends PickaxeItem {
	private static final String REMAINING_KEY = "UO007NegativeDurability";
	private static final int INITIAL_DURABILITY = -Tiers.DIAMOND.getUses();

	public NegativeDiamondPickaxeItem() {
		this(Tiers.DIAMOND);
	}
    public NegativeDiamondPickaxeItem(net.minecraft.world.item.Tier tier) {
        super(tier, new Properties().durability(tier.getUses()));
    }

	public static int remaining(ItemStack stack) {
        CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.contains(REMAINING_KEY)?Math.min(0,data.getInt(REMAINING_KEY)):-stack.getMaxDamage();
	}

	private static void setRemaining(ItemStack stack, int value) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putInt(REMAINING_KEY, value);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static void useNegativeDurability(ItemStack stack, LivingEntity user) {
		int current = remaining(stack);
		int next = NegativeItemValues.afterUse(current);
		if (next == 0) {
			stack.shrink(1);
            if (user instanceof net.minecraft.server.level.ServerPlayer p) net.mcreator.mcanomalyarchives.codex.ArchiveAdvancements.award(p, "negative_zero");
		} else {
			setRemaining(stack, next);
		}
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
		if (!level.isClientSide && entity instanceof Player) useNegativeDurability(stack, entity);
		return false; // Prevent vanilla positive durability damage.
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!attacker.level().isClientSide && attacker instanceof Player) useNegativeDurability(stack, attacker);
		return false; // Prevent vanilla positive durability damage.
	}

    @Override
    public int getDamage(ItemStack stack) {
        // NeoForge normally clamps this to maxDamage. Expose the real signed
        // remainder to vanilla's durability tooltip and inverted red bar.
        return NegativeItemValues.vanillaDamage(stack.getMaxDamage(),remaining(stack));
    }
}
