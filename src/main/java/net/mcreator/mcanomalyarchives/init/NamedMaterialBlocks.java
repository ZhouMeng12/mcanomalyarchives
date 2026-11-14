package net.mcreator.mcanomalyarchives.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.mcreator.mcanomalyarchives.block.LiquidCobblestoneBlock;
import net.neoforged.neoforge.registries.*;

public final class NamedMaterialBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("mcanomalyarchives");
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("mcanomalyarchives");
    public static final DeferredBlock<Block> LIQUID_COBBLESTONE = BLOCKS.register("liquid_cobblestone", LiquidCobblestoneBlock::new);
    public static final DeferredItem<Item> LIQUID_COBBLESTONE_ITEM = ITEMS.register("liquid_cobblestone", () -> new BlockItem(LIQUID_COBBLESTONE.get(), new Item.Properties()));
    private NamedMaterialBlocks() { }
}
