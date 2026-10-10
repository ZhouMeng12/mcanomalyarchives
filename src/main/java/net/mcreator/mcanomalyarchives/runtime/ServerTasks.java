package net.mcreator.mcanomalyarchives.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

/**
 * 延迟任务的 NeoForge 接入：队列随服务器创建/销毁，不依赖 MCreator 生成主类中的 tick。
 * 排队/取消仅限当前服务器线程；玩家任务退出时取消，执行时再校验原玩家实体仍在线且存活。
 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class ServerTasks {
	private static MinecraftServer activeServer;
	private static ServerTaskQueue queue;

	private ServerTasks() {
	}

	@SubscribeEvent
	public static void onStarting(ServerStartingEvent event) {
		if (queue != null) {
			queue.close();
		}
		activeServer = event.getServer();
		queue = new ServerTaskQueue(failure ->
				McanomalyarchivesMod.LOGGER.error("Delayed server task failed", failure));
	}

	public static ServerTaskQueue.TaskHandle schedule(int delay, Runnable action) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (!canSchedule(server)) {
			return ServerTaskQueue.rejected();
		}
		return queue.schedule(delay, action);
	}

	public static ServerTaskQueue.TaskHandle scheduleForPlayer(ServerPlayer player, int delay, Runnable action) {
		MinecraftServer server = player.getServer();
		if (!canSchedule(server)) {
			return ServerTaskQueue.rejected();
		}
		return queue.schedule(player.getUUID(), delay, () -> {
			if (server.getPlayerList().getPlayer(player.getUUID()) == player
					&& player.isAlive() && !player.isRemoved()) {
				action.run();
			}
		});
	}

	private static boolean canSchedule(MinecraftServer server) {
		return server != null && server == activeServer && queue != null && server.isSameThread();
	}

	@SubscribeEvent
	public static void onTick(ServerTickEvent.Post event) {
		if (event.getServer() == activeServer && queue != null) {
			queue.tick(event.getServer().getTickCount());
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && player.getServer() == activeServer && queue != null) {
			queue.cancelOwner(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		if (event.getEntity() instanceof ServerPlayer player && player.getServer() == activeServer && queue != null) {
			// 玩家实体被替换，旧回调不应留到期限结束，更不能作用在重生后的同 UUID 玩家上。
			queue.cancelOwner(player.getUUID());
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
			if (queue != null) {
				queue.close();
			}
			queue = null;
			activeServer = null;
		}
	}
}
