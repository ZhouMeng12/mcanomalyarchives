package net.mcreator.mcanomalyarchives.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class NamedPotatoClient {
    @SubscribeEvent public static void sprouts(RenderLivingEvent.Post<?, ?> event) {
        var entity = event.getEntity();
        if (!"item:minecraft:potato".equals(NamedTransformClient.targetOf(entity))) return;
        float progress = NamedTransformClient.progressOf(entity);
        if (progress < .75f) return;
        var poses = event.getPoseStack();
        poses.pushPose();
        try {
            poses.translate(-.2, entity.getBbHeight()*.85, -.2);
            float growth = .15f + .5f * (progress - .75f) / .25f;
            poses.scale(.4f, growth, .4f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.POTATOES.defaultBlockState(), poses,
                    event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        } finally { poses.popPose(); }
    }
}
