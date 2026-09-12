package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class CornPoppyRenderListener {
    private static boolean wasInitialized = false;

    public static void init() {
        if (FMLEnvironment.dist.isClient()) {
            McanomalyarchivesMod.LOGGER.info("=== CornPoppyRenderListener INITIALIZED ===");
            wasInitialized = true;
            NeoForge.EVENT_BUS.register(new CornPoppyRenderListener());
        }
    }

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
    }
}
