package net.mcreator.mcanomalyarchives.procedures;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;

public class CloudWaterButtleWanJiaWanChengShiYongWuPinShiProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
			_entity.addEffect(new MobEffectInstance(McanomalyarchivesModMobEffects.CLOUDING, 3000, 1));
	}
}