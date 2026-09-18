/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.level.block.Block;

import net.mcreator.mcanomalyarchives.block.*;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class McanomalyarchivesModBlocks {
	public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(McanomalyarchivesMod.MODID);
	public static final DeferredBlock<Block> CLOUD_STONE;
	public static final DeferredBlock<Block> CLOUD_DEM_PORTAL;
	public static final DeferredBlock<Block> CLOUD_WATER;
	public static final DeferredBlock<Block> CORN_POPPY;
	public static final DeferredBlock<Block> SAD_POPPY;
	public static final DeferredBlock<Block> DEAD_POPPY;
	public static final DeferredBlock<Block> CHAIR;
	public static final DeferredBlock<Block> COMPUTER;
	public static final DeferredBlock<Block> ORANGE_LOG;
	public static final DeferredBlock<Block> ORANGE_LEAVES;
	public static final DeferredBlock<Block> STRIPPED_ORANGE_LOG;
	public static final DeferredBlock<Block> ORANGE_PLANK;
	public static final DeferredBlock<Block> ORANGE_SAPLING;
	public static final DeferredBlock<Block> CLOUD_ROCK;
	public static final DeferredBlock<Block> CLOUD_ORE;
	public static final DeferredBlock<Block> STRANGETREELOG;
	public static final DeferredBlock<Block> STRANGETREELEAVES;
	static {
		CLOUD_STONE = REGISTRY.register("cloud_stone", CloudStoneBlock::new);
		CLOUD_DEM_PORTAL = REGISTRY.register("cloud_dem_portal", CloudDemPortalBlock::new);
		CLOUD_WATER = REGISTRY.register("cloud_water", CloudWaterBlock::new);
		CORN_POPPY = REGISTRY.register("corn_poppy", CornPoppyBlock::new);
		SAD_POPPY = REGISTRY.register("sad_poppy", SadPoppyBlock::new);
		DEAD_POPPY = REGISTRY.register("dead_poppy", DeadPoppyBlock::new);
		CHAIR = REGISTRY.register("chair", ChairBlock::new);
		COMPUTER = REGISTRY.register("computer", ComputerBlock::new);
		ORANGE_LOG = REGISTRY.register("orange_log", OrangeTreeLogBlock::new);
		ORANGE_LEAVES = REGISTRY.register("orange_leaves", OrangeTreeLeavesBlock::new);
		STRIPPED_ORANGE_LOG = REGISTRY.register("stripped_orange_log", StrippedOrangeLogBlock::new);
		ORANGE_PLANK = REGISTRY.register("orange_plank", OrangeWoodBlock::new);
		ORANGE_SAPLING = REGISTRY.register("orange_sapling", OrangeSaplingBlock::new);
		CLOUD_ROCK = REGISTRY.register("cloud_rock", CloudRockBlock::new);
		CLOUD_ORE = REGISTRY.register("cloud_ore", CloudOreBlock::new);
		STRANGETREELOG = REGISTRY.register("strangetreelog", StrangetreelogBlock::new);
		STRANGETREELEAVES = REGISTRY.register("strangetreeleaves", StrangetreeleavesBlock::new);
	}

	// Start of user code block custom blocks
	// End of user code block custom blocks
	@EventBusSubscriber(Dist.CLIENT)
	public static class BlocksClientSideHandler {
		@SubscribeEvent
		public static void blockColorLoad(RegisterColorHandlersEvent.Block event) {
			SadPoppyBlock.blockColorLoad(event);
			DeadPoppyBlock.blockColorLoad(event);
		}
	}
}