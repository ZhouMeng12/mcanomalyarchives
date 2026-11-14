package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepAdvancements;
import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepMechanics;
import net.mcreator.mcanomalyarchives.entity.PinkSheepEntity;
import net.mcreator.mcanomalyarchives.network.PlayerBlinkPacket;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Direct observation is settled after every player; only the absence of all observers allows relocation. */
public class PinkSheepLookHandler {

	public static final double LOOK_RANGE = 24.0;
	public static final double LOOK_ANGLE_DOT = 0.966;   // ~15°
	public static final int BLINK_TICKS = 900;
	public static final int SIGHT_TICKS = 40;

	/** 目击确认回调（Phase B 注册）：参数 (watcher, sheep) */
	public static BiConsumer<ServerPlayer, PinkSheepEntity> encounterCallback = (p, s) -> {};

	private static final net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepObservation observation =
			new net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepObservation();
	private static final java.util.Map<UUID, Long> heldSince = new java.util.HashMap<>();
	private static final java.util.Map<UUID, Long> sharedBlinkAt = new java.util.HashMap<>();
	private static final java.util.Map<UUID, Set<UUID>> blinkWitnesses = new java.util.HashMap<>();
	public static void onSheepTeleported(PinkSheepEntity sheep) {
		observation.resetSheep(sheep.getUUID());
		heldSince.remove(sheep.getUUID());
		sharedBlinkAt.remove(sheep.getUUID());
	}

	/** 当前世界活跃的粉羊（唯一性下数量极少；Join 加入、死亡/移除清理） */
	private static final Set<PinkSheepEntity> activeSheep = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static void init() {
		NeoForge.EVENT_BUS.register(PinkSheepLookHandler.class);
	}

	@SubscribeEvent
	public static void onPlayerQuit(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() != null) {
			observation.forgetPlayer(event.getEntity().getUUID());
		}
	}

	/** 区域唯一：新粉羊加入世界时，若 128 格内已存在另一只活粉羊，则取消（防自然刷出多只） */
	@SubscribeEvent
	public static void onSheepJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
		if (!(event.getEntity() instanceof PinkSheepEntity sheep))
			return;
		if (sheep.level().isClientSide)
			return;
		ServerLevel level = (ServerLevel) sheep.level();
		var others = level.getEntitiesOfClass(PinkSheepEntity.class,
				new net.minecraft.world.phys.AABB(sheep.getX() - 128, level.getMinBuildHeight(),
						sheep.getZ() - 128, sheep.getX() + 128, level.getMaxBuildHeight(), sheep.getZ() + 128));
		for (PinkSheepEntity other : others) {
			if (other != sheep && other.isAlive()) {
				event.setCanceled(true);
				return;
			}
		}
		// 通过去重 → 记入活跃集合，并进入出生游荡期
		activeSheep.add(sheep);
		PinkSheepMechanics.onSpawnWander(sheep);
	}

	/** 实体离开世界 → 丢弃其瞬时状态（WeakHashMap 兜底，这里显式清理） */
	@SubscribeEvent
	public static void onSheepLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
		if (event.getEntity() instanceof PinkSheepEntity sheep) {
			activeSheep.remove(sheep);
			onSheepTeleported(sheep);
			PinkSheepMechanics.forget(sheep);
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		ServerLevel level = event.getServer().overworld();
		if (level == null)
			return;
		if (!PinkSheepGameRules.enabled(event.getServer())) {
			observation.clear();
			heldSince.clear();
			sharedBlinkAt.clear();
			return;
		}
		observation.beginTick();
		blinkWitnesses.clear();
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (player.serverLevel() == level) {
				tickPlayer(player, level);
			} else {
				observation.forgetPlayer(player.getUUID());
			}
		}
		// 结算：本 tick 被看的羊保持存在；任何未被注视的羊 → 立即消失（观察者效应强规则）。
		// 同时跳过"附近有创造/旁观玩家"的羊（当作普通羊）。
		activeSheep.removeIf(s -> !s.isAlive() || s.isRemoved());
		for (PinkSheepEntity sheep : activeSheep) {
			if (!sheep.isAlive() || sheep.level() != level)
				continue;
			// 创造/旁观玩家 32 格内 → 普通羊
			boolean creativeNearby = false;
			for (Player p : sheep.level().players()) {
				if (p.distanceToSqr(sheep) < 32.0 * 32.0
						&& p instanceof net.minecraft.server.level.ServerPlayer sp
						&& (sp.gameMode.isCreative() || sp.isSpectator())) {
					creativeNearby = true;
					break;
				}
			}
			if (creativeNearby)
				continue;
			long now = level.getGameTime();
			UUID sheepId = sheep.getUUID();
			boolean watched = observation.watched(sheepId);
			if (watched) {
				heldSince.putIfAbsent(sheepId, now);
				if (now >= sharedBlinkAt.getOrDefault(sheepId, Long.MAX_VALUE)) {
					for (UUID watcher : observation.observers(sheepId)) {
						ServerPlayer observer = level.getServer().getPlayerList().getPlayer(watcher);
						if (observer != null) PacketDistributor.sendToPlayer(observer, new PlayerBlinkPacket());
						blinkWitnesses.computeIfAbsent(sheepId, k -> new HashSet<>()).add(watcher);
					}
					observation.closeAllEyes(sheepId, now);
					watched = false;
				} else if (now - heldSince.get(sheepId) >= 3600 && now % 20 == 0
						&& !sharedBlinkAt.containsKey(sheepId) && sheep.getRandom().nextInt(120) == 0) {
					sharedBlinkAt.put(sheepId, now + 40);
					for (UUID watcher : observation.observers(sheepId)) {
						ServerPlayer observer = level.getServer().getPlayerList().getPlayer(watcher);
						if (observer != null) observer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
								"message.mcanomalyarchives.sheep_tired_eyes"), true);
					}
				}
			}
			if (watched) {
				PinkSheepMechanics.setWatched(sheep, true);
				PinkSheepMechanics.markWatchedOnce(sheep); // 被注视过 → 之后严格观察者效应
			} else {
				heldSince.remove(sheepId);
				sharedBlinkAt.remove(sheepId);
				PinkSheepMechanics.setWatched(sheep, false);
				// 无人注视：游荡/宽限期不消失；已"被看过"的羊则立即消失转移
				if (!PinkSheepMechanics.shouldPersistWithoutWatcher(sheep)) {
					for (UUID watcher : blinkWitnesses.getOrDefault(sheepId, Set.of())) {
						ServerPlayer observer = level.getServer().getPlayerList().getPlayer(watcher);
						if (observer != null) PinkSheepAdvancements.grantBlink(observer);
					}
					PinkSheepMechanics.teleportAway(sheep);
				}
			}
		}
	}

	private static void tickPlayer(ServerPlayer player, ServerLevel level) {
		if (!player.isAlive() || player.gameMode.isCreative() || player.isSpectator()) {
			observation.forgetPlayer(player.getUUID());
			return;
		}
		PinkSheepEntity target = level.getEntitiesOfClass(PinkSheepEntity.class,
				player.getBoundingBox().inflate(LOOK_RANGE), s -> s.isAlive() && isPlayerLookingAt(player, s))
				.stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
		var result = observation.observe(player.getUUID(), target == null ? null : target.getUUID(),
				level.getGameTime(), BLINK_TICKS + Math.floorMod(player.getUUID().hashCode(), 201));
		if (result == net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepObservation.Result.FIRST_SIGHT)
			encounterCallback.accept(player, target);
		if (result == net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepObservation.Result.BLINK) {
			PacketDistributor.sendToPlayer(player, new PlayerBlinkPacket());
			blinkWitnesses.computeIfAbsent(target.getUUID(), k -> new HashSet<>()).add(player.getUUID());
		}
	}

	public static boolean isBeingWatched(PinkSheepEntity sheep) {
		return observation.watched(sheep.getUUID());
	}

	/** 玩家视线是否朝向羊（角度 + 视线） */
	public static boolean isPlayerLookingAt(ServerPlayer player, PinkSheepEntity sheep) {
		if (player.level() != sheep.level() || player.distanceToSqr(sheep) > LOOK_RANGE * LOOK_RANGE) return false;
		Vec3 eye = player.getEyePosition(1.0F);
		Vec3 center = sheep.position().add(0, sheep.getBbHeight() * 0.5, 0);
		Vec3 toSheep = center.subtract(eye).normalize();
		Vec3 look = player.getViewVector(1.0F).normalize();
		double dot = look.dot(toSheep);
		return dot > LOOK_ANGLE_DOT && player.hasLineOfSight(sheep);
	}

	@SubscribeEvent
	public static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
		observation.clear();
		heldSince.clear();
		sharedBlinkAt.clear();
		activeSheep.clear();
		blinkWitnesses.clear();
	}
}
