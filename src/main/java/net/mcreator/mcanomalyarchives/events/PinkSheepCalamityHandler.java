package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepAdvancements;
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
 * - 靠近羊跨过 10 格 / 6 格 / 3 格阈值 → 各触发一次灾厄（Lv1/2/3）；
 * - 近战攻击羊 → 取消伤害 → 羊转移 + 攻击者吃最高档（Lv3·直接击杀档）；
 * - 远程投射物将命中羊 → 拦截并把投射物弹开 → 羊转移 + 攻击者吃最高档。
 * - 玩家死亡或羊转移后清分档状态，离开 15 格外重新靠近可再次触发。
 */
public class PinkSheepCalamityHandler {

	// 距离阈值
	private static final double RANGE_10 = 10.0;
	private static final double RANGE_6 = 6.0;
	private static final double RANGE_3 = 3.0;

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
		ServerLevel level = event.getServer().overworld();
		if (level == null)
			return;
		if (!PinkSheepGameRules.enabled(event.getServer()))
			return;
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
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
				// 玩家当前所处档位：只在 10/6/3 最近一个阈值内触发对应档（不跳档补发）
				int lvl = dist <= RANGE_3 ? 3 : dist <= RANGE_6 ? 2 : dist <= RANGE_10 ? 1 : 0;
				if (lvl > 0 && !alreadyTriggered(sheep, player.getUUID(), lvl)) {
					triggerCalamity(player, lvl);
					markTriggered(sheep, player.getUUID(), lvl);
				}
			}
			// 清理已死羊记录
			sweepDead();
		}
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
		// 生存玩家：取消对羊的伤害（设 0），羊转移 + 攻击者吃最高档
		event.setNewDamage(0);
		// 攻击即触发最高档（每羊每玩家一次；随后羊传送清记录）
		if (!alreadyTriggered(sheep, player.getUUID(), 3)) {
			triggerCalamity(player, 3);
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
			PinkSheepMeteor.onMeteorImpact(projectile);
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
		if (!alreadyTriggered(sheep, player.getUUID(), 3)) {
			triggerCalamity(player, 3);
			markTriggered(sheep, player.getUUID(), 3);
		}
		PinkSheepMechanics.teleportAway(sheep);
	}

	// ===== 灾厄执行 =====
	/** @param level 1=10格小惩罚 2=6格严重 3=3格或攻击(最高·无预警直接击杀) */
	public static void triggerCalamity(ServerPlayer player, int level) {
		if (player == null || !player.isAlive())
			return;
		ServerLevel levelWorld = player.serverLevel();
		switch (level) {
			case 1 -> {
				// 小惩罚：天降TNT/闪电 或 铁装僵尸骷髅小队
				if (player.getRandom().nextBoolean()) {
					skyTntOrLightning(player, levelWorld, false);
				} else {
					spawnArmedSquad(player, levelWorld);
				}
			}
			case 2 -> {
				// 严重警告：凋零 / Warden(尖啸钻出) / 附魔合金鸡骑士
				double r = player.getRandom().nextDouble();
				if (r < 0.35) {
					spawnWither(player, levelWorld);
				} else if (r < 0.7) {
					spawnWardenEmerging(player, levelWorld);
				} else {
					spawnChickenJockey(player, levelWorld);
				}
			}
			case 3 -> {
				// 成就：陨石问候（惹出最高档灾厄；近战/远程攻击触发也走这里）
				PinkSheepAdvancements.grantCalamity(player);
				// 最高档（直接击杀，无预警）：陨石 或 天基屠龙炮
				if (player.getRandom().nextBoolean()) {
					meteorStrike(player, levelWorld);
				} else {
					orbitalArrowRain(player, levelWorld);
				}
			}
		}
	}

	// ---- Lv1 ----
	private static void skyTntOrLightning(ServerPlayer player, ServerLevel level, boolean big) {
		double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 4;
		double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 4;
		if (player.getRandom().nextBoolean()) {
			var bolt = EntityType.LIGHTNING_BOLT.create(level);
			if (bolt != null) {
				bolt.moveTo(x, player.getY(), z);
				level.addFreshEntity(bolt);
			}
		} else {
			var tnt = EntityType.TNT.create(level);
			if (tnt != null) {
				tnt.moveTo(x, player.getY() + 12, z);
				tnt.setFuse(40);
				level.addFreshEntity(tnt);
			}
		}
	}

	private static void spawnArmedSquad(ServerPlayer player, ServerLevel level) {
		for (int i = 0; i < 3; i++) {
			Mob mob;
			if (player.getRandom().nextBoolean()) {
				Zombie z = EntityType.ZOMBIE.create(level);
				if (z == null) continue;
				z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob = z;
			} else {
				Skeleton sk = EntityType.SKELETON.create(level);
				if (sk == null) continue;
				sk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				mob = sk;
			}
			spawnNearPlayer(player, level, mob);
			mob.setTarget(player);
		}
	}

	// ---- Lv2 ----
	private static void spawnWither(ServerPlayer player, ServerLevel level) {
		WitherBoss wither = EntityType.WITHER.create(level);
		if (wither == null) return;
		spawnNearPlayer(player, level, wither);
		wither.setTarget(player);
	}

	private static void spawnWardenEmerging(ServerPlayer player, ServerLevel level) {
		// 尖啸预警 0.5s
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 2.0F, 1.0F);
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 40, 2, 2, 2, 0.1);
		Warden warden = EntityType.WARDEN.create(level);
		if (warden == null) return;
		Vec3 pos = player.position().add(player.getLookAngle().scale(-3));
		warden.moveTo(pos.x, player.getY(), pos.z);
		warden.setPose(Pose.EMERGING);
		level.addFreshEntity(warden);
		warden.setTarget(player);
	}

	private static void spawnChickenJockey(ServerPlayer player, ServerLevel level) {
		// 小僵尸骑鸡 + 速度 II + 附魔下界合金剑套
		var chicken = EntityType.CHICKEN.create(level);
		Zombie zombie = EntityType.ZOMBIE.create(level);
		if (chicken == null || zombie == null) return;
		// 小僵尸
		zombie.setBaby(true);
		// 下界合金剑（玩家级威胁由武器+速度II实现，附魔表查询留待后续）
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
		zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
		zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 1)); // 速度 II
		chicken.moveTo(player.getX(), player.getY(), player.getZ());
		zombie.moveTo(player.getX(), player.getY(), player.getZ());
		level.addFreshEntity(chicken);
		level.addFreshEntity(zombie);
		zombie.startRiding(chicken);
		zombie.setTarget(player);
	}

	// ---- Lv3（直接击杀档）----
	/**
	 * 陨石雨：3 颗冒火石头从斜上方砸向玩家，落地 12 格爆炸 + 保底 200 伤害 + 附近震屏。
	 * 具体实现见 {@link net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepMeteor}。
	 */
	private static void meteorStrike(ServerPlayer player, ServerLevel level) {
		PinkSheepMeteor.strike(player, level);
	}

	private static void orbitalArrowRain(ServerPlayer player, ServerLevel level) {
		// 天基屠龙炮：单支超高速箭，从玩家头顶瞬落，几乎立即命中。
		// 伤害 = 速度 × baseDamage = 6 × 20 = 120（≥100），靠高速体现"天基炮"冲击。
		var arrow = EntityType.ARROW.create(level);
		if (arrow == null) return;
		double sx = player.getX() + (player.getRandom().nextDouble() - 0.5) * 2;
		double sz = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 2;
		double sy = player.getY() + 6 + player.getRandom().nextDouble() * 4; // 头顶 6~10 格
		arrow.setPos(sx, sy, sz);
		Vec3 dir = new Vec3(player.getX() - sx, (player.getY() + 1.2) - sy, player.getZ() - sz).normalize();
		arrow.setDeltaMovement(dir.scale(6.0));   // 超高速
		arrow.setNoGravity(true);                  // 无重力直线瞬落
		arrow.setBaseDamage(20.0);                 // ×6 速度 = 120 伤害
		level.addFreshEntity(arrow);
	}

	// ===== 工具 =====
	private static void spawnNearPlayer(ServerPlayer player, ServerLevel level, Mob mob) {
		double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 6;
		double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 6;
		mob.moveTo(x, player.getY() + 0.1, z, player.getRandom().nextFloat() * 360, 0);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
	}
}
