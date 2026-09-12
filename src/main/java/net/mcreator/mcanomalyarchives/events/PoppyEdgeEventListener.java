package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

import net.mcreator.mcanomalyarchives.client.render.PoppyEdgeRenderer;

public class PoppyEdgeEventListener {

	public static void init() {
		if (FMLEnvironment.dist.isClient()) {
			NeoForge.EVENT_BUS.register(new PoppyEdgeEventListener());
		}
	}

	@SubscribeEvent
	public void onClientTick(ClientTickEvent.Post event) {
		PoppyEdgeRenderer.clientTick();
	}

	@SubscribeEvent
	public void onRenderGuiOverlay(RenderGuiEvent.Post event) {
		PoppyEdgeRenderer.renderHUD(event.getGuiGraphics());
	}
}
