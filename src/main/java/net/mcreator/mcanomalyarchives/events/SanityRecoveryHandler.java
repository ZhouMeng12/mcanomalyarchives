package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.sanity.PlayerSanity;
import net.mcreator.mcanomalyarchives.entity.SittingEnityEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SanityRecoveryHandler {

    private static final ResourceKey<DamageType> BRAIN_DAMAGE_KEY = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "braindamage")
    );

    // Per-player tick counters
    private static final Map<UUID, Integer> recoveryTickCounters = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> chairTickCounters = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> brainDamageTickCounters = new ConcurrentHashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.register(new SanityRecoveryHandler());
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        UUID playerId = player.getUUID();
        PlayerSanity sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
        int currentSanity = sanity.getSanity();

        // === HP-driven recovery/drain ===
        float hpRatio = player.getHealth() / player.getMaxHealth();
        int recoveryInterval;
        boolean shouldDrain;

        if (hpRatio >= 0.8f) {
            recoveryInterval = 40; // +1 every 2s
            shouldDrain = false;
        } else if (hpRatio >= 0.5f) {
            recoveryInterval = 80; // +1 every 4s
            shouldDrain = false;
        } else if (hpRatio >= 0.2f) {
            recoveryInterval = 160; // +1 every 8s
            shouldDrain = false;
        } else {
            recoveryInterval = 100; // -1 every 5s
            shouldDrain = true;
        }

        int recoveryCounter = recoveryTickCounters.getOrDefault(playerId, 0) + 1;
        if (recoveryCounter >= recoveryInterval) {
            recoveryCounter = 0;
            if (shouldDrain) {
                sanity.reduceSanity(1);
                PlayerSanity.syncToClient(player);
            } else {
                sanity.increaseSanity(1);
                PlayerSanity.syncToClient(player);
            }
        }
        recoveryTickCounters.put(playerId, recoveryCounter);

        // === Chair recovery (independent of HP) ===
        if (player.getVehicle() instanceof SittingEnityEntity) {
            int chairCounter = chairTickCounters.getOrDefault(playerId, 0) + 1;
            if (chairCounter >= 40) { // every 2s
                chairCounter = 0;
                sanity.increaseSanity(3);
                PlayerSanity.syncToClient(player);
            }
            chairTickCounters.put(playerId, chairCounter);
        }

        // === Low sanity brain-damage ticks ===
        if (currentSanity <= 24) {
            int bdCounter = brainDamageTickCounters.getOrDefault(playerId, 0) + 1;
            if (bdCounter >= 200) { // every 10s
                bdCounter = 0;

                // Mark this damage so SanityEventHandler skips it
                player.getPersistentData().putBoolean(SanityEventHandler.SANITY_BRAIN_DAMAGE_TAG, true);
                DamageSource brainDamage = player.level().damageSources().source(BRAIN_DAMAGE_KEY);
                player.hurt(brainDamage, 1.0f);
                // 低理智掉血不产生击退：清掉 hurt 施加的水平速度（保留垂直分量，避免影响下落）
                player.setDeltaMovement(
                        player.getDeltaMovement().x() * 0.0,
                        player.getDeltaMovement().y(),
                        player.getDeltaMovement().z() * 0.0
                );
            }
            brainDamageTickCounters.put(playerId, bdCounter);
        } else {
            brainDamageTickCounters.remove(playerId);
        }
    }
}
