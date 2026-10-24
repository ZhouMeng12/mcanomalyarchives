package net.mcreator.mcanomalyarchives.client.event;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.renderer.item.PurplehandItemRenderer;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.network.PurplehandSweepPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = McanomalyarchivesMod.MODID, value = Dist.CLIENT)
public final class PurplehandCombatInput {
    private PurplehandCombatInput() { }

    @SubscribeEvent
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isCanceled() && event.isAttack() && mc.player != null && !mc.player.isSpectator()
                && !mc.player.isUsingItem() && mc.player.getMainHandItem().is(McanomalyarchivesModItems.PURPLEHAND.get())
                && mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.BLOCK) {
            PurplehandItemRenderer.startAttackAnimation();
        }
    }

    @SubscribeEvent
    public static void onEmptyAttack(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().getMainHandItem().is(McanomalyarchivesModItems.PURPLEHAND.get())) {
            PacketDistributor.sendToServer(new PurplehandSweepPacket());
        }
    }
}
