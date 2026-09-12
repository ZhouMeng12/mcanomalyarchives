package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.network.OpenPurpleGuiPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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

    // 玩家退出时清理所有相关的紫怪和紫狗
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        // 清理静止计时器
        stillTimers.remove(uuid);

        // 查找并移除该玩家触发的所有表演模式紫怪/紫狗
        ServerLevel serverLevel = (ServerLevel) player.level();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof PurpleMonsterEntity pm && pm.isPerformMode()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
            if (entity instanceof PurpleDogEntity dog && dog.isPerformMode()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        McanomalyarchivesMod.LOGGER.info("Cleaned up purple entities after player {} logged out",
            player.getName().getString());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            processPlayer(player);
        }
    }

    private void processPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();

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
        ServerLevel serverLevel = (ServerLevel) player.level();
        double yawRad = Math.toRadians(player.getYRot());

        double frontX = -Math.sin(yawRad) * 2.0;
        double frontZ = Math.cos(yawRad) * 2.0;
        double spawnX = player.getX() + frontX;
        double spawnZ = player.getZ() + frontZ;
        double spawnY = player.getY();

        PurpleMonsterEntity purpleMonster = new PurpleMonsterEntity(
            McanomalyarchivesModEntities.PURPLE_MONSTER.get(), serverLevel);
        purpleMonster.setPos(spawnX, spawnY, spawnZ);
        purpleMonster.setXRot(0.0F);
        purpleMonster.setYRot(player.getYRot() + 180.0F);
        purpleMonster.yBodyRot = player.getYRot() + 180.0F;
        purpleMonster.yHeadRot = player.getYRot() + 180.0F;
        purpleMonster.setPerformMode(player.getUUID());
        serverLevel.addFreshEntity(purpleMonster);

        PacketDistributor.sendToPlayer(player,
            new OpenPurpleGuiPacket(purpleMonster.getId(), 1, 0));

        player.setData(McanomalyarchivesModAttachments.PURPLE_MONSTER_TRIGGER_COOLDOWN,
            player.level().getGameTime());

        McanomalyarchivesMod.LOGGER.info("Natural trigger: Purple Monster spawned for player {} "
            + "(sanity={}, hp={})", player.getName().getString(),
            player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY).getSanity(),
            String.format("%.0f%%", player.getHealth() / player.getMaxHealth() * 100));
    }
}
