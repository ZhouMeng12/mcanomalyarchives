package net.mcreator.mcanomalyarchives.item;

import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.mcreator.mcanomalyarchives.anomaly.negative.NegativeBowReturn;
import net.mcreator.mcanomalyarchives.anomaly.negative.NegativeItemValues;
import net.mcreator.mcanomalyarchives.codex.CodexObservations;

/** Ordinary bow handling and draw animation, with delayed inward firing instead of an outgoing arrow. */
public final class NegativeBowItem extends BowItem {
    private static final String DURABILITY = "UO007NegativeDurability";
    public static final int INITIAL_DURABILITY = -384;

    public NegativeBowItem() {
        super(new Properties().durability(-INITIAL_DURABILITY));
    }

    public static int remaining(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(DURABILITY) ? Math.min(0, tag.getInt(DURABILITY)) : INITIAL_DURABILITY;
    }

    @Override
    public int getDamage(ItemStack stack) {
        return NegativeItemValues.vanillaDamage(stack.getMaxDamage(), remaining(stack));
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return stack -> stack.is(Items.ARROW);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bow = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(bow);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(bow);
    }

    @Override
    public void releaseUsing(ItemStack bow, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || player.getCooldowns().isOnCooldown(this)) return;
        int drawTicks = getUseDuration(bow, entity) - timeLeft;
        drawTicks = net.neoforged.neoforge.event.EventHooks.onArrowLoose(bow, level, player, drawTicks, true);
        if (drawTicks < 0 || getPowerForTime(drawTicks) < .1f) return;

        // Schedule first: a stopped server must never consume an arrow without a return event.
        if (!NegativeBowReturn.queue(player)) return;
        if (!player.hasInfiniteMaterials()) {
            ItemStack returned = new ItemStack(Items.ARROW);
            if (!player.getInventory().add(returned)) player.drop(returned, false);
            int next = NegativeItemValues.afterUse(remaining(bow));
            if (next == 0) {
                bow.shrink(1);
                net.mcreator.mcanomalyarchives.codex.ArchiveAdvancements.award(player, "negative_zero");
                player.onEquippedItemBroken(this, LivingEntity.getSlotForHand(player.getUsedItemHand()));
            } else {
                CompoundTag tag = bow.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putInt(DURABILITY, next);
                bow.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        }
        player.getCooldowns().addCooldown(this, 20);
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getInventory().setChanged();
        CodexObservations.record(player, "negative_bow_release");
    }
}
