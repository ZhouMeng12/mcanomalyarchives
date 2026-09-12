package net.mcreator.mcanomalyarchives.client;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import net.mcreator.mcanomalyarchives.McanomalyarchivesModPlayerAnimationAPI;
import net.mcreator.mcanomalyarchives.mixin.CameraAccessor;
import net.mcreator.mcanomalyarchives.sanity.PlayerSanity;
import net.mcreator.mcanomalyarchives.client.SanityScreenEffects;

import java.util.Random;

public class SanityClientHandler {

    private static volatile int cachedSanity = PlayerSanity.MAX_SANITY;
    private static final Random RANDOM = new Random();
    
    // Noise: random 2x2 pixel dots scattered across screen
    private static final int MAX_NOISE_DOTS = 800;
    private static final int NOISE_DOT_SIZE = 2;

    public static void init() {
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(new SanityClientHandler());
        }
    }

    public static int getCachedSanity() {
        return cachedSanity;
    }

    public static void updateCachedSanity(int sanity) {
        cachedSanity = sanity;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 相机安全网：无动画播放时若相机被错误 detach（渲染异常泄漏），强制复位
        if (!player.isSpectator() && McanomalyarchivesModPlayerAnimationAPI.animations.isEmpty()
                && mc.gameRenderer.getMainCamera().isDetached()) {
            ((CameraAccessor) (Object) mc.gameRenderer.getMainCamera()).setDetached(false);
        }

        boolean wearingDetector = SanityMonitorDetector.isWearingSanityMonitor(player);
        SanityScreenEffects.update(cachedSanity, mc, player, wearingDetector);
    }

    @SubscribeEvent
    public void onRenderGuiPost(RenderGuiEvent.Post event) {
        float vignette = SanityScreenEffects.vignetteStrength;
        float desat = SanityScreenEffects.desaturationStrength;
        float daze = SanityScreenEffects.dazePulse;
        float noise = SanityScreenEffects.noiseIntensity;

        if (vignette <= 0.0f && desat <= 0.0f && noise <= 0.0f) return;

        var guiGraphics = event.getGuiGraphics();
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float shortSide = Math.min(width, height);

        // === Noise: random 2x2 pixel dots (TV snow) ===
        if (noise > 0.0f) {
            int alpha = (int) (noise * 0.55f * 255);
            int whiteColor = (alpha << 24) | 0xFFFFFF;
            int blackColor = alpha << 24;
            int dotCount = (int) (MAX_NOISE_DOTS * noise);
            
            for (int i = 0; i < dotCount; i++) {
                int x = RANDOM.nextInt(width - NOISE_DOT_SIZE);
                int y = RANDOM.nextInt(height - NOISE_DOT_SIZE);
                guiGraphics.fill(x, y, x + NOISE_DOT_SIZE, y + NOISE_DOT_SIZE,
                        RANDOM.nextBoolean() ? whiteColor : blackColor);
            }

            // Scanlines over noise
            float scanAlpha = noise * 0.45f;
            int scanColor = (int) (scanAlpha * 255) << 24;
            int lineGap = 3;
            for (int y = 0; y < height; y += lineGap) {
                guiGraphics.fill(0, y, width, y + 1, scanColor);
            }
        }

        // === Vignette: multi-layer radial gradient (tunnel vision) ===
        if (vignette > 0.0f) {
            int layers = 20;
            for (int i = 0; i < layers; i++) {
                float t = (float) i / (layers - 1);
                // Offset from each edge: from ~2% to ~35% of short side
                int offset = (int) (shortSide * (0.02f + t * 0.33f));
                // Alpha decreases from outer to inner: edges dark, center clear
                int alpha = (int) (vignette * (1.0f - t * 0.88f) * 80);
                int color = alpha << 24;

                guiGraphics.fill(0, 0, width, offset, color);
                guiGraphics.fill(0, height - offset, width, height, color);
                guiGraphics.fill(0, offset, offset, height - offset, color);
                guiGraphics.fill(width - offset, offset, width, height - offset, color);
            }
        }

        // === Desaturation overlay: trance-like gray wash ===
        if (desat > 0.0f) {
            float dazeFactor = daze * 0.5f + 0.5f;
            float overlayAlpha = desat * 0.22f * dazeFactor;
            int gray128 = 128;
            int overlayColor = ((int) (overlayAlpha * 255) << 24) | (gray128 << 16) | (gray128 << 8) | gray128;
            guiGraphics.fill(0, 0, width, height, overlayColor);
        }
    }
}