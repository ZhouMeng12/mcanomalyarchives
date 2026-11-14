package net.mcreator.mcanomalyarchives.client;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.NegativeReturnArrowEntity;
import net.mcreator.mcanomalyarchives.init.UO007Entities;
import net.mcreator.mcanomalyarchives.init.UO007Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = McanomalyarchivesMod.MODID, value = Dist.CLIENT)
public final class NegativeBowClient {
    private NegativeBowClient() { }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(net.mcreator.mcanomalyarchives.init.UO007Blocks.NEGATIVE_SPACE.get(), net.minecraft.client.renderer.RenderType.cutout());
            ItemProperties.register(UO007Items.NEGATIVE_MUSIC_DISC.get(), ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "disc_wear"),
                    (stack, level, entity, seed) -> net.mcreator.mcanomalyarchives.item.NegativeMusicDiscItem.usedCharges(stack) / 10f);
            ItemProperties.register(UO007Items.NEGATIVE_BOW.get(), ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> entity != null && entity.getUseItem() == stack
                            ? (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20f : 0f);
            ItemProperties.register(UO007Items.NEGATIVE_BOW.get(), ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
        });
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(UO007Entities.RETURN_ARROW.get(), context -> new ArrowRenderer<NegativeReturnArrowEntity>(context) {
            private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

            @Override
            public ResourceLocation getTextureLocation(NegativeReturnArrowEntity arrow) {
                return TEXTURE;
            }
        });
    }
}
