package net.mcreator.mcanomalyarchives.block;

import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.LiquidBlock;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModFluids;

public class CloudWaterBlock extends LiquidBlock {
	public CloudWaterBlock() {
		super(McanomalyarchivesModFluids.CLOUD_WATER.get(), BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(100f).noCollission().noLootTable().liquid().pushReaction(PushReaction.DESTROY).sound(SoundType.EMPTY).replaceable());
	}
}