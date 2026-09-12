package net.mcreator.mcanomalyarchives.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

public class PurplehandHuiFangTiaoJianProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		return entity instanceof LivingEntity _livEnt0 && _livEnt0.swinging && (entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getItem() == McanomalyarchivesModItems.PURPLEHAND.get();
	}
}