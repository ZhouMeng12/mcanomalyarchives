package net.mcreator.mcanomalyarchives.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Registers the vanilla fishing-rod cast predicate for this custom rod item. */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID, value = Dist.CLIENT)
public final class StrangeFishingRodModelProperty {
	private StrangeFishingRodModelProperty() { }

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> ItemProperties.register(
				McanomalyarchivesModItems.STRANGE_FISHING_ROD.get(),
				ResourceLocation.withDefaultNamespace("cast"),
				(stack, level, entity, seed) -> entity instanceof Player player && player.fishing != null ? 1.0F : 0.0F));
	}
}
