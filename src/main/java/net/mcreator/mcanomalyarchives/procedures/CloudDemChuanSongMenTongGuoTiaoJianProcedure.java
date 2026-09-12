package net.mcreator.mcanomalyarchives.procedures;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;

public class CloudDemChuanSongMenTongGuoTiaoJianProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		if (entity.canChangeDimensions(entity.level(), entity.level()) && entity instanceof LivingEntity _livEnt1 && _livEnt1.hasEffect(McanomalyarchivesModMobEffects.CLOUDING)) {
			return true;
		}
		return false;
	}
}