package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModGameRules;
import net.mcreator.mcanomalyarchives.block.CornPoppyBlock;
import net.mcreator.mcanomalyarchives.events.SanityEventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CornPoppyAngryListener {
	private static final TagKey<EntityType<?>> NPC_TAG = TagKey.create(
		Registries.ENTITY_TYPE,
		ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "npc")
	);

	public static void init() {
		NeoForge.EVENT_BUS.register(new CornPoppyAngryListener());
	}

	@SubscribeEvent
	public void onServerTick(ServerTickEvent.Post event) {
		ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
		if (overworld == null) return;
		
		long currentTime = overworld.getGameTime();
		List<BlockPos> toRemove = new ArrayList<>();

		for (Map.Entry<BlockPos, Long> entry : CornPoppyBlock.getLastWatchedTimeMap().entrySet()) {
			BlockPos pos = entry.getKey();

			if (!overworld.isInWorldBounds(pos)) {
				toRemove.add(pos);
				continue;
			}

			Block currentBlock = overworld.getBlockState(pos).getBlock();
			if (currentBlock != McanomalyarchivesModBlocks.CORN_POPPY.get() && 
				currentBlock != McanomalyarchivesModBlocks.SAD_POPPY.get()) {
				toRemove.add(pos);
				continue;
			}

			long lastWatched = entry.getValue();
			Long angryEnd = CornPoppyBlock.getAngryEndTime(pos);

			if (angryEnd != null && currentTime > angryEnd) {
				if (overworld.getBlockState(pos).getBlock() == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
					overworld.setBlock(pos, McanomalyarchivesModBlocks.CORN_POPPY.get().defaultBlockState(), 3);
				}
				toRemove.add(pos);
				continue;
			}

			if (angryEnd != null) {
				continue;
			}

			if (!CornPoppyBlock.getHasBeenWatched(pos)) {
				continue;
			}

			long timeSinceLastWatch = currentTime - lastWatched;
			if (timeSinceLastWatch >= CornPoppyBlock.getAngryTriggerTime()) {
				if (!overworld.getGameRules().getBoolean(McanomalyarchivesModGameRules.CORN_POPPY_SAD)) {
					continue;
				}
				CornPoppyBlock.putAngryEndTime(pos, currentTime + CornPoppyBlock.getAngryDuration());
				overworld.setBlock(pos, McanomalyarchivesModBlocks.SAD_POPPY.get().defaultBlockState(), 3);
				applyAngryEffect(overworld, pos);
			}
		}

		for (BlockPos pos : toRemove) {
			CornPoppyBlock.removeFromAllMaps(pos);
		}
	}

	private void applyAngryEffect(ServerLevel level, BlockPos pos) {
		AABB range = new AABB(pos).inflate(CornPoppyBlock.getAngryRange());
		
		List<Player> players = level.getEntitiesOfClass(Player.class, range);
		for (Player player : players) {
			applyAngryDebuffs(player);

			if (player instanceof ServerPlayer serverPlayer) {
				SanityEventHandler.onSadPoppyProximity(serverPlayer);
				serverPlayer.displayClientMessage(
					Component.literal("为什么要让我心碎？").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					false
				);
				
				net.minecraft.server.MinecraftServer server = serverPlayer.getServer();
				if (server != null) {
					net.minecraft.advancements.AdvancementHolder advancementHolder = 
						server.getAdvancements().get(
							net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
								"mcanomalyarchives", "whyletmesad"
							)
						);
					if (advancementHolder != null && 
						!serverPlayer.getAdvancements().getOrStartProgress(advancementHolder).isDone()) {
						serverPlayer.getAdvancements().award(advancementHolder, "whyletmesad_0");
					}
				}
			}
		}

		List<LivingEntity> npcs = level.getEntitiesOfClass(LivingEntity.class, range,
			e -> e.getType().is(NPC_TAG) && !(e instanceof Player));
		for (LivingEntity npc : npcs) {
			applyAngryDebuffs(npc);
		}
	}

	private void applyAngryDebuffs(LivingEntity entity) {
		int sadLevel = 5;
		
		entity.addEffect(new MobEffectInstance(
			McanomalyarchivesModMobEffects.SAD,
			(int) CornPoppyBlock.getAngryDuration(),
			sadLevel - 1,
			false,
			true
		));

		int slownessLevel = Math.min(sadLevel - 1, 4);
		if (slownessLevel >= 0) {
			entity.addEffect(new MobEffectInstance(
				MobEffects.MOVEMENT_SLOWDOWN,
				(int) CornPoppyBlock.getAngryDuration(),
				slownessLevel,
				false,
				false
			));
		}

		int weaknessLevel = Math.min((sadLevel - 1) / 2, 4);
		if (weaknessLevel >= 0) {
			entity.addEffect(new MobEffectInstance(
				MobEffects.WEAKNESS,
				(int) CornPoppyBlock.getAngryDuration(),
				weaknessLevel,
				false,
				false
			));
		}
	}
}