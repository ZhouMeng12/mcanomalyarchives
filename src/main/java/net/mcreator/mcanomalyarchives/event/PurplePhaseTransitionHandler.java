package net.mcreator.mcanomalyarchives.event;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.network.OpenPurpleGuiPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public class PurplePhaseTransitionHandler {
	private static final Map<UUID, TransitionState> TRANSITIONS = new ConcurrentHashMap<>();
	private static final int ENTITY_SPAWN_DELAY = 20; // 1秒后同时生成紫怪和紫狗
	private static final int GUI_OPEN_DELAY = 60; // 3秒后打开GUI

	public static void startTransition(ServerPlayer player, int purpleEntityId) {
		// 安全校验：验证实体存在且归属于该玩家
		if (!validateOwnership(player, purpleEntityId)) {
			return;
		}
		TRANSITIONS.put(player.getUUID(), new TransitionState(player, purpleEntityId, 4));
	}

	public static void startTransitionToPhase2(ServerPlayer player, int purpleEntityId) {
		// 安全校验：验证实体存在且归属于该玩家
		if (!validateOwnership(player, purpleEntityId)) {
			return;
		}
		TRANSITIONS.put(player.getUUID(), new TransitionState(player, purpleEntityId, 2));
	}

	private static boolean validateOwnership(ServerPlayer player, int purpleEntityId) {
		if (player.level() instanceof ServerLevel serverLevel) {
			Entity entity = serverLevel.getEntity(purpleEntityId);
			if (entity instanceof PurpleMonsterEntity purple) {
				return player.getUUID().equals(purple.getPerformTargetUUID());
			}
		}
		return false;
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

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		for (Map.Entry<UUID, TransitionState> entry : TRANSITIONS.entrySet()) {
			TransitionState state = entry.getValue();
			state.tick++;

			if (!state.purpleRemoved && state.tick >= 5) {
				// 删除紫怪
				Entity purpleEntity = ((ServerLevel) state.player.level()).getEntity(state.purpleEntityId);
				if (purpleEntity != null) {
					purpleEntity.remove(Entity.RemovalReason.DISCARDED);
				}
				state.purpleRemoved = true;
			}

			if (!state.entitiesSpawned && state.tick >= ENTITY_SPAWN_DELAY) {
				ServerLevel level = (ServerLevel) state.player.level();
				double yawRad = Math.toRadians(state.player.getYRot());

				if (state.targetPhase == 4) {
					// 阶段4：生成紫怪和紫狗
					// 紫怪：左前方
					double frontX = -Math.sin(yawRad) * 1.5;
					double frontZ = Math.cos(yawRad) * 1.5;
					double leftX = Math.cos(yawRad) * 1.0;
					double leftZ = Math.sin(yawRad) * 1.0;
					double purpleSpawnX = state.player.getX() + frontX + leftX;
					double purpleSpawnZ = state.player.getZ() + frontZ + leftZ;
					double spawnY = state.player.getY();

					PurpleMonsterEntity purpleMonster = new PurpleMonsterEntity(McanomalyarchivesModEntities.PURPLE_MONSTER.get(), level);
					purpleMonster.setPos(purpleSpawnX, spawnY, purpleSpawnZ);
					purpleMonster.setXRot(0.0F);
					purpleMonster.setYRot(state.player.getYRot() + 180.0F);
					purpleMonster.yBodyRot = state.player.getYRot() + 180.0F;
					purpleMonster.yHeadRot = state.player.getYRot() + 180.0F;
					purpleMonster.setPerformMode(state.player.getUUID());
					purpleMonster.setTextureVariant(1); // purple 纹理
					level.addFreshEntity(purpleMonster);
					state.newPurpleEntityId = purpleMonster.getId();

					// 紫狗：正前方
					double dogX = state.player.getX() - Math.sin(yawRad) * 1.5;
					double dogZ = state.player.getZ() + Math.cos(yawRad) * 1.5;

					PurpleDogEntity purpleDog = new PurpleDogEntity(McanomalyarchivesModEntities.PURPLE_DOG.get(), level);
					purpleDog.setPos(dogX, spawnY, dogZ);
					purpleDog.setXRot(0.0F);
					purpleDog.setYRot(state.player.getYRot() + 180.0F);
					purpleDog.yBodyRot = state.player.getYRot() + 180.0F;
					purpleDog.yHeadRot = state.player.getYRot() + 180.0F;
					purpleDog.setPerformMode(state.player.getUUID());
					level.addFreshEntity(purpleDog);
					state.dogEntityId = purpleDog.getId();
				} else {
					// 阶段2：只生成紫怪在正前方
					double frontX = -Math.sin(yawRad) * 2.0;
					double frontZ = Math.cos(yawRad) * 2.0;
					double spawnX = state.player.getX() + frontX;
					double spawnZ = state.player.getZ() + frontZ;
					double spawnY = state.player.getY();

					PurpleMonsterEntity purpleMonster = new PurpleMonsterEntity(McanomalyarchivesModEntities.PURPLE_MONSTER.get(), level);
					purpleMonster.setPos(spawnX, spawnY, spawnZ);
					purpleMonster.setXRot(0.0F);
					purpleMonster.setYRot(state.player.getYRot() + 180.0F);
					purpleMonster.yBodyRot = state.player.getYRot() + 180.0F;
					purpleMonster.yHeadRot = state.player.getYRot() + 180.0F;
					purpleMonster.setPerformMode(state.player.getUUID());
					purpleMonster.setTextureVariant(1); // purple 纹理
					level.addFreshEntity(purpleMonster);
					state.newPurpleEntityId = purpleMonster.getId();
				}

				state.entitiesSpawned = true;
			}

			if (!state.guiOpened && state.tick >= GUI_OPEN_DELAY) {
				// 打开目标阶段GUI
				PacketDistributor.sendToPlayer(state.player, new OpenPurpleGuiPacket(state.newPurpleEntityId, state.targetPhase, state.dogEntityId));
				state.guiOpened = true;
			}

			if (state.guiOpened) {
				TRANSITIONS.remove(entry.getKey());
			}
		}
	}

	private static class TransitionState {
		final ServerPlayer player;
		final int purpleEntityId;
		final int targetPhase;
		int newPurpleEntityId = 0;
		int dogEntityId = 0;
		int tick = 0;
		boolean purpleRemoved = false;
		boolean entitiesSpawned = false;
		boolean guiOpened = false;

		TransitionState(ServerPlayer player, int purpleEntityId, int targetPhase) {
			this.player = player;
			this.purpleEntityId = purpleEntityId;
			this.targetPhase = targetPhase;
		}
	}
}
