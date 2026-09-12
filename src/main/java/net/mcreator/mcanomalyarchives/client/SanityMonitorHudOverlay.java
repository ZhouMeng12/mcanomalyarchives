package net.mcreator.mcanomalyarchives.client;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import net.mcreator.mcanomalyarchives.client.hud.*;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModDataComponents;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SanityMonitorHudOverlay {

    private static final int FRAME_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BG_COLOR = 0x08000000;
    private static final int HUD_MARGIN = 12;

    // Pixel circle: 12x12 logical pixels rendered at 2x scale = 24x24 canvas
    private static final int CIRCLE_SIZE = 12;
    private static final int CIRCLE_PX = 2; // pixel scale
    private static final int CIRCLE_CANVAS = CIRCLE_SIZE * CIRCLE_PX; // 24

    // Pre-computed 12x12 pixel circle pattern
    private static final int[][] CIRCLE_PATTERN = {
        {0,0,0,0,1,1,1,1,0,0,0,0},
        {0,0,1,1,1,1,1,1,1,1,0,0},
        {0,1,1,1,1,1,1,1,1,1,1,0},
        {0,1,1,1,1,1,1,1,1,1,1,0},
        {1,1,1,1,1,1,1,1,1,1,1,1},
        {1,1,1,1,1,1,1,1,1,1,1,1},
        {1,1,1,1,1,1,1,1,1,1,1,1},
        {1,1,1,1,1,1,1,1,1,1,1,1},
        {0,1,1,1,1,1,1,1,1,1,1,0},
        {0,1,1,1,1,1,1,1,1,1,1,0},
        {0,0,1,1,1,1,1,1,1,1,0,0},
        {0,0,0,0,1,1,1,1,0,0,0,0},
    };

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public static void init() {
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(new SanityMonitorHudOverlay());
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (player.isSpectator()) return;

        ItemStack detector = SanityMonitorDetector.getDetectorItemStack(player);
        if (detector == null) return;

        // 电量由服务端扣减（DetectorEnergyHandler），HUD 只读
        int energy = getEnergy(detector);
        if (energy <= 0) return;

        int sanity = SanityClientHandler.getCachedSanity();
        GuiGraphics graphics = event.getGuiGraphics();

        renderSciFiHud(graphics, sanity, energy, detector, player);
    }

    private void renderSciFiHud(GuiGraphics graphics, int sanity, int energy, ItemStack detector, LocalPlayer player) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int borderSize = 2;
        int m = HUD_MARGIN;

        graphics.fill(m, m, w - m, h - m, BG_COLOR);
        drawBorder(graphics, borderSize, m, w, h);
        drawCornerMarkers(graphics, m, w, h);
        renderTopLeft(graphics, sanity, detector, m);
        renderTopRight(graphics, sanity, player, m);
        renderBottomLeft(graphics, energy, m);
        renderBottomRight(graphics, m);
        drawDiagonalLines(graphics, m, w, h);
    }

    // === Border & Decor ===

    private void drawBorder(GuiGraphics graphics, int borderSize, int m, int w, int h) {
        int color = FRAME_COLOR;
        graphics.fill(m, m, w - m, m + borderSize, color);
        graphics.fill(m, h - m - borderSize, w - m, h - m, color);
        graphics.fill(m, m, m + borderSize, h - m, color);
        graphics.fill(w - m - borderSize, m, w - m, h - m, color);
    }

    private void drawCornerMarkers(GuiGraphics graphics, int m, int w, int h) {
        int margin = m + 8;
        Minecraft mc = Minecraft.getInstance();
        graphics.drawString(mc.font, "3", margin, margin, FRAME_COLOR);
        graphics.drawString(mc.font, "0", w - margin - mc.font.width("0"), margin, FRAME_COLOR);
        graphics.drawString(mc.font, "7", margin, h - margin - mc.font.lineHeight, FRAME_COLOR);
        graphics.drawString(mc.font, "4", w - margin - mc.font.width("4"), h - margin - mc.font.lineHeight, FRAME_COLOR);
    }

    private void drawDiagonalLines(GuiGraphics graphics, int m, int w, int h) {
        int color = FRAME_COLOR & 0x33FFFFFF;
        int d = 68;
        for (int i = 0; i < d; i += 4) {
            graphics.fill(m + d - i, m + i, m + d - i + 1, m + i + 1, color);
            graphics.fill(w - m - d + i, m + i, w - m - d + i + 1, m + i + 1, color);
            graphics.fill(m + d - i, h - m - 1 - i, m + d - i + 1, h - m - i, color);
            graphics.fill(w - m - d + i, h - m - 1 - i, w - m - d + i + 1, h - m - i, color);
        }
    }

    // === Top Left: Pixel Circle Indicator + Title ===

    private void renderTopLeft(GuiGraphics graphics, int sanity, ItemStack detector, int m) {
        int margin = m + 10;
        Minecraft mc = Minecraft.getInstance();

        // Draw pixel circle indicator
        int circleColor = NordIndexResolver.getColor(sanity);

        int circleX = margin;
        int circleY = margin + mc.font.lineHeight / 2 - CIRCLE_CANVAS / 2;

        for (int py = 0; py < CIRCLE_SIZE; py++) {
            for (int px = 0; px < CIRCLE_SIZE; px++) {
                if (CIRCLE_PATTERN[py][px] != 0) {
                    graphics.fill(
                        circleX + px * CIRCLE_PX,
                        circleY + py * CIRCLE_PX,
                        circleX + (px + 1) * CIRCLE_PX,
                        circleY + (py + 1) * CIRCLE_PX,
                        circleColor
                    );
                }
            }
        }

        // Draw title with serial
        String serial = getSerial(detector);
        String title = "神经监控-" + serial;
        graphics.drawString(mc.font, title, circleX + CIRCLE_CANVAS + 8, margin, TEXT_COLOR);
    }

    // === Top Right: Nord Index + Landa Index ===

    private void renderTopRight(GuiGraphics graphics, int sanity, LocalPlayer player, int m) {
        int margin = m + 10;
        int w = graphics.guiWidth();
        Minecraft mc = Minecraft.getInstance();

        String nordText = NordIndexResolver.getText(sanity);

        String nordLine = "诺德指数: " + nordText;
        int nordWidth = mc.font.width(nordLine);
        graphics.drawString(mc.font, nordLine, w - margin - nordWidth, margin, TEXT_COLOR);

        // Landa index (addiction)
        int addictionLevel = 0;
        var effect = player.getEffect(McanomalyarchivesModMobEffects.ADDICTE);
        if (effect != null) {
            addictionLevel = effect.getAmplifier() + 1;
        }
        double landa = LandaIndex.fromEffect(addictionLevel);
        if (landa > 0) {
            String landaLine = "兰达指数: " + String.format("%.1f", landa);
            int landaWidth = mc.font.width(landaLine);
            graphics.drawString(mc.font, landaLine, w - margin - landaWidth, margin + mc.font.lineHeight + 3, TEXT_COLOR);
        }
    }

    // === Bottom Left: Battery ===

    private void renderBottomLeft(GuiGraphics graphics, int energy, int m) {
        final int margin = m + 8;
        final int segments = 4;
        final int segWidth = 14;
        final int segHeight = 16;
        final int gap = 3;
        final int h = graphics.guiHeight();

        int totalWidth = segments * segWidth + (segments - 1) * gap;
        int batteryH = segHeight + 4; // + padding
        int batteryW = totalWidth + 4;
        int x = margin;
        int y = h - margin - batteryH;

        // Battery body border
        graphics.fill(x, y, x + batteryW, y + 1, FRAME_COLOR);       // top
        graphics.fill(x, y + batteryH - 1, x + batteryW, y + batteryH, FRAME_COLOR); // bottom
        graphics.fill(x, y, x + 1, y + batteryH, FRAME_COLOR);        // left
        graphics.fill(x + batteryW - 1, y, x + batteryW, y + batteryH, FRAME_COLOR); // right

        // Battery nub (right side bump)
        int nubW = 4;
        int nubH = 8;
        int nubX = x + batteryW;
        int nubY = y + (batteryH - nubH) / 2;
        graphics.fill(nubX, nubY, nubX + nubW, nubY + 1, FRAME_COLOR);
        graphics.fill(nubX, nubY + nubH - 1, nubX + nubW, nubY + nubH, FRAME_COLOR);
        graphics.fill(nubX + nubW - 1, nubY, nubX + nubW, nubY + nubH, FRAME_COLOR);

        // Segments
        int segCount = EnergyVisuals.getSegmentCount(energy, McanomalyarchivesModDataComponents.MAX_ENERGY);
        int segX = x + 2;
        int segY = y + 2;
        for (int i = 0; i < segments; i++) {
            if (i < segCount) {
                graphics.fill(segX, segY, segX + segWidth, segY + segHeight, 0xFFFFFFFF);
            }
            segX += segWidth + gap;
        }
    }

    // === Bottom Right: DateTime ===

    private void renderBottomRight(GuiGraphics graphics, int m) {
        int margin = m + 10;
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        Minecraft mc = Minecraft.getInstance();

        String time = LocalDateTime.now().format(TIME_FORMAT);
        int textX = w - margin - mc.font.width(time);
        int textY = h - margin - mc.font.lineHeight;

        graphics.drawString(mc.font, time, textX, textY, TEXT_COLOR);
    }

    // === Energy & Serial helpers (inlined from DetecterItem to survive MCreator overwrites) ===

    private static int getEnergy(ItemStack stack) {
        Integer energy = stack.get(McanomalyarchivesModDataComponents.ENERGY);
        if (energy == null) {
            energy = McanomalyarchivesModDataComponents.MAX_ENERGY;
            stack.set(McanomalyarchivesModDataComponents.ENERGY, energy);
        }
        return energy;
    }

    @SuppressWarnings("unused")
    private static int consumeEnergy(ItemStack stack) {
        // 客户端不再扣电，扣电已移至服务端 DetectorEnergyHandler
        return getEnergy(stack);
    }

    private static String getSerial(ItemStack stack) {
        String serial = stack.get(McanomalyarchivesModDataComponents.SERIAL_NUMBER);
        if (serial == null) {
            serial = SerialGenerator.generate();
            stack.set(McanomalyarchivesModDataComponents.SERIAL_NUMBER, serial);
        }
        return serial;
    }
}
