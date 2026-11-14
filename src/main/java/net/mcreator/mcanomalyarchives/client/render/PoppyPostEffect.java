package net.mcreator.mcanomalyarchives.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.events.AddictionClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

/** Own post chain: does not replace GameRenderer's spectator/resource-pack effects. */
@net.neoforged.fml.common.EventBusSubscriber(modid="mcanomalyarchives",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class PoppyPostEffect {
    private static PostChain chain;
    private static Object resources;
    private static boolean failed;
    private static int width, height;
    private static RenderTarget sourceTarget;
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelRender(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if(event.getStage()==net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL)render();
    }
    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (resources != mc.getResourceManager()) { clear(); resources = mc.getResourceManager(); failed = false; }
        if (!AddictionClientHandler.shouldRender()) { clear(); return; }
        if (failed) return;
        try {
            var target = mc.getMainRenderTarget();
            if (sourceTarget != target) { close(); sourceTarget = target; }
            if (chain == null) chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), target,
                    ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "shaders/post/poppy.json"));
            if (width != target.width || height != target.height) {
                width = target.width; height = target.height; chain.resize(width, height);
            }
            float intensity = AddictionClientHandler.intensity(mc.getTimer().getGameTimeDeltaPartialTick(false));
            chain.setUniform("Influence", Math.min(.94f, intensity * .095f) * AddictionClientHandler.fade());
            RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
            // PostPass clears its output, so it must only render to the scratch target.
            // Draw its color texture over main without clearing/copying world depth.
            chain.process(mc.getTimer().getGameTimeDeltaPartialTick(false));
            RenderTarget result = chain.getTempTarget("poppy_swap");
            if (result == null) throw new IllegalStateException("Missing poppy_swap render target");
            target.bindWrite(true);
            result.blitToScreen(target.width, target.height, true);
        } catch (Exception error) {
            failed = true; close();
            McanomalyarchivesMod.LOGGER.warn("Poppy desaturation disabled; keeping geometry and gaze effects", error);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true,true,true,true);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }
    public static void clear() {
        boolean open = chain != null;
        close();
        if (open) Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
    private static void close() {
        if (chain != null) { chain.close(); chain = null; }
        sourceTarget = null;
        width = height = 0;
    }
}
