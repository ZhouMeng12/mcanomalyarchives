package net.mcreator.mcanomalyarchives.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.Random;
import net.mcreator.mcanomalyarchives.events.AddictionClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Persistent roots grow stems, leaves and crowns from the four screen edges. */
public final class PoppyEdgeRenderer {
    private static final ResourceLocation POPPY = ResourceLocation.withDefaultNamespace("textures/block/poppy.png");
    private static final int MAX_FLOWERS=128;
    private static final float[] ALONG = new float[MAX_FLOWERS], BEND = new float[MAX_FLOWERS], SIZE = new float[MAX_FLOWERS];
    private static long lastSeed = Long.MIN_VALUE;
    private PoppyEdgeRenderer() {}
    public static void clientTick() {} // State is advanced once by AddictionClientHandler.
    public static int getAddictionLevel() { return AddictionClientHandler.level(); }
    public static void clear() { lastSeed = Long.MIN_VALUE; }
    public static void renderHUD(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        float fade = AddictionClientHandler.fade();
        if (!AddictionClientHandler.shouldRender() || AddictionClientHandler.cloud()) return;
        long seed = AddictionClientHandler.seed();
        if (seed != lastSeed) {
            Random random = new Random(seed);
            for (int i = 0; i < MAX_FLOWERS; i++) { ALONG[i] = .015f + random.nextFloat() * .97f;
                BEND[i] = random.nextFloat() * 2 - 1; SIZE[i] = .75f + random.nextFloat() * .5f; }
            lastSeed = seed;
        }
        float intensity = AddictionClientHandler.intensity(mc.getTimer().getGameTimeDeltaPartialTick(false));
        int width = g.guiWidth(), height = g.guiHeight();
        float shortSide = Math.min(width, height);
        float execution = AddictionClientHandler.execution();
        g.enableScissor(0, 0, width, height);
        RenderSystem.enableBlend();
        try {
            for (int i = 0; i < MAX_FLOWERS; i++) {
                float threshold = .35f + i * .065f;
                float growth = Mth.clamp((intensity - threshold) / .7f, 0, 1);
                if (growth <= 0) continue;
                int edge = i % 4;
                float x = edge == 0 ? -3 : edge == 1 ? width + 3 : width * ALONG[i];
                float y = edge == 2 ? -3 : edge == 3 ? height + 3 : height * ALONG[i];
                float dx = width * .5f - x, dy = height * .5f - y;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                dx /= length; dy /= length;
                float reach = shortSide * (.12f + .045f * intensity + execution * .20f) * growth * SIZE[i];
                reach = Math.min(reach, length * (.88f + execution * .12f));
                float lastX = x, lastY = y;
                int color = ((int)(fade * (.6f + intensity * .025f) * 255) << 24) | 0x355b35;
                for (int seg = 1; seg <= 14; seg++) {
                    float t = seg / 14f;
                    float curve = (float) Math.sin(t * Math.PI) * BEND[i] * reach * .18f;
                    float nx = x + dx * reach * t - dy * curve;
                    float ny = y + dy * reach * t + dx * curve;
                    stem(g, lastX, lastY, nx, ny, Math.max(1, shortSide * .004f), color);
                    if ((seg == 6 || seg == 10) && growth > .4f) {
                        g.pose().pushPose();
                        g.pose().translate(nx, ny, 0);
                        g.pose().mulPose(Axis.ZP.rotation((float)Math.atan2(dy, dx) + (seg == 6 ? .6f : -.9f)));
                        float leaf = shortSide * .035f * growth;
                        g.fill(0, -2, (int)leaf, 2, color);
                        g.fill(2, -4, (int)(leaf * .7f), 4, color);
                        g.pose().popPose();
                    }
                    lastX = nx; lastY = ny;
                }
                float bloom = Mth.clamp((growth - .55f) / .45f, 0, 1);
                if (bloom > 0) {
                    float crown = shortSide * (.060f + intensity * .013f) * SIZE[i] * bloom;
                    g.pose().pushPose();
                    g.pose().translate(lastX, lastY, 0);
                    g.pose().mulPose(Axis.ZP.rotation(BEND[i] * .45f));
                    g.pose().scale(crown / 8, crown / 8, 1);
                    g.setColor(1, 1, 1, fade * Math.min(.97f,.55f + intensity * .05f));
                    // Include the whole red crown, rather than cropping it in half.
                    g.blit(POPPY, -4, -3, 4, 4, 8, 7, 16, 16);
                    g.setColor(1, 1, 1, 1);
                    g.pose().popPose();
                }
            }
        } finally { g.setColor(1, 1, 1, 1); g.disableScissor(); RenderSystem.disableBlend(); }
    }
    private static void stem(GuiGraphics g, float x, float y, float nx, float ny, float thickness, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotation((float)Math.atan2(ny - y, nx - x)));
        int length = (int)Math.ceil(Math.hypot(nx - x, ny - y));
        g.fill(0, -(int)thickness / 2, length + 1, Math.max(1, (int)thickness / 2), color);
        g.pose().popPose();
    }
}
