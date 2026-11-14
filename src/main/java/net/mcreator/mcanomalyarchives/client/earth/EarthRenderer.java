package net.mcreator.mcanomalyarchives.client.earth;

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
public final class EarthRenderer {
    private static PostChain chain;
    private static Object resources;
    private static RenderTarget target;
    private static int width, height;
    private static boolean failed;
    private EarthRenderer() { }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (resources != mc.getResourceManager()) { close(); failed = false; resources = mc.getResourceManager(); }
        if (!EarthClient.active() || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) { close(); return; }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float age = EarthClient.age(partial);
        fireball(event, age);
        pulse(event, age);
        shockRing(event, age);
        ejecta(event, age);
        if (failed) return;
        try {
            RenderTarget main = mc.getMainRenderTarget();
            if (target != main) { close(); target = main; }
            if (chain == null) chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main,
                    ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "shaders/post/earth.json"));
            if (width != main.width || height != main.height) { width = main.width; height = main.height; chain.resize(width, height); }
            Vec3 offset = Vec3.atCenterOf(EarthClient.center()).subtract(event.getCamera().getPosition());
            Vector4f projected = new Vector4f((float)offset.x, (float)offset.y, (float)offset.z, 1);
            projected.mul(event.getModelViewMatrix()).mul(event.getProjectionMatrix());
            chain.setUniform("CenterX", projected.w > .01 ? Math.max(-1, Math.min(2, projected.x / projected.w * .5f + .5f)) : .5f);
            chain.setUniform("CenterY", projected.w > .01 ? Math.max(-1, Math.min(2, projected.y / projected.w * .5f + .5f)) : .5f);
            chain.setUniform("Age", age);
            chain.setUniform("Dust", EarthClient.dust(partial));
            chain.setUniform("Strength", Math.min(1, age / 300f) * Math.max(0, Math.min(1, (800 - age) / 160f)));
            chain.setUniform("FieldRadius", net.mcreator.mcanomalyarchives.anomaly.earth.EarthRules.visualRadius(age));
            chain.setUniform("OffsetX", (float)offset.x); chain.setUniform("OffsetY", (float)offset.y); chain.setUniform("OffsetZ", (float)offset.z);
            Matrix4f inverse = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
            float[] values = new float[16]; inverse.get(values);
            for (int i = 0; i < 16; i++) chain.setUniform("Inverse" + i, values[i]);
            RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
            chain.process(partial);
            RenderTarget result = chain.getTempTarget("earth_swap");
            if (result == null) throw new IllegalStateException("Missing earth_swap target");
            main.bindWrite(true);
            result.blitToScreen(main.width, main.height, true);
        } catch (Exception error) {
            failed = true; close();
            McanomalyarchivesMod.LOGGER.warn("Earth post effect disabled; world pulse remains available", error);
        } finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.depthMask(true); RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL); RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }

    private static void fireball(RenderLevelStageEvent event, float age) {
        float t = age - 600;
        if (t < 12 || t >= 160) return;
        float radius = 20 + 120 * (1 - (float)Math.exp(-t / 8));
        int alpha = (int)(95 * Math.min(1, (t - 12) / 12) * Math.pow(Math.max(0, 1 - t / 160), 1.5));
        Vec3 relative = Vec3.atCenterOf(EarthClient.center()).add(0, -12 + Math.min(30, t * .25), 0).subtract(event.getCamera().getPosition());
        Matrix4f matrix = new Matrix4f(event.getModelViewMatrix()).translate((float)relative.x, (float)relative.y, (float)relative.z);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int lat = 0; lat < 16; lat++) for (int lon = 0; lon < 40; lon++) {
                double a = Math.PI * lat / 16, b = Math.PI * (lat + 1) / 16;
                double c = Math.PI * 2 * lon / 40, d = Math.PI * 2 * (lon + 1) / 40;
                fireVertex(builder,matrix,radius,a,c,alpha,t); fireVertex(builder,matrix,radius,b,c,alpha,t); fireVertex(builder,matrix,radius,b,d,alpha,t);
                fireVertex(builder,matrix,radius,a,c,alpha,t); fireVertex(builder,matrix,radius,b,d,alpha,t); fireVertex(builder,matrix,radius,a,d,alpha,t);
            }
            BufferUploader.drawWithShader(builder.buildOrThrow());
        } finally { RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend(); }
    }
    private static void fireVertex(BufferBuilder builder, Matrix4f matrix, float radius, double lat, double lon, int alpha, float age) {
        float rough = (float)(1 + .12 * Math.sin(lon * 7 + age * .13) * Math.sin(lat * 9 - age * .08));
        float cooling = Math.min(1, age / 100);
        builder.addVertex(matrix, (float)(radius * rough * Math.sin(lat) * Math.cos(lon)), (float)(radius * rough * Math.cos(lat)),
            (float)(radius * rough * Math.sin(lat) * Math.sin(lon)))
            .setColor((int)(255 - 110 * cooling), (int)(245 - 155 * cooling), (int)(170 - 125 * cooling), alpha);
    }
    private static void ejecta(RenderLevelStageEvent event, float age) {
        float ticks = age - 600;
        if (ticks < 0 || ticks >= net.mcreator.mcanomalyarchives.anomaly.earth.EarthEjecta.LIFETIME) return;
        Minecraft mc = Minecraft.getInstance();
        var buffers = mc.renderBuffers().bufferSource();
        Vec3 origin = Vec3.atCenterOf(EarthClient.center()).subtract(event.getCamera().getPosition());
        var pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        var stones = new net.minecraft.world.level.block.state.BlockState[]{
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.GRANITE.defaultBlockState()};
        for (int i = 0; i < net.mcreator.mcanomalyarchives.anomaly.earth.EarthEjecta.COUNT; i++) {
            var piece = net.mcreator.mcanomalyarchives.anomaly.earth.EarthEjecta.at(i, ticks);
            pose.pushPose();
            pose.translate(origin.x + piece.x(), origin.y + piece.y(), origin.z + piece.z());
            pose.mulPose(new org.joml.Quaternionf().rotationXYZ(piece.rotation(), piece.rotation() * .7f, piece.rotation() * .4f));
            pose.scale(piece.size(), piece.size(), piece.size()); pose.translate(-.5, -.5, -.5);
            mc.getBlockRenderer().renderSingleBlock(stones[i % stones.length], pose, buffers,
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch();
    }
    private static void pulse(RenderLevelStageEvent event, float age) {
        if (age > 680) return;
        boolean charging = age < 600;
        float radius = charging ? net.mcreator.mcanomalyarchives.anomaly.earth.EarthRules.visualRadius(age) : 320 * (float)Math.sqrt((age - 600) / 80f);
        if (radius < .1) return;
        int shellAlpha = charging ? (int)(30 + 40 * Math.min(1, age / 600f)) : (int)(100 * Math.max(0, 1 - (age - 600) / 80));
        var pose = event.getPoseStack();
        Vec3 relative = Vec3.atCenterOf(EarthClient.center()).subtract(event.getCamera().getPosition());
        pose.pushPose(); pose.translate(relative.x, relative.y, relative.z);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = new Matrix4f(event.getModelViewMatrix()).translate((float)relative.x, (float)relative.y, (float)relative.z);
            for (int lat = 0; lat < 18; lat++) for (int lon = 0; lon < 48; lon++) {
                double a = Math.PI * lat / 18, b = Math.PI * (lat + 1) / 18;
                double c = Math.PI * 2 * lon / 48, d = Math.PI * 2 * (lon + 1) / 48;
                vertex(builder, matrix, radius, a, c, shellAlpha); vertex(builder, matrix, radius, b, c, shellAlpha); vertex(builder, matrix, radius, b, d, shellAlpha);
                vertex(builder, matrix, radius, a, c, shellAlpha); vertex(builder, matrix, radius, b, d, shellAlpha); vertex(builder, matrix, radius, a, d, shellAlpha);
            }
            BufferUploader.drawWithShader(builder.buildOrThrow());
            // Outward blast rings are visual only; gameplay radius is unchanged.
            float fade = charging ? .35f + .45f * age / 600f : Math.max(0, 1 - (age - 600) / 80);
            BufferBuilder boundary = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 128; i++) {
                for (int end = 0; end < 2; end++) {
                    double angle = Math.PI * 2 * (i + end) / 128;
                    float a = radius * (float)Math.cos(angle), b = radius * (float)Math.sin(angle);
                    boundary.addVertex(matrix, plane == 2 ? 0 : a, plane == 0 ? 0 : b, plane == 0 ? b : plane == 2 ? a : 0)
                            .setColor(255, 215, 95, (int)(150 * Math.min(1, fade)));
                }
            }
            BufferUploader.drawWithShader(boundary.buildOrThrow());
        } finally {
            pose.popPose(); RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }
    /** A thick, ground-level pressure front, visible from outside the blast. */
    private static void shockRing(RenderLevelStageEvent event, float age) {
        float t = age - 600;
        if (t < 0 || t >= 100) return;
        float radius = 4 + 316 * (float)Math.sqrt(t / 100f);
        float thickness = 4 + 10 * (1 - t / 100f);
        Vec3 relative = Vec3.atCenterOf(EarthClient.center()).subtract(event.getCamera().getPosition());
        Matrix4f matrix = new Matrix4f(event.getModelViewMatrix()).translate((float)relative.x, (float)relative.y, (float)relative.z);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int alpha = (int)(220 * Math.pow(1 - t / 100, 1.4));
            for (int layer = 0; layer < 3; layer++) for (int i = 0; i < 128; i++) {
                double a = i * Math.PI * 2 / 128, c = (i + 1) * Math.PI * 2 / 128;
                float outer = radius - layer * thickness * 1.8f, inner = Math.max(0, outer - thickness);
                if (outer <= 0) continue;
                float y = 1 + layer * 3;
                b.addVertex(matrix,(float)Math.cos(a)*inner,y,(float)Math.sin(a)*inner).setColor(255,235,180,0);
                b.addVertex(matrix,(float)Math.cos(a)*outer,y,(float)Math.sin(a)*outer).setColor(255,245,215,alpha/(layer+1));
                b.addVertex(matrix,(float)Math.cos(c)*outer,y,(float)Math.sin(c)*outer).setColor(255,245,215,alpha/(layer+1));
                b.addVertex(matrix,(float)Math.cos(c)*inner,y,(float)Math.sin(c)*inner).setColor(255,235,180,0);
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        } finally { RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.disableBlend(); }
    }
    private static void vertex(BufferBuilder builder, Matrix4f matrix, float radius, double lat, double lon, int alpha) {
        builder.addVertex(matrix, (float)(radius * Math.sin(lat) * Math.cos(lon)), (float)(radius * Math.cos(lat)),
                (float)(radius * Math.sin(lat) * Math.sin(lon))).setColor(255, 218, 102, alpha);
    }
    private static void close() {
        if (chain != null) { chain.close(); chain = null; }
        target = null; width = height = 0;
    }
}
