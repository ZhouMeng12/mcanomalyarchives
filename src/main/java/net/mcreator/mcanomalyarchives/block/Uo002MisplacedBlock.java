package net.mcreator.mcanomalyarchives.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Slowly converts nearby ordinary blocks into the overlapping UO-002 material. */
public final class Uo002MisplacedBlock extends Block {
	public Uo002MisplacedBlock() {
		super(BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(1.5f, 6.0f).randomTicks());
	}

	@Override
	public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// Random ticks happen infrequently; only one side is checked each time and
		// conversion has a 1-in-4 chance, keeping the spread gradual.
		if (random.nextInt(4) != 0) return;
		BlockPos target = pos.relative(Direction.values()[random.nextInt(Direction.values().length)]);
		if (!level.hasChunkAt(target)) return;
		BlockState neighbor = level.getBlockState(target);
		if (neighbor.isAir() || neighbor.hasBlockEntity() || neighbor.getDestroySpeed(level, target) < 0.0F
				|| !neighbor.getFluidState().isEmpty() || neighbor.getBlock() instanceof FallingBlock
				|| neighbor.is(this)) return;
		level.setBlock(target, defaultBlockState(), Block.UPDATE_ALL);
	}
}
