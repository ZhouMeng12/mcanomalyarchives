package net.mcreator.mcanomalyarchives.procedures;

import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

public class ChairShuBiaoYouJianDanJiFangKuaiShiProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, BlockState blockstate, Entity entity) {
		if (entity == null)
			return;
		Entity sittingenity = null;
		if (!(getPropertyByName(blockstate, "is_sitting") instanceof BooleanProperty _getbp1 && blockstate.getValue(_getbp1))) {
			sittingenity = world instanceof ServerLevel _level2 ? McanomalyarchivesModEntities.SITTING_ENITY.get().spawn(_level2, BlockPos.containing(x, y, z), MobSpawnType.MOB_SUMMONED) : null;
			entity.startRiding(sittingenity);
			{
				BlockPos _pos = BlockPos.containing(x, y, z);
				BlockState _bs = world.getBlockState(_pos);
				if (_bs.getBlock().getStateDefinition().getProperty("is_sitting") instanceof BooleanProperty _booleanProp)
					world.setBlock(_pos, _bs.setValue(_booleanProp, true), 3);
			}
		}
	}

	private static Property<?> getPropertyByName(BlockState state, String name) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals(name)) {
				return property;
			}
		}
		return null;
	}
}