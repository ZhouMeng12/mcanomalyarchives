package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PurpleMonsterTriggerHandler {

    private static final int SANITY_THRESHOLD = 15;
    private static final float HEALTH_RATIO_THRESHOLD = 0.3f;
    private static final int STILL_TIME_REQUIRED = 1200;  // 60 秒 = 20 tick/s * 60
    private static final int COOLDOWN_TICKS = 24000;       // 20 分钟

    // 每个玩家的静止 tick 计数（不持久化）
    private static final Map<UUID, Integer> stillTimers = new ConcurrentHashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.register(new PurpleMonsterTriggerHandler());
    }

    // 此类只拥有静止计时器；演员清理由会话模块按玩家归属执行。
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        stillTimers.remove(player.getUUID());
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        stillTimers.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        stillTimers.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        stillTimers.clear();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            processPlayer(player);
        }
    }

    private void processPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (!player.isAlive() || player.isRemoved() || PurpleStoryModule.hasActive(player)) {
            stillTimers.remove(uuid);
            return;
        }

        // 1. 仅生存模式
        if (player.gameMode.isCreative() || player.isSpectator()) {
            stillTimers.remove(uuid);
            return;
        }

        // 2. 冷却检查
        long cooldownTimestamp = player.getData(McanomalyarchivesModAttachments.PURPLE_MONSTER_TRIGGER_COOLDOWN);
        long currentGameTime = player.level().getGameTime();
        if (cooldownTimestamp > 0 && (currentGameTime - cooldownTimestamp) < COOLDOWN_TICKS) {
            stillTimers.remove(uuid);
            return;
        }

        // 3. 条件检查：理智 ≤ 15 或 血量 ≤ 30%
        int sanity = player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY).getSanity();
        float hpRatio = player.getHealth() / player.getMaxHealth();
        boolean conditionMet = sanity <= SANITY_THRESHOLD || hpRatio <= HEALTH_RATIO_THRESHOLD;

        if (!conditionMet) {
            stillTimers.remove(uuid);
            return;
        }

        // 4. 静止检测
        if (isPlayerStill(player)) {
            int timer = stillTimers.getOrDefault(uuid, 0) + 1;
            stillTimers.put(uuid, timer);

            if (timer >= STILL_TIME_REQUIRED) {
                triggerPurpleMonster(player);
                stillTimers.remove(uuid);
            }
        } else {
            stillTimers.remove(uuid);
        }
    }

    private boolean isPlayerStill(ServerPlayer player) {
        // 有移动输入
        if (player.xxa != 0 || player.zza != 0) return false;
        // 刚受伤
        if (player.hurtTime > 0) return false;
        // 在骑乘
        if (player.isPassenger()) return false;
        return true;
    }

    private void triggerPurpleMonster(ServerPlayer player) {
        if (!PurpleStoryModule.startNatural(player)) return;

        player.setData(McanomalyarchivesModAttachments.PURPLE_MONSTER_TRIGGER_COOLDOWN,
            player.level().getGameTime());

        McanomalyarchivesMod.LOGGER.info("Natural trigger: Purple Monster spawned for player {} "
            + "(sanity={}, hp={})", player.getName().getString(),
            player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY).getSanity(),
            String.format("%.0f%%", player.getHealth() / player.getMaxHealth() * 100));
    }
}
