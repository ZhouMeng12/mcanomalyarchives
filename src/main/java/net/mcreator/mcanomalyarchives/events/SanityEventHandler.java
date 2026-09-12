package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerPlayer;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.mcreator.mcanomalyarchives.sanity.PlayerSanity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SanityEventHandler {

    private static final Map<UUID, Long> cloudDimensionCooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> sadPoppyCooldowns = new ConcurrentHashMap<>();
    private static final long DIMENSION_COOLDOWN_MS = 5 * 60 * 1000;
    private static final long SAD_POPPY_COOLDOWN_MS = 30 * 1000;

    private static final ResourceKey<DamageType> BRAIN_DAMAGE_KEY = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "braindamage")
    );

    public static final String SANITY_BRAIN_DAMAGE_TAG = "sanity_brain_damage";

    public static void init() {
        NeoForge.EVENT_BUS.register(new SanityEventHandler());
    }

    // === Cloud Dimension entry ===
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getTo().location().getPath().equals("cloud_dem")) return;

        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = cloudDimensionCooldowns.get(id);
        if (last != null && (now - last) < DIMENSION_COOLDOWN_MS) return;

        cloudDimensionCooldowns.put(id, now);
        PlayerSanity sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(35);
        PlayerSanity.syncToClient(player);
    }

    // === Sad effect applied ===
    @SubscribeEvent
    public void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getEffectInstance().getEffect().equals(McanomalyarchivesModMobEffects.SAD.get())) return;

        int level = event.getEffectInstance().getAmplifier() + 1;
        int loss = 8 * level;

        PlayerSanity sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(loss);
        PlayerSanity.syncToClient(player);
    }

    // === Damage taken ===
    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Skip damage caused by low-sanity brain-damage (avoid infinite loop)
        if (player.getPersistentData().getBoolean(SANITY_BRAIN_DAMAGE_TAG)) {
            player.getPersistentData().remove(SANITY_BRAIN_DAMAGE_TAG);
            return;
        }

        boolean isBrainDamage = event.getSource().is(BRAIN_DAMAGE_KEY);
        float damage = event.getNewDamage();

        PlayerSanity sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);

        if (isBrainDamage) {
            sanity.reduceSanity(8);
        } else {
            int loss = Math.max(1, (int) Math.floor(damage / 2.0));
            sanity.reduceSanity(loss);
        }
        PlayerSanity.syncToClient(player);
    }

    // === SadPoppy proximity (called from CornPoppyAngryListener) ===
    public static void onSadPoppyProximity(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = sadPoppyCooldowns.get(id);
        if (last != null && (now - last) < SAD_POPPY_COOLDOWN_MS) return;

        sadPoppyCooldowns.put(id, now);
        PlayerSanity sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(5);
        PlayerSanity.syncToClient(player);
    }

    // === Fishing Tier3 mob spawned (called from StrangeFishingRodLootHandler) ===
    public static void onFishingTier3(ServerPlayer player) {
        PlayerSanity sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(20);
        PlayerSanity.syncToClient(player);
    }

    // === CloudWater drunk (called from CloudWaterButtleItem / CloudWaterItem) ===
    public static void onCloudWaterDrunk(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer serverPlayer)) return;
        PlayerSanity sanity = serverPlayer.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(25);
        PlayerSanity.syncToClient(serverPlayer);
    }

    // === Death resets sanity ===
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        Player newPlayer = event.getEntity();
        if (!(newPlayer instanceof ServerPlayer)) return;
        PlayerSanity sanity = newPlayer.getData(McanomalyarchivesModAttachments.PLAYER_SANITY);
        sanity.resetToMax();
        PlayerSanity.syncToClient((ServerPlayer) newPlayer);
    }
}
