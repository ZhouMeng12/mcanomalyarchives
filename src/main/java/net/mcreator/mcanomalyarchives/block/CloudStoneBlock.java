package net.mcreator.mcanomalyarchives.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;

public class CloudStoneBlock extends Block {
	public CloudStoneBlock() {
		super(BlockBehaviour.Properties.of().strength(1f, 10f));
	}
}