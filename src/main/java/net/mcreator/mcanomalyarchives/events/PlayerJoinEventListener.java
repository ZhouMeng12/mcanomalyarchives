package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.advancements.AdvancementHolder;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.sanity.PlayerSanity;

@EventBusSubscriber
public class PlayerJoinEventListener {

	public static void init() {
	}

	@SubscribeEvent
	public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ResourceLocation welcomeId = ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "welcome");
			MinecraftServer server = player.getServer();
			if (server != null) {
				AdvancementHolder welcomeHolder = server.getAdvancements().get(welcomeId);
				if (welcomeHolder != null) {
					player.getAdvancements().award(welcomeHolder, "welcome_0");
				}
			}
			
			PlayerSanity.syncToClient(player);
		}
	}
}