package net.mcreator.mcanomalyarchives.event;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public class PurplehandAOEHandler {

	private static final Map<UUID, Boolean> prevSwinging = new ConcurrentHashMap<>();

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (!(player instanceof ServerPlayer serverPlayer))
			return;

		boolean swinging = player.swinging;
		boolean prev = prevSwinging.getOrDefault(player.getUUID(), false);
		prevSwinging.put(player.getUUID(), swinging);

		if (swinging && !prev) {
			if (player.getMainHandItem().getItem() == McanomalyarchivesModItems.PURPLEHAND.get()) {
				McanomalyarchivesMod.queueServerWork(7, () -> triggerAOE(serverPlayer));
			}
		}
	}

	private static void triggerAOE(ServerPlayer attacker) {
		ServerLevel level = attacker.level() instanceof ServerLevel sl ? sl : null;
		if (level == null)
			return;
		AABB aoe = attacker.getBoundingBox().inflate(3.0);
		List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aoe,
				e -> e != attacker);
		for (LivingEntity e : entities) {
			e.invulnerableTime = 0;
			e.hurt(e.damageSources().mobAttack(attacker), 20.0f);
		}
	}
}
