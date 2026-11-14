package net.mcreator.mcanomalyarchives.client.rewind;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** A world-space pulse and a colour-only post pass; never copy or replace the main depth buffer. */
@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class RewindRenderer {
    private static PostChain chain;
    private static Object resources;
    private static RenderTarget target;
    private static int width, height;
    private static boolean failed;
    private RewindRenderer() { }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (resources != mc.getResourceManager()) { close(); failed = false; resources = mc.getResourceManager(); }
        if (!RewindClient.active()) { close(); return; }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float age = RewindClient.age(partial);
        pulse(event, age);
        if (failed || RewindClient.strength() <= 0) return;
        try {
            RenderTarget main = mc.getMainRenderTarget();
            if (target != main) { close(); target = main; }
            if (chain == null) chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main,
                    ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "shaders/post/rewind.json"));
            if (width != main.width || height != main.height) { width = main.width; height = main.height; chain.resize(width, height); }
            Vec3 offset = Vec3.atCenterOf(RewindClient.center()).subtract(event.getCamera().getPosition());
            Vector4f projected = new Vector4f((float)offset.x, (float)offset.y, (float)offset.z, 1);
            projected.mul(event.getPoseStack().last().pose()).mul(event.getProjectionMatrix());
            chain.setUniform("CenterX", projected.w > .01 ? Math.max(-1, Math.min(2, projected.x / projected.w * .5f + .5f)) : .5f);
            chain.setUniform("CenterY", projected.w > .01 ? Math.max(-1, Math.min(2, projected.y / projected.w * .5f + .5f)) : .5f);
            chain.setUniform("Age", age);
            chain.setUniform("Strength", RewindClient.strength());
            RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
            chain.process(partial);
            RenderTarget result = chain.getTempTarget("rewind_swap");
            if (result == null) throw new IllegalStateException("Missing rewind_swap target");
            main.bindWrite(true);
            result.blitToScreen(main.width, main.height, true);
        } catch (Exception error) {
            failed = true; close();
            McanomalyarchivesMod.LOGGER.warn("Negative disc post effect disabled; world pulse remains available", error);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.depthMask(true); RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL); RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }

    private static void pulse(RenderLevelStageEvent event, float age) {
        float radius = age < 10 ? RewindClient.radius() * (1 - (float)Math.pow(1 - age / 10, 3))
                : RewindClient.radius() * (1 - Math.min(1, (age - 10) / 42));
        if (radius < .1) return;
        var pose = event.getPoseStack();
        Vec3 relative = Vec3.atCenterOf(RewindClient.center()).subtract(event.getCamera().getPosition());
        pose.pushPose(); pose.translate(relative.x, relative.y, relative.z);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = pose.last().pose();
            for (int lat = 0; lat < 18; lat++) for (int lon = 0; lon < 48; lon++) {
                double a = Math.PI * lat / 18, b = Math.PI * (lat + 1) / 18;
                double c = Math.PI * 2 * lon / 48, d = Math.PI * 2 * (lon + 1) / 48;
                vertex(builder, matrix, radius, a, c, 28); vertex(builder, matrix, radius, b, c, 28); vertex(builder, matrix, radius, b, d, 28);
                vertex(builder, matrix, radius, a, c, 28); vertex(builder, matrix, radius, b, d, 28); vertex(builder, matrix, radius, a, d, 28);
            }
            BufferUploader.drawWithShader(builder.buildOrThrow());
            // Three intersecting great circles mark the actual 50-block boundary for outside observers.
            float fade = Math.min(1, age / 6) * Math.max(0, (60 - age) / 10);
            BufferBuilder boundary = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 128; i++) {
                for (int end = 0; end < 2; end++) {
                    double angle = Math.PI * 2 * (i + end) / 128;
                    float a = RewindClient.radius() * (float)Math.cos(angle), b = RewindClient.radius() * (float)Math.sin(angle);
                    boundary.addVertex(matrix, plane == 2 ? 0 : a, plane == 0 ? 0 : b, plane == 0 ? b : plane == 2 ? a : 0)
                            .setColor(205, 171, 255, (int)(150 * Math.min(1, fade)));
                }
            }
            BufferUploader.drawWithShader(boundary.buildOrThrow());
        } finally {
            pose.popPose(); RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }
    private static void vertex(BufferBuilder builder, Matrix4f matrix, float radius, double lat, double lon, int alpha) {
        builder.addVertex(matrix, (float)(radius * Math.sin(lat) * Math.cos(lon)), (float)(radius * Math.cos(lat)),
                (float)(radius * Math.sin(lat) * Math.sin(lon))).setColor(152, 107, 240, alpha);
    }
    private static void close() {
        if (chain != null) { chain.close(); chain = null; }
        target = null; width = height = 0;
    }
}
