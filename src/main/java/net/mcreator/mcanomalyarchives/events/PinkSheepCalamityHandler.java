package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepAdvancements;
import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepCataclysms;
import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepMechanics;
import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepMeteor;
import net.mcreator.mcanomalyarchives.entity.PinkSheepEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UO-012 幸运粉羊 · 距离灾厄（Phase C）。
 *
 * - 靠近羊跨过 10 格 / 8 格 / 3 格阈值 → 各触发一次灾厄（Lv1/2/3）；
 * - 近战攻击羊 → 取消伤害 → 羊转移 + 攻击者吃最高档（Lv3·直接击杀档）；
 * - 远程投射物将命中羊 → 拦截并把投射物弹开 → 羊转移 + 攻击者吃最高档。
 * - 玩家死亡或羊转移后清分档状态，离开 15 格外重新靠近可再次触发。
 */
public class PinkSheepCalamityHandler {

	// 距离阈值
	private static final double RANGE_10 = 10.0;
	private static final double RANGE_8 = 8.0;
	private static final double RANGE_3 = 3.0;
	private static final Map<ServerPlayer, Long> nextCalamity = new java.util.WeakHashMap<>();

	/**
	 * 触发记录：每只羊(identity) → 每玩家UUID → 已触发档位 bitmask（bit1=Lv1, bit2=Lv2, bit3=Lv3）。
	 * 规则：同一只羊（未传送前）对同一玩家每一档只触发一次；羊传送/转移后清空，可重新触发。
	 */
	private static final Map<PinkSheepEntity, Map<UUID, Integer>> triggeredBySheep =
			new java.util.concurrent.ConcurrentHashMap<>();

	public static void init() {
		// 羊转移后清空该羊各档触发记录（新位置可重新触发）——由机制类回调，避免机制包反向依赖事件包
		PinkSheepMechanics.teleportCallback = PinkSheepCalamityHandler::onSheepTeleported;
		NeoForge.EVENT_BUS.register(PinkSheepCalamityHandler.class);
	}

	private static Map<UUID, Integer> triggeredOf(PinkSheepEntity sheep) {
		return triggeredBySheep.computeIfAbsent(sheep, k -> new java.util.concurrent.ConcurrentHashMap<>());
	}

	private static boolean alreadyTriggered(PinkSheepEntity sheep, UUID player, int lvl) {
		int mask = triggeredOf(sheep).getOrDefault(player, 0);
		return (mask & (1 << lvl)) != 0;
	}

	private static void markTriggered(PinkSheepEntity sheep, UUID player, int lvl) {
		triggeredOf(sheep).merge(player, 1 << lvl, (a, b) -> a | b);
	}

	/** 羊被传送/转移后调用：清空该羊所有触发记录（新位置可重新触发） */
	public static void onSheepTeleported(PinkSheepEntity sheep) {
		triggeredBySheep.remove(sheep);
		PinkSheepLookHandler.onSheepTeleported(sheep);
	}
	@SubscribeEvent
	public static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
		triggeredBySheep.clear();
		nextCalamity.clear();
	}

	/** 清理已死亡/移除的羊记录 */
	private static void sweepDead() {
		triggeredBySheep.keySet().removeIf(s -> !s.isAlive() || s.isRemoved());
	}

	public static void onPlayerDeath(UUID uuid) {
		// 玩家死亡清空它对所有羊的记录（下次遭遇可重新触发）
		for (Map<UUID, Integer> m : triggeredBySheep.values()) {
			m.remove(uuid);
		}
	}

	@SubscribeEvent
	public static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() != null) {
			for (Map<UUID, Integer> m : triggeredBySheep.values()) {
				m.remove(event.getEntity().getUUID());
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() != null) {
			for (Map<UUID, Integer> m : triggeredBySheep.values()) {
				m.remove(event.getEntity().getUUID());
			}
		}
	}

	// ===== 距离分档触发 =====
	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		// 在途陨石每 tick 推进（先于 gamerule 判断：已经砸下来的陨石不该因为中途关规则而消失）
		PinkSheepMeteor.tick(event.getServer());
		net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepAccidents.tick(event.getServer());
		PinkSheepCataclysms.tick(event.getServer()); // 末地水晶错峰引爆队列
		ServerLevel level = event.getServer().overworld();
		if (level == null)
			return;
		if (!PinkSheepGameRules.enabled(event.getServer()))
			return;
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			if (player.serverLevel() != level) continue;
			// 只对生存玩家生效：创造/旁观不受灾厄影响
			if (player.gameMode.isCreative() || player.isSpectator())
				continue;
			// 找玩家附近 10 格内所有粉羊
			var nearby = level.getEntitiesOfClass(PinkSheepEntity.class,
					player.getBoundingBox().inflate(RANGE_10 + 1),
					s -> s.isAlive());
			if (nearby.isEmpty()) {
				continue;
			}
			for (PinkSheepEntity sheep : nearby) {
				double dist = Math.sqrt(sheep.distanceToSqr(player));
				// 玩家当前所处档位：只在 10/8/3 最近一个阈值内触发对应档（不跳档补发）
				int lvl = dist <= RANGE_3 ? 3 : dist <= RANGE_8 ? 2 : dist <= RANGE_10 ? 1 : 0;
				if (lvl > 0 && !alreadyTriggered(sheep, player.getUUID(), lvl) && triggerCalamity(player, lvl)) {
					markTriggered(sheep, player.getUUID(), lvl);
				}
			}
		}
		sweepDead();
	}

	// ===== 近战攻击羊 =====
	@SubscribeEvent
	public static void onSheepHurt(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof PinkSheepEntity sheep))
			return;
		if (!(event.getSource().getEntity() instanceof ServerPlayer player))
			return;
		// 创造/旁观玩家可正常攻击击杀（不触发机制）
		if (player.gameMode.isCreative() || player.isSpectator())
			return;
		if (!PinkSheepGameRules.enabled(player.getServer())) return;
		// 生存玩家：取消对羊的伤害（设 0），羊转移 + 攻击者吃最高档
		event.setNewDamage(0);
		// 攻击即触发最高档（每羊每玩家一次；随后羊传送清记录）
		if (!alreadyTriggered(sheep, player.getUUID(), 3) && triggerCalamity(player, 3)) {
			markTriggered(sheep, player.getUUID(), 3);
		}
		PinkSheepMechanics.teleportAway(sheep);
	}

	// ===== 远程投射物将命中羊 → 弹开 =====
	@SubscribeEvent
	public static void onProjectileImpact(ProjectileImpactEvent event) {
		HitResult hit = event.getRayTraceResult();
		Projectile projectile = event.getProjectile();
		// 陨石（大火球）撞到地形/实体：取消原版 1 级小爆炸，立刻按陨石规则引爆（12 格 + 保底 200）
		if (PinkSheepMeteor.isMeteor(projectile)) {
			event.setCanceled(true);
			PinkSheepMeteor.onMeteorImpact(projectile, hit.getLocation());
			return;
		}
		if (net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepAccidents.onArrowImpact(projectile, hit)) {
			event.setCanceled(true);
			return;
		}
		if (!(hit instanceof net.minecraft.world.phys.EntityHitResult entityHit))
			return;
		if (!(entityHit.getEntity() instanceof PinkSheepEntity sheep))
			return;
		if (!(projectile.getOwner() instanceof ServerPlayer player))
			return;
		// 创造/旁观玩家可正常射杀（不触发机制）
		if (player.gameMode.isCreative() || player.isSpectator())
			return;
		if (!PinkSheepGameRules.enabled(player.getServer())) return;
		// 取消命中（箭头继续飞但需人为偏转，否则穿过）——弹开：
		event.setCanceled(true);
		Vec3 vel = projectile.getDeltaMovement();
		// 反向加随机偏转，模拟"莫名弹开"
		projectile.setDeltaMovement(vel.scale(-0.4).add(
				(player.getRandom().nextDouble() - 0.5) * 0.6,
				0.3 + player.getRandom().nextDouble() * 0.4,
				(player.getRandom().nextDouble() - 0.5) * 0.6));
		projectile.setNoGravity(false);
		// 攻击即触发最高档；随后羊传送清记录
		if (!alreadyTriggered(sheep, player.getUUID(), 3) && triggerCalamity(player, 3)) {
			markTriggered(sheep, player.getUUID(), 3);
		}
		PinkSheepMechanics.teleportAway(sheep);
	}

	/** Scene selection happens before consuming the distance tier. */
	public static boolean triggerCalamity(ServerPlayer player, int severity) {
		if (severity < 1 || severity > 3 || player == null || !player.isAlive()
				|| !PinkSheepGameRules.enabled(player.getServer())) return false;
		long now = player.getServer().getTickCount();
		if (now < nextCalamity.getOrDefault(player, Long.MIN_VALUE)) return false;
		boolean started = net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepAccidents.start(player, severity);
		nextCalamity.put(player, now + (started ? 200 : 40));
		if (!started) return false;
		net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "sheep_calamity");
		if (severity == 3) PinkSheepAdvancements.grantCalamity(player);
		return true;
	}
}
