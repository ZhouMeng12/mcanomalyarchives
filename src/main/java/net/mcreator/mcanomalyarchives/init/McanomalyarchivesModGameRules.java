/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.GameRules;

@EventBusSubscriber
public class McanomalyarchivesModGameRules {
	public static GameRules.Key<GameRules.BooleanValue> CORN_POPPY_SAD;

	@SubscribeEvent
	public static void registerGameRules(FMLCommonSetupEvent event) {
		CORN_POPPY_SAD = GameRules.register("cornPoppySad", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
	}
}