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

/**
 * UO-012 幸运粉羊 · 观察者效应 / 目击确认状态机。
 *
 * 规则：
 * - 羊被注视时维持存在；无任何注视者超 UNWATCHED_TICKS → 消散转移到 ≥15 格外。
 * - 玩家连续凝视满 BLINK_TICKS → 眨眼包 + 羊转移（"眨眼瞬间消失"）。
 * - 目击确认（注视 ≥ SIGHT_TICKS）→ encounterCallback（Phase B 发幸运），同 encounter 一次，
 *   羊转移后重置（新羊新 id 自然重置）。
 *
 * 设计：以羊为键维护每个"玩家→凝视 tick"；被看得最久的那只羊推进状态机。
 * 简化且正确：同一时刻只处理"正被看"的羊（lookHandler 按需扫描玩家周围）。
 */
public class PinkSheepLookHandler {

	public static final double LOOK_RANGE = 24.0;
	public static final double LOOK_ANGLE_DOT = 0.966;   // ~15°
	public static final int UNWATCHED_TICKS = 120;        // 移开视线 6s 才消散
	public static final int BLINK_TICKS = 900;            // 凝视 45s → 眨眼（久一点）
	public static final int SIGHT_TICKS = 40;             // 目击确认 2s

	/** 目击确认回调（Phase B 注册）：参数 (watcher, sheep) */
	public static BiConsumer<ServerPlayer, PinkSheepEntity> encounterCallback = (p, s) -> {};

	/** 玩家当前凝视的羊（以实体对象为键，identity 语义） */
	private static final java.util.Map<UUID, PinkSheepEntity> lookingAtSheep = new java.util.concurrent.ConcurrentHashMap<>();
	/** 玩家对当前羊的累计凝视 tick */
	private static final java.util.Map<UUID, Integer> lookTicks = new java.util.concurrent.ConcurrentHashMap<>();
	/** 该羊本 encounter 是否已发幸运（identity 集合，羊对象转移/重生后自然失效） */
	private static final Set<PinkSheepEntity> encounterGranted = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** 本 tick 被注视过的羊（结算 watched） */
	private static final Set<PinkSheepEntity> watchedThisTick = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** 当前世界活跃的粉羊（唯一性下数量极少；Join 加入、死亡/移除清理） */
	private static final Set<PinkSheepEntity> activeSheep = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static void init() {
		NeoForge.EVENT_BUS.register(PinkSheepLookHandler.class);
	}

	@SubscribeEvent
	public static void onPlayerQuit(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() != null) {
			lookingAtSheep.remove(event.getEntity().getUUID());
			lookTicks.remove(event.getEntity().getUUID());
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
			PinkSheepMechanics.forget(sheep);
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		ServerLevel level = event.getServer().overworld();
		if (level == null)
			return;
		if (!PinkSheepGameRules.enabled(event.getServer()))
			return;
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			tickPlayer(player, level);
		}
		// 结算：本 tick 被看的羊保持存在；任何未被注视的羊 → 立即消失（观察者效应强规则）。
		// 同时跳过"附近有创造/旁观玩家"的羊（当作普通羊）。
		activeSheep.removeIf(s -> !s.isAlive() || s.isRemoved());
		for (PinkSheepEntity sheep : activeSheep) {
			if (!sheep.isAlive())
				continue;
			// 创造/旁观玩家 32 格内 → 普通羊
			boolean creativeNearby = false;
			for (Player p : level.players()) {
				if (p.distanceToSqr(sheep) < 32.0 * 32.0
						&& p instanceof net.minecraft.server.level.ServerPlayer sp
						&& (sp.gameMode.isCreative() || sp.isSpectator())) {
					creativeNearby = true;
					break;
				}
			}
			if (creativeNearby)
				continue;
			boolean watched = watchedThisTick.contains(sheep) || isBeingWatched(sheep);
			if (watched) {
				PinkSheepMechanics.setWatched(sheep, true);
				PinkSheepMechanics.markWatchedOnce(sheep); // 被注视过 → 之后严格观察者效应
			} else {
				PinkSheepMechanics.setWatched(sheep, false);
				// 无人注视：游荡/宽限期不消失；已"被看过"的羊则立即消失转移
				if (!PinkSheepMechanics.shouldPersistWithoutWatcher(sheep)) {
					PinkSheepMechanics.teleportAway(sheep);
				}
			}
		}
		watchedThisTick.clear();
	}

	private static void tickPlayer(ServerPlayer player, ServerLevel level) {
		// 只对生存玩家生效：创造/旁观玩家视为"不存在于观察者效应中"
		if (player.gameMode.isCreative() || player.isSpectator()) {
			lookingAtSheep.remove(player.getUUID());
			lookTicks.remove(player.getUUID());
			return;
		}
		// 找玩家周围 24 格内所有粉羊
		AABB box = player.getBoundingBox().inflate(LOOK_RANGE);
		java.util.List<PinkSheepEntity> nearby = level.getEntitiesOfClass(PinkSheepEntity.class, box);
		PinkSheepEntity target = null;
		for (PinkSheepEntity sheep : nearby) {
			if (isPlayerLookingAt(player, sheep)) {
				target = sheep;
				break;
			}
		}

		PinkSheepEntity prev = lookingAtSheep.get(player.getUUID());
		if (target != null) {
			lookingAtSheep.put(player.getUUID(), target);
			// 换目标则重置计时
			if (prev != target) {
				lookTicks.put(player.getUUID(), 0);
			}
			int ticks = lookTicks.merge(player.getUUID(), 1, Integer::sum);

			// 目击确认 → 发幸运（每 encounter 一次）
			if (!encounterGranted.contains(target) && ticks >= SIGHT_TICKS) {
				encounterGranted.add(target);
				encounterCallback.accept(player, target);
			}
			// 凝视满 → 眨眼转移
			if (ticks >= BLINK_TICKS) {
				PacketDistributor.sendToPlayer(player, new PlayerBlinkPacket());
				PinkSheepAdvancements.grantBlink(player); // 成就：眨眼之间
				lookingAtSheep.remove(player.getUUID());
				lookTicks.remove(player.getUUID());
				encounterGranted.remove(target);
				PinkSheepMechanics.setWatched(target, false);
				PinkSheepMechanics.teleportAway(target);
			} else {
				watchedThisTick.add(target);
			}
		} else {
			// 没在看任何羊
			lookingAtSheep.remove(player.getUUID());
			lookTicks.remove(player.getUUID());
		}
	}

	/** 羊是否正被某玩家注视（供实体判断维持） */
	public static boolean isBeingWatched(PinkSheepEntity sheep) {
		return lookingAtSheep.containsValue(sheep);
	}

	/** 玩家视线是否朝向羊（角度 + 视线） */
	public static boolean isPlayerLookingAt(ServerPlayer player, PinkSheepEntity sheep) {
		Vec3 eye = player.getEyePosition(1.0F);
		Vec3 center = sheep.position().add(0, sheep.getBbHeight() * 0.5, 0);
		Vec3 toSheep = center.subtract(eye).normalize();
		Vec3 look = player.getViewVector(1.0F).normalize();
		double dot = look.dot(toSheep);
		return dot > LOOK_ANGLE_DOT && player.hasLineOfSight(sheep);
	}
}
