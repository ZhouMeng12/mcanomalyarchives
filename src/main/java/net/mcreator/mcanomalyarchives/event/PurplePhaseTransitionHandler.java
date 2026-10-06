package net.mcreator.mcanomalyarchives.event;

import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;
import net.minecraft.server.level.ServerPlayer;

@EventBusSubscriber
public class PurplePhaseTransitionHandler {
	public static void startTransition(ServerPlayer player, int purpleEntityId) {
		PurpleStoryModule.advanceToPhase4(player, purpleEntityId);
	}

	public static void startTransitionToPhase2(ServerPlayer player, int purpleEntityId) {
		PurpleStoryModule.advanceToPhase2(player, purpleEntityId);
	}

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent.Pre event) {
		if (event.getEntity() instanceof PurpleMonsterEntity monster && monster.isPerformMode()) {
			event.setNewDamage(0);
		}
		if (event.getEntity() instanceof PurpleDogEntity dog && dog.isPerformMode()) {
			event.setNewDamage(0);
		}
	}

}
