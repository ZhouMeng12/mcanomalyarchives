package net.mcreator.mcanomalyarchives.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;

public class StrangetreeleavesFangKuaiPoHuaiProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		if (!(entity instanceof Player _plr ? _plr.getAbilities().instabuild : false)) {
			world.setBlock(BlockPos.containing(x, y, z), McanomalyarchivesModBlocks.STRANGETREELEAVES.get().defaultBlockState(), 3);
		}
	}
}