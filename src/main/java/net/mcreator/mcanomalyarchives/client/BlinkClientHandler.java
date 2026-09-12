package net.mcreator.mcanomalyarchives.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 客户端眨眼演出：收到 PlayerBlinkPacket 后，短时间内全屏压暗（模拟闭眼一瞬）。
 * 时长约 4 tick（0.2s）：前段渐暗、后段渐亮。
 */
public final class BlinkClientHandler {

    private static final int BLINK_TOTAL_TICKS = 5;
    private static long blinkStartMs = -1;
    private static long blinkEndMs = -1;

    private BlinkClientHandler() {
    }

    public static void init() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(BlinkClientHandler.class);
        }
    }

    /** 由网络包调用：触发一次眨眼 */
    public static void playBlink() {
        long now = System.currentTimeMillis();
        blinkStartMs = now;
        blinkEndMs = now + BLINK_TOTAL_TICKS * 50L;
    }

    public static boolean isBlinking() {
        return System.currentTimeMillis() < blinkEndMs;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        long now = System.currentTimeMillis();
        if (now >= blinkEndMs || blinkStartMs < 0)
            return;
        // 透明度曲线：0→1→0
        long total = blinkEndMs - blinkStartMs;
        if (total <= 0)
            return;
        float t = (float) (now - blinkStartMs) / (float) total;
        float alpha;
        if (t < 0.5F) {
            alpha = t * 2.0F;              // 前段渐暗
        } else {
            alpha = (1.0F - t) * 2.0F;     // 后段渐亮
        }
        // 上限到 ~0.85 不完全黑屏，保证不眩晕
        alpha = Math.min(alpha, 0.85F);
        GuiGraphics gui = event.getGuiGraphics();
        int a = (int) (alpha * 255.0F) << 24;
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        gui.fill(0, 0, width, height, 0xFF000000 & 0x00FFFFFF | a);
    }
}
