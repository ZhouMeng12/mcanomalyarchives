package net.mcreator.mcanomalyarchives.client;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.network.GarbledGuiStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reports inventory/container screen visibility so the server never guesses from the always-open inventory menu. */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID, value = Dist.CLIENT)
public final class GarbledGuiClientTracker {
	private static boolean lastOpen;
	private static int refreshTicks;

	private GarbledGuiClientTracker() {
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			lastOpen = false;
			refreshTicks = 0;
			return;
		}

		boolean open = minecraft.screen instanceof AbstractContainerScreen<?>;
		if (open != lastOpen) {
			lastOpen = open;
			refreshTicks = 0;
			PacketDistributor.sendToServer(new GarbledGuiStatePacket(open));
		} else if (open && ++refreshTicks >= 20) {
			refreshTicks = 0;
			PacketDistributor.sendToServer(new GarbledGuiStatePacket(true));
		}
	}
}
