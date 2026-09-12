package net.mcreator.mcanomalyarchives.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.world.level.GameRules;

/** UO-012 粉羊机制总开关：/gamerule pinkSheepLuck false 可停用全部幸运/灾厄/观察者效应 */
@EventBusSubscriber(modid = net.mcreator.mcanomalyarchives.McanomalyarchivesMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class PinkSheepGameRules {

	public static GameRules.Key<GameRules.BooleanValue> PINK_SHEEP_LUCK;

	@SubscribeEvent
	public static void register(FMLCommonSetupEvent event) {
		PINK_SHEEP_LUCK = GameRules.register("pinkSheepLuck", GameRules.Category.PLAYER,
				GameRules.BooleanValue.create(true));
	}

	/** 服务端查询开关（handler 在服务端 tick 调用，传入 server） */
	public static boolean enabled(net.minecraft.server.MinecraftServer server) {
		return PINK_SHEEP_LUCK == null || server == null
				|| server.getGameRules().getBoolean(PINK_SHEEP_LUCK);
	}
}
