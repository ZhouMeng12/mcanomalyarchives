/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;

import net.mcreator.mcanomalyarchives.item.*;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

@EventBusSubscriber
public class McanomalyarchivesModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(McanomalyarchivesMod.MODID);
	public static final DeferredItem<Item> STANGE_CLOUD_SPAWN_EGG;
	public static final DeferredItem<Item> CLOUD_STONE;
	public static final DeferredItem<Item> CLOUD_DEM;
	public static final DeferredItem<Item> CLOUD_WATER_BUCKET;
	public static final DeferredItem<Item> CLOUD_WATER_BOTTLE;
	public static final DeferredItem<Item> CORN_POPPY;
	public static final DeferredItem<Item> ICON;
	public static final DeferredItem<Item> ANBULA_SPAWN_EGG;
	public static final DeferredItem<Item> SADICON;
	public static final DeferredItem<Item> HEARTICON;
	public static final DeferredItem<Item> SAD_POPPY;
	public static final DeferredItem<Item> DEAD_POPPY;
	public static final DeferredItem<Item> PRISONER_SPAWN_EGG;
	public static final DeferredItem<Item> CHAIR;
	public static final DeferredItem<Item> COMPUTER;
	public static final DeferredItem<Item> SVAN_SPAWN_EGG;
	public static final DeferredItem<Item> ORANGE_LOG;
	public static final DeferredItem<Item> ORANGE_LEAVES;
	public static final DeferredItem<Item> STRIPPED_ORANGE_LOG;
	public static final DeferredItem<Item> ORANGE_PLANK;
	public static final DeferredItem<Item> ORANGE_SAPLING;
	public static final DeferredItem<Item> ORANGE;
	public static final DeferredItem<Item> QU_SPAWN_EGG;
	public static final DeferredItem<Item> STRANGE_FISHING_ROD;
	public static final DeferredItem<Item> DETECTER;
	public static final DeferredItem<Item> PURPLE_MONSTER_SPAWN_EGG;
	public static final DeferredItem<Item> PURPLEHAND;
	public static final DeferredItem<Item> CLOUD_ROCK;
	public static final DeferredItem<Item> CLOUD_ORE;
	public static final DeferredItem<Item> CLOUD_INGOT;
	public static final DeferredItem<Item> CLOUD_UPGRADE;
	public static final DeferredItem<Item> CLOUD_ARMOR_HELMET;
	public static final DeferredItem<Item> CLOUD_ARMOR_CHESTPLATE;
	public static final DeferredItem<Item> CLOUD_ARMOR_LEGGINGS;
	public static final DeferredItem<Item> CLOUD_ARMOR_BOOTS;
	public static final DeferredItem<Item> CLOUD_SCRAP;
	public static final DeferredItem<Item> YIFULIN_SPAWN_EGG;
	public static final DeferredItem<Item> DAVID_SPAWN_EGG;
	public static final DeferredItem<Item> POTTER_SPAWN_EGG;
	public static final DeferredItem<Item> STRANGETREELOG;
	public static final DeferredItem<Item> STRANGETREELEAVES;
	public static final DeferredItem<Item> STRANGE_TREE_SPAWN_EGG;
	public static final DeferredItem<Item> PINK_SHEEP_SPAWN_EGG;
	public static final DeferredItem<Item> ANOMALY_CODEX;
	public static final DeferredItem<Item> BAIBIAN_NAME_TAG;
	public static final DeferredItem<Item> UO002_GARBLED;
	public static final DeferredItem<Item> SMALL_STANGE_CLOUD_SPAWN_EGG;
	public static final DeferredItem<Item> BLACK_STANGE_CLOUD_SPAWN_EGG;
	public static final DeferredItem<Item> YIFULIN_2_SPAWN_EGG;
	public static final DeferredItem<Item> UO002_MISPLACED;
	public static final DeferredItem<Item> REDFIN_SPAWN_EGG;
	public static final DeferredItem<Item> BLOOMCOW_SPAWN_EGG;
	public static final DeferredItem<Item> WALKSQUID_SPAWN_EGG;
	public static final DeferredItem<Item> PALE_SPAWN_EGG;
	public static final DeferredItem<Item> REMNANT_SPAWN_EGG;
	public static final DeferredItem<Item> FOLD_SPAWN_EGG;
	static {
		STANGE_CLOUD_SPAWN_EGG = REGISTRY.register("stange_cloud_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.STANGE_CLOUD, -1, -1, new Item.Properties()));
		CLOUD_STONE = block(McanomalyarchivesModBlocks.CLOUD_STONE);
		CLOUD_DEM = REGISTRY.register("cloud_dem", CloudDemItem::new);
		CLOUD_WATER_BUCKET = REGISTRY.register("cloud_water_bucket", CloudWaterItem::new);
		CLOUD_WATER_BOTTLE = REGISTRY.register("cloud_water_bottle", CloudWaterButtleItem::new);
		CORN_POPPY = block(McanomalyarchivesModBlocks.CORN_POPPY);
		ICON = REGISTRY.register("icon", IconItem::new);
		ANBULA_SPAWN_EGG = REGISTRY.register("anbula_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.ANBULA, -13421773, -16777216, new Item.Properties()));
		SADICON = REGISTRY.register("sadicon", SadiconItem::new);
		HEARTICON = REGISTRY.register("hearticon", HearticonItem::new);
		SAD_POPPY = block(McanomalyarchivesModBlocks.SAD_POPPY);
		DEAD_POPPY = block(McanomalyarchivesModBlocks.DEAD_POPPY);
		PRISONER_SPAWN_EGG = REGISTRY.register("prisoner_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.PRISONER, -39424, -1, new Item.Properties()));
		CHAIR = block(McanomalyarchivesModBlocks.CHAIR);
		COMPUTER = block(McanomalyarchivesModBlocks.COMPUTER);
		SVAN_SPAWN_EGG = REGISTRY.register("svan_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.SVAN, -205, -1, new Item.Properties()));
		ORANGE_LOG = block(McanomalyarchivesModBlocks.ORANGE_LOG);
		ORANGE_LEAVES = block(McanomalyarchivesModBlocks.ORANGE_LEAVES);
		STRIPPED_ORANGE_LOG = block(McanomalyarchivesModBlocks.STRIPPED_ORANGE_LOG);
		ORANGE_PLANK = block(McanomalyarchivesModBlocks.ORANGE_PLANK);
		ORANGE_SAPLING = block(McanomalyarchivesModBlocks.ORANGE_SAPLING);
		ORANGE = REGISTRY.register("orange", OrangeItem::new);
		QU_SPAWN_EGG = REGISTRY.register("qu_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.QU, -13382656, -256, new Item.Properties()));
		STRANGE_FISHING_ROD = REGISTRY.register("strange_fishing_rod", StrangeFishingRodItem::new);
		DETECTER = REGISTRY.register("detecter", DetecterItem::new);
		PURPLE_MONSTER_SPAWN_EGG = REGISTRY.register("purple_monster_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.PURPLE_MONSTER, -1, -1, new Item.Properties()));
		PURPLEHAND = REGISTRY.register("purplehand", PurplehandItem::new);
		CLOUD_ROCK = block(McanomalyarchivesModBlocks.CLOUD_ROCK);
		CLOUD_ORE = block(McanomalyarchivesModBlocks.CLOUD_ORE);
		CLOUD_INGOT = REGISTRY.register("cloud_ingot", CloudIngotItem::new);
		CLOUD_UPGRADE = REGISTRY.register("cloud_upgrade", CloudUpgradeItem::new);
		CLOUD_ARMOR_HELMET = REGISTRY.register("cloud_armor_helmet", CloudArmorItem.Helmet::new);
		CLOUD_ARMOR_CHESTPLATE = REGISTRY.register("cloud_armor_chestplate", CloudArmorItem.Chestplate::new);
		CLOUD_ARMOR_LEGGINGS = REGISTRY.register("cloud_armor_leggings", CloudArmorItem.Leggings::new);
		CLOUD_ARMOR_BOOTS = REGISTRY.register("cloud_armor_boots", CloudArmorItem.Boots::new);
		CLOUD_SCRAP = REGISTRY.register("cloud_scrap", CloudScrapItem::new);
		YIFULIN_SPAWN_EGG = REGISTRY.register("yifulin_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.YIFULIN, -1, -1, new Item.Properties()));
		DAVID_SPAWN_EGG = REGISTRY.register("david_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.DAVID, -1, -1, new Item.Properties()));
		POTTER_SPAWN_EGG = REGISTRY.register("potter_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.POTTER, -1, -1, new Item.Properties()));
		STRANGETREELOG = block(McanomalyarchivesModBlocks.STRANGETREELOG);
		STRANGETREELEAVES = block(McanomalyarchivesModBlocks.STRANGETREELEAVES);
		STRANGE_TREE_SPAWN_EGG = REGISTRY.register("strange_tree_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.STRANGE_TREE, -14803684, -15593199, new Item.Properties()));
		PINK_SHEEP_SPAWN_EGG = REGISTRY.register("pink_sheep_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.PINK_SHEEP, -1, -26164, new Item.Properties()));
		ANOMALY_CODEX = REGISTRY.register("anomaly_codex", AnomalyCodexItem::new);
		BAIBIAN_NAME_TAG = REGISTRY.register("baibian_name_tag", BaibianNameTagItem::new);
		UO002_GARBLED = REGISTRY.register("uo002_garbled", UO002GarbledItem::new);
		SMALL_STANGE_CLOUD_SPAWN_EGG = REGISTRY.register("small_stange_cloud_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.SMALL_STANGE_CLOUD, -1, -1, new Item.Properties()));
		BLACK_STANGE_CLOUD_SPAWN_EGG = REGISTRY.register("black_stange_cloud_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.BLACK_STANGE_CLOUD, -1, -1, new Item.Properties()));
		YIFULIN_2_SPAWN_EGG = REGISTRY.register("yifulin_2_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.YIFULIN_2, -1, -1, new Item.Properties()));
		UO002_MISPLACED = block(McanomalyarchivesModBlocks.UO002_MISPLACED);
		REDFIN_SPAWN_EGG = REGISTRY.register("redfin_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.REDFIN, 6503736, 14082261, new Item.Properties()));
		BLOOMCOW_SPAWN_EGG = REGISTRY.register("bloomcow_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.BLOOMCOW, 4876097, 15128435, new Item.Properties()));
		WALKSQUID_SPAWN_EGG = REGISTRY.register("walksquid_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.WALKSQUID, 5858923, 7981509, new Item.Properties()));
		PALE_SPAWN_EGG = REGISTRY.register("pale_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.PALE, 15197402, 3877180, new Item.Properties()));
		REMNANT_SPAWN_EGG = REGISTRY.register("remnant_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.REMNANT, 1053205, 4408906, new Item.Properties()));
		FOLD_SPAWN_EGG = REGISTRY.register("fold_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.FOLD, 13946036, 7693150, new Item.Properties()));
	}

	// Start of user code block custom items
	// End of user code block custom items
	@SubscribeEvent
	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new FluidBucketWrapper(stack), CLOUD_WATER_BUCKET.get());
	}

	private static DeferredItem<Item> block(DeferredHolder<Block, Block> block) {
		return block(block, new Item.Properties());
	}

	private static DeferredItem<Item> block(DeferredHolder<Block, Block> block, Item.Properties properties) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), properties));
	}
}