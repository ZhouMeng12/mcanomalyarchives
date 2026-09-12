package net.mcreator.mcanomalyarchives.procedures;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;

public class SittingEnityShiTiYouXiKeGengXinShiProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		if (!((world.getBlockState(BlockPos.containing(x, y, z))).getBlock() == McanomalyarchivesModBlocks.CHAIR.get())) {
			if (!entity.level().isClientSide())
				entity.discard();
		}
		if (!entity.isVehicle()) {
			{
				BlockPos _pos = BlockPos.containing(x, y, z);
				BlockState _bs = world.getBlockState(_pos);
				if (_bs.getBlock().getStateDefinition().getProperty("is_sitting") instanceof BooleanProperty _booleanProp)
					world.setBlock(_pos, _bs.setValue(_booleanProp, false), 3);
			}
			if (!entity.level().isClientSide())
				entity.discard();
		}
	}
}