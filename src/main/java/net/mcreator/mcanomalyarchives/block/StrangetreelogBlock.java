package net.mcreator.mcanomalyarchives.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;

public class StrangetreelogBlock extends Block {
	public StrangetreelogBlock() {
		super(BlockBehaviour.Properties.of().sound(SoundType.WOOD).strength(-1, 3600000));
	}
}