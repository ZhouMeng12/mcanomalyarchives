package net.mcreator.mcanomalyarchives.client.render;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PoppyEdgeRenderer {

    private static final List<Poppy2D> POPPIES = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int spawnTimer = 0;
    private static int logTimer = 0;

    private static class Poppy2D {
        float x, y, size, life, maxLife;

        Poppy2D(float x, float y, float size, float maxLife) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.life = 0;
            this.maxLife = maxLife;
        }
    }

    public static void clientTick() {
        logTimer++;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            POPPIES.clear();
            return;
        }

        int addictionLevel = getAddictionLevel();
        if (logTimer % 100 == 0) {
            // (debug log removed)
        }

        if (addictionLevel <= 0) {
            POPPIES.clear();
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        POPPIES.forEach(p -> p.life++);
        POPPIES.removeIf(p -> p.life > p.maxLife);

        int maxPoppies = (int) (Math.pow(1.7, addictionLevel) * 15);
        int spawnInterval = Math.max(1, 6 - addictionLevel / 2);

        spawnTimer++;
        if (spawnTimer >= spawnInterval && POPPIES.size() < maxPoppies) {
            spawnTimer = 0;
            int count = 3 + addictionLevel * 3;
            for (int i = 0; i < count; i++) {
                float x = RANDOM.nextFloat() * screenWidth;
                float y = RANDOM.nextFloat() * screenHeight;
                float size = 60 + RANDOM.nextFloat() * 60 + addictionLevel * 12;
                float maxLife = 200 + RANDOM.nextFloat() * 150;
                POPPIES.add(new Poppy2D(x, y, size, maxLife));
            }
        }
    }

    public static int getAddictionLevel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        MobEffectInstance effect = mc.player.getEffect(McanomalyarchivesModMobEffects.ADDICTE);
        if (effect == null) return 0;
        return effect.getAmplifier() + 1;
    }

    public static void renderHUD(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int addictionLevel = getAddictionLevel();
        if (addictionLevel <= 0 || POPPIES.isEmpty()) return;

        ItemStack poppyStack = new ItemStack(Items.POPPY);

        for (Poppy2D poppy : POPPIES) {
            guiGraphics.renderItem(poppyStack, (int) poppy.x - 8, (int) poppy.y - 8);
        }
    }

    public static void clear() {
        POPPIES.clear();
        spawnTimer = 0;
    }
}