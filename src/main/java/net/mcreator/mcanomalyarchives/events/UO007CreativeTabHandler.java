package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModTabs;
import net.mcreator.mcanomalyarchives.init.UO007Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid = McanomalyarchivesMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class UO007CreativeTabHandler {
	private UO007CreativeTabHandler() { }

	@SubscribeEvent
	public static void addUO007Items(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey().equals(McanomalyarchivesModTabs.ANOMALY_ARCHIVES.getKey())) {
			event.accept(UO007Items.NEGATIVE_APPLE.get());
			event.accept(UO007Items.NEGATIVE_DIAMOND_PICKAXE.get());
		}
	}
}
