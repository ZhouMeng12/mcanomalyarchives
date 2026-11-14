package net.mcreator.mcanomalyarchives.item;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.mcreator.mcanomalyarchives.anomaly.negative.NegativeItemValues;
import net.mcreator.mcanomalyarchives.anomaly.rewind.DiscUsageData;

public final class NegativeMusicDiscItem extends Item {
    private static final String ID = "UO007DiscIdentity";
    private static final String USES = "UO007DiscUsed";

    public NegativeMusicDiscItem() {
        super(new Properties().durability(DiscUsageData.MAX_USES).setNoRepair().jukeboxPlayable(
                ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "negative_cat"))));
    }
    public static UUID identity(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(ID)) {
            tag.putUUID(ID, UUID.randomUUID());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return tag.getUUID(ID);
    }
    public static void synchronize(ItemStack stack, ServerLevel level) {
        if (!(stack.getItem() instanceof NegativeMusicDiscItem)) return;
        int used = DiscUsageData.get(level).used(identity(stack));
        if (used >= DiscUsageData.MAX_USES) { stack.setCount(0); return; }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getInt(USES) != used) {
            tag.putInt(USES, used);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server) synchronize(stack, server);
    }
    @Override public int getDamage(ItemStack stack) {
        int used = usedCharges(stack);
        return NegativeItemValues.vanillaDamage(DiscUsageData.MAX_USES, Math.min(0, used - DiscUsageData.MAX_USES));
    }
    public static int usedCharges(ItemStack stack) {
        return Math.max(0, Math.min(DiscUsageData.MAX_USES,
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(USES)));
    }
}
