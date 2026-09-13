/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class McanomalyarchivesModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, McanomalyarchivesMod.MODID);
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ANOMALY_ARCHIVES = REGISTRY.register("anomaly_archives",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.mcanomalyarchives.anomaly_archives")).icon(() -> new ItemStack(McanomalyarchivesModItems.ICON.get())).displayItems((parameters, tabData) -> {
				tabData.accept(McanomalyarchivesModItems.STANGE_CLOUD_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModBlocks.CLOUD_STONE.get().asItem());
				tabData.accept(McanomalyarchivesModItems.CLOUD_DEM.get());
				tabData.accept(McanomalyarchivesModItems.CLOUD_WATER_BUCKET.get());
				tabData.accept(McanomalyarchivesModItems.CLOUD_WATER_BOTTLE.get());
				tabData.accept(McanomalyarchivesModBlocks.CORN_POPPY.get().asItem());
				tabData.accept(McanomalyarchivesModItems.ANBULA_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModBlocks.DEAD_POPPY.get().asItem());
				tabData.accept(McanomalyarchivesModItems.PRISONER_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModBlocks.CHAIR.get().asItem());
				tabData.accept(McanomalyarchivesModBlocks.COMPUTER.get().asItem());
				tabData.accept(McanomalyarchivesModItems.SVAN_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModBlocks.ORANGE_LOG.get().asItem());
				tabData.accept(McanomalyarchivesModBlocks.ORANGE_LEAVES.get().asItem());
				tabData.accept(McanomalyarchivesModBlocks.STRIPPED_ORANGE_LOG.get().asItem());
				tabData.accept(McanomalyarchivesModBlocks.ORANGE_PLANK.get().asItem());
				tabData.accept(McanomalyarchivesModBlocks.ORANGE_SAPLING.get().asItem());
				tabData.accept(McanomalyarchivesModItems.ORANGE.get());
				tabData.accept(McanomalyarchivesModItems.QU_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModItems.STRANGE_FISHING_ROD.get());
				tabData.accept(McanomalyarchivesModItems.DETECTER.get());
				tabData.accept(McanomalyarchivesModItems.PURPLE_MONSTER_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModItems.PURPLEHAND.get());
				tabData.accept(McanomalyarchivesModBlocks.CLOUD_ROCK.get().asItem());
				tabData.accept(McanomalyarchivesModBlocks.CLOUD_ORE.get().asItem());
				tabData.accept(McanomalyarchivesModItems.CLOUD_INGOT.get());
				tabData.accept(McanomalyarchivesModItems.CLOUD_UPGRADE.get());
				tabData.accept(McanomalyarchivesModItems.CLOUD_ARMOR_HELMET.get());
				tabData.accept(McanomalyarchivesModItems.CLOUD_ARMOR_CHESTPLATE.get());
				tabData.accept(McanomalyarchivesModItems.CLOUD_ARMOR_LEGGINGS.get());
				tabData.accept(McanomalyarchivesModItems.CLOUD_ARMOR_BOOTS.get());
				tabData.accept(McanomalyarchivesModItems.CLOUD_SCRAP.get());
				tabData.accept(McanomalyarchivesModItems.YIFULIN_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModItems.DAVID_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModItems.POTTER_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModBlocks.STRANGETREELOG.get().asItem());
				tabData.accept(McanomalyarchivesModBlocks.STRANGETREELEAVES.get().asItem());
				tabData.accept(McanomalyarchivesModItems.STRANGE_TREE_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModItems.PINK_SHEEP_SPAWN_EGG.get());
				tabData.accept(McanomalyarchivesModItems.ANOMALY_CODEX.get());
			}).build());
}