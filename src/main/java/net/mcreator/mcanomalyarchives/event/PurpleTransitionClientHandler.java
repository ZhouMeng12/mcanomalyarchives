package net.mcreator.mcanomalyarchives.event;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(value = Dist.CLIENT)
public class PurpleTransitionClientHandler {
	private static final Set<UUID> LOCKED_PLAYERS = ConcurrentHashMap.newKeySet();

	public static void lockPlayer(UUID playerUUID) {
		LOCKED_PLAYERS.add(playerUUID);
	}

	public static void unlockPlayer(UUID playerUUID) {
		LOCKED_PLAYERS.remove(playerUUID);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;

		if (LOCKED_PLAYERS.contains(player.getUUID())) {
			// 锁定玩家输入
			mc.options.keyUp.setDown(false);
			mc.options.keyDown.setDown(false);
			mc.options.keyLeft.setDown(false);
			mc.options.keyRight.setDown(false);
			player.xxa = 0;
			player.zza = 0;
			player.setSprinting(false);
		}
	}
}
