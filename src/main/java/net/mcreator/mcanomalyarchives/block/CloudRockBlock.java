package net.mcreator.mcanomalyarchives.block;

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;

public class CloudRockBlock extends Block {
	public CloudRockBlock() {
		super(BlockBehaviour.Properties.of().sound(SoundType.GRAVEL).strength(1f, 10f).instrument(NoteBlockInstrument.BASEDRUM));
	}
}