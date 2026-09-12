package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class CloudArmorSetBonusHandler {

    // 效果持续时间（tick），每 tick 刷新，保证脱下后 2 秒内消失
    private static final int EFFECT_DURATION = 40;

    public static void init() {
        NeoForge.EVENT_BUS.register(new CloudArmorSetBonusHandler());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            if (hasFullCloudArmor(player)) {
                applySetBonuses(player);
            }
        }
    }

    private boolean hasFullCloudArmor(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        return head.is(McanomalyarchivesModItems.CLOUD_ARMOR_HELMET.get())
            && chest.is(McanomalyarchivesModItems.CLOUD_ARMOR_CHESTPLATE.get())
            && legs.is(McanomalyarchivesModItems.CLOUD_ARMOR_LEGGINGS.get())
            && feet.is(McanomalyarchivesModItems.CLOUD_ARMOR_BOOTS.get());
    }

    private void applySetBonuses(ServerPlayer player) {
        // 云化效果（amplifier 0 表示套装版本，区别于云水瓶 amplifier 1）
        MobEffectInstance clouding = player.getEffect(McanomalyarchivesModMobEffects.CLOUDING);
        if (clouding == null || clouding.getDuration() < EFFECT_DURATION) {
            player.addEffect(new MobEffectInstance(
                McanomalyarchivesModMobEffects.CLOUDING,
                EFFECT_DURATION, 0,
                false, false, true
            ));
        }
        // 抗性提升 II (amplifier 1)
        MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance == null || resistance.getAmplifier() < 1 || resistance.getDuration() < EFFECT_DURATION) {
            player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                EFFECT_DURATION, 1,
                false, false, true
            ));
        }
    }
}
