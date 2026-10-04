package net.mcreator.mcanomalyarchives.sanity;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.mcreator.mcanomalyarchives.network.SyncPlayerSanityPacket;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DirtyPlayerSanityTracker {
    private static final ConcurrentHashMap<UUID, Integer> DIRTY_SANITY_PLAYERS = new ConcurrentHashMap<>();

    public static void markDirty(ServerPlayer player) {
        DIRTY_SANITY_PLAYERS.put(player.getUUID(), player.getData(net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments.PLAYER_SANITY).getSanity());
    }

    public static void syncDirtyPlayers() {
        // 服务器可能正在关/启（getCurrentServer 会返回 null），此时直接跳过，
        // 条目留着下次 flush 再说，别在关服路径上 NPE。
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        DIRTY_SANITY_PLAYERS.forEach((uuid, sanity) -> {
            // 按 UUID 重新查一遍：玩家可能已经掉线（查不到就丢掉这条，重连时 join 那次同步会补上）
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new SyncPlayerSanityPacket(sanity));
            }
            DIRTY_SANITY_PLAYERS.remove(uuid);
        });
    }
}
