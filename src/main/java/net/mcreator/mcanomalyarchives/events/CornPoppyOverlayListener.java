package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class CornPoppyOverlayListener {
    private static int logTimer = 0;
    
    public static void init() {
        // 暂时停用，避免重复渲染，使用 PoppyEdgeRenderer 替代
        // if (FMLEnvironment.dist.isClient()) {
        //     McanomalyarchivesMod.LOGGER.info("=== CornPoppyOverlayListener INITIALIZED ===");
        //     NeoForge.EVENT_BUS.register(new CornPoppyOverlayListener());
        // }
    }

    @SubscribeEvent
    public void onRenderGuiPost(RenderGuiEvent.Post event) {
        logTimer++;
        if (logTimer % 200 == 0) {
            McanomalyarchivesMod.LOGGER.info("CornPoppyOverlayListener onRenderGuiPost called");
        }
        CornPoppyClientListener.renderOverlay(event.getGuiGraphics());
    }
}