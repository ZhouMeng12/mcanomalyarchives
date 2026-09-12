package net.mcreator.mcanomalyarchives.client.render;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

public class SimplePoppyRenderer {

    public static void init() {
        if (FMLEnvironment.dist.isClient()) {
            McanomalyarchivesMod.LOGGER.info("=== SimplePoppyRenderer INITIALIZED ===");
            NeoForge.EVENT_BUS.register(new SimplePoppyRenderer());
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // 检查是否有上瘾效果
        MobEffectInstance effect = mc.player.getEffect(McanomalyarchivesModMobEffects.ADDICTE);
        if (effect == null) {
            return;
        }

        int addictionLevel = effect.getAmplifier() + 1;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        GuiGraphics guiGraphics = event.getGuiGraphics();

        McanomalyarchivesMod.LOGGER.info("=== SimplePoppyRenderer rendering, addictionLevel=" + addictionLevel);

        ItemStack poppyStack = new ItemStack(Items.POPPY);

        // 渲染一个大的虞美人区域
        int baseX = screenWidth / 2;
        int baseY = screenHeight / 2;
        int size = 50 + addictionLevel * 20;
        
        // 渲染多个重叠的虞美人，让它看起来很大
        for (int offsetX = -size; offsetX <= size; offsetX += 10) {
            for (int offsetY = -size; offsetY <= size; offsetY += 10) {
                guiGraphics.renderItem(poppyStack, baseX - 8 + offsetX, baseY - 8 + offsetY);
            }
        }
    }
}
