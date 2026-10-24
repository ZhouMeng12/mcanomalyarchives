package net.mcreator.mcanomalyarchives.client;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = McanomalyarchivesMod.MODID, value = Dist.CLIENT)
public final class NamedAppearanceLifecycle {
	@SubscribeEvent
	public static void onLeave(EntityLeaveLevelEvent event) {
		if (event.getLevel().isClientSide()) NamedTransformClient.forget(event.getEntity().getId());
	}
	@SubscribeEvent
	public static void onUnload(LevelEvent.Unload event) {
		if (!event.getLevel().isClientSide()) return;
		NamedTransformClient.clear();
		NamedEntityAppearance.clear();
	}
}
