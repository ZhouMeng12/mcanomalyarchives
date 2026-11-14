package net.mcreator.mcanomalyarchives.init;

import net.minecraft.world.level.block.Block;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.block.NegativeSpaceBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

public final class UO007Blocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(McanomalyarchivesMod.MODID);
    public static final DeferredBlock<Block> NEGATIVE_SPACE = REGISTRY.register("negative_space", NegativeSpaceBlock::new);
    private UO007Blocks() { }
}
