package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

import java.util.function.Predicate;

public class GardenStructureListener {

	private static final ResourceLocation GARDEN_STRUCTURE_ID = ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "garden");
	private static final ResourceLocation GETTOGARDEN_ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "gettogarden");

	public static void init() {
		NeoForge.EVENT_BUS.register(new GardenStructureListener());
	}

	@SubscribeEvent
	public void onServerTick(ServerTickEvent.Post event) {
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			checkPlayerInGarden(player);
		}
	}

	private void checkPlayerInGarden(ServerPlayer player) {
		BlockPos playerPos = player.blockPosition();
		
		// 用Predicate<Holder<Structure>>来检测garden结构
		Predicate<Holder<Structure>> gardenPredicate = structureHolder -> {
			ResourceLocation structureId = structureHolder.unwrapKey()
				.map(key -> key.location())
				.orElse(null);
			
			return GARDEN_STRUCTURE_ID.equals(structureId);
		};
		
		// 检查玩家是否在garden结构内
		// 1.21.1: StructureManager 通过 ServerLevel.structureManager() 访问（不是 Level.getStructureManager()）
		boolean inGarden = player.serverLevel().structureManager()
			.getStructureWithPieceAt(playerPos, gardenPredicate)
			.isValid();
		
		if (inGarden) {
			awardGetToGardenAdvancement(player);
		}
	}

	private void awardGetToGardenAdvancement(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server != null) {
			AdvancementHolder advancementHolder = server.getAdvancements().get(GETTOGARDEN_ADVANCEMENT);
			if (advancementHolder != null) {
				// 检查玩家是否还没有获得这个成就
				if (!player.getAdvancements().getOrStartProgress(advancementHolder).isDone()) {
					player.getAdvancements().award(advancementHolder, "gettogarden_0");
				}
			}
		}
	}
}
