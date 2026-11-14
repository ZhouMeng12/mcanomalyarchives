package net.mcreator.mcanomalyarchives.client.clouding;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.config.ComfortConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** World-only grading: inventory/HUD remain readable, and main depth is never cleared. */
@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class CloudDimensionVisuals {
    public static final ResourceLocation DIMENSION = ResourceLocation.parse("mcanomalyarchives:cloud_dem");
    private static ClientLevel observedLevel;
    private static int age = 60, width, height;
    private static PostChain chain;
    private static RenderTarget source;
    private static boolean failed;

    public static boolean active() {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().location().equals(DIMENSION);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != observedLevel) {
            observedLevel = mc.level;
            age = active() ? 0 : 60;
            reset();
        } else if (!mc.isPaused() && age < 60) age++;
        if (!active() && chain != null) reset();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !active() || failed) return;
        var mc = Minecraft.getInstance();
        try {
            RenderTarget target = mc.getMainRenderTarget();
            if (source != target) { close(); source = target; }
            if (chain == null) chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), target,
                    ResourceLocation.parse("mcanomalyarchives:shaders/post/cloud_dimension.json"));
            if (width != target.width || height != target.height) {
                width = target.width; height = target.height; chain.resize(width, height);
            }
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float entry = Math.max(0, 1 - (age + partial) / 50f);
            chain.setUniform("Entry", entry);
            chain.setUniform("Darkness", ComfortConfig.blinkScale());
            chain.setUniform("Distortion", ComfortConfig.shakeScale());
            chain.setUniform("Phase", age + partial);
            RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
            chain.process(partial);
            RenderTarget result = chain.getTempTarget("cloud_swap");
            if (result == null) throw new IllegalStateException("Missing cloud_swap target");
            target.bindWrite(true);
            result.blitToScreen(target.width, target.height, true);
        } catch (Exception error) {
            failed = true; close();
            McanomalyarchivesMod.LOGGER.warn("Cloud dimension grading disabled until reload", error);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void close() {
        if (chain != null) { chain.close(); chain = null; }
        source = null; width = height = 0;
    }

    public static void reset() { close(); failed = false; }

    @EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
    public static final class Reload {
        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager ->
                    Minecraft.getInstance().execute(CloudDimensionVisuals::reset));
        }
    }
}
