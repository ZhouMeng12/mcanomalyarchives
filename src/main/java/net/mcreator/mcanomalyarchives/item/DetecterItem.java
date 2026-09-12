package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.mcreator.mcanomalyarchives.client.hud.SerialGenerator;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModDataComponents;
import org.jetbrains.annotations.Nullable;

public class DetecterItem extends Item {

    public DetecterItem() {
        super(new Item.Properties());
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, LivingEntity entity) {
        return armorType == EquipmentSlot.HEAD;
    }

    @Override
    @Nullable
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * getEnergy(stack) / (float) McanomalyarchivesModDataComponents.MAX_ENERGY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = (float) getEnergy(stack) / McanomalyarchivesModDataComponents.MAX_ENERGY;
        if (ratio > 0.5f) return 0x00FF00;
        if (ratio > 0.25f) return 0xFFFF00;
        return 0xFF0000;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        if (!stack.has(McanomalyarchivesModDataComponents.SERIAL_NUMBER)) {
            stack.set(McanomalyarchivesModDataComponents.SERIAL_NUMBER, SerialGenerator.generate());
        }
        if (!stack.has(McanomalyarchivesModDataComponents.ENERGY)) {
            stack.set(McanomalyarchivesModDataComponents.ENERGY, McanomalyarchivesModDataComponents.MAX_ENERGY);
        }
    }

    public static String getSerial(ItemStack stack) {
        String serial = stack.get(McanomalyarchivesModDataComponents.SERIAL_NUMBER);
        if (serial == null) {
            serial = SerialGenerator.generate();
            stack.set(McanomalyarchivesModDataComponents.SERIAL_NUMBER, serial);
        }
        return serial;
    }

    public static int getEnergy(ItemStack stack) {
        Integer energy = stack.get(McanomalyarchivesModDataComponents.ENERGY);
        if (energy == null) {
            energy = McanomalyarchivesModDataComponents.MAX_ENERGY;
            stack.set(McanomalyarchivesModDataComponents.ENERGY, energy);
        }
        return energy;
    }

    public static boolean consumeEnergy(ItemStack stack) {
        int current = getEnergy(stack);
        if (current <= 0) return false;
        int newEnergy = current - McanomalyarchivesModDataComponents.ENERGY_CONSUME_PER_TICK;
        if (newEnergy <= 0) {
            stack.set(McanomalyarchivesModDataComponents.ENERGY, 0);
        } else {
            stack.set(McanomalyarchivesModDataComponents.ENERGY, newEnergy);
        }
        return newEnergy > 0;
    }

    public static void chargeEnergy(ItemStack stack, int amount) {
        int current = getEnergy(stack);
        int newEnergy = Math.min(current + amount, McanomalyarchivesModDataComponents.MAX_ENERGY);
        stack.set(McanomalyarchivesModDataComponents.ENERGY, newEnergy);
    }
}
