package net.mcreator.mcanomalyarchives.sanity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.network.SyncPlayerSanityPacket;

/** 手写理智模块的同步接入。无需在生成主类或其他 tick handler 手动调用 flush。 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class DirtyPlayerSanityTracker {
	private static MinecraftServer activeServer;
	private static SanitySyncQueue pending;

	private DirtyPlayerSanityTracker() {
	}

	@SubscribeEvent
	public static void onStarting(ServerStartingEvent event) {
		if (pending != null) {
			pending.clear();
		}
		activeServer = event.getServer();
		pending = new SanitySyncQueue();
	}

	public static void markDirty(ServerPlayer player) {
		if (isActive(player)) {
			pending.markDirty(player.getUUID());
		}
	}

	public static void syncNow(ServerPlayer player) {
		if (isActive(player)) {
			pending.syncNow(player.getUUID(), id -> currentValue(player), (id, value) -> send(player, value));
		}
	}

	private static boolean isActive(ServerPlayer player) {
		return pending != null && player.getServer() == activeServer && activeServer.isSameThread();
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if (server == activeServer && pending != null) {
			pending.flush(server.getTickCount(),
					id -> server.getPlayerList().getPlayer(id) != null,
					id -> currentValue(server.getPlayerList().getPlayer(id)),
					(id, value) -> send(server.getPlayerList().getPlayer(id), value));
		}
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			syncNow(player);
		}
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			// Clone 先完成属性复制/死亡重置，Respawn 再向最终玩家连接同步。
			syncNow(player);
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && isActive(player)) {
			pending.forget(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onStopping(ServerStoppingEvent event) {
		stop(event.getServer());
	}

	@SubscribeEvent
	public static void onStopped(ServerStoppedEvent event) {
		stop(event.getServer());
	}

	private static void stop(MinecraftServer server) {
		if (server == activeServer) {
			if (pending != null) {
				pending.clear();
			}
			pending = null;
			activeServer = null;
		}
	}

	private static int currentValue(ServerPlayer player) {
		return player.getData(McanomalyarchivesModAttachments.PLAYER_SANITY).getSanity();
	}

	private static void send(ServerPlayer player, int value) {
		PacketDistributor.sendToPlayer(player, new SyncPlayerSanityPacket(value));
	}
}
