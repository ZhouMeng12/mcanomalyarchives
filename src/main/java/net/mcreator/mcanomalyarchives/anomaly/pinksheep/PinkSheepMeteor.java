package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PinkSheepEntity;
import net.mcreator.mcanomalyarchives.network.StrangeTreeQuakePacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UO-012 幸运粉羊 · 最高档灾厄「陨石雨」。
 *
 * 【工程层归属】本类位于 anomaly 包（非 MCreator 生成区），事件接线在 PinkSheepCalamityHandler。
 *
 * 实现要点：
 * - 3 颗陨石从斜上方 22~32 格外、55 格高空生成，各自瞄准玩家附近地面，
 *   速度 = 位移/飞行 tick 数 → 轨迹是明确的斜线（不是垂直落体）；
 * - 陨石本体用大火球（原版火球就是"冒火石头"的观感），沿途洒 LAVA/FLAME/LARGE_SMOKE 拖尾，
 *   并伴随 2 块岩浆方块坠落（disableDrop，只做视觉，落地不留方块）；
 * - 落地不依赖火球自带的 1 级小爆炸：命中事件里取消原版爆炸，由本类统一引爆
 *   （半径 12 格 ≥ 要求 10 格，破坏地形，砸出坑）；
 * - 落地瞬间给目标玩家结算【保底 200 点】伤害，伤害类型 mcanomalyarchives:meteor_strike
 *   带 bypasses_armor/enchantments/resistance/shield/cooldown 标签，护甲、保护附魔、
 *   抗性药水、盾牌、无敌帧都拦不住这 200 点；
 * - 64 格内玩家一起震屏（复用怪树那套 StrangeTreeQuakePacket + CameraShakeMixin）。
 *
 * 时序：每颗陨石按自己的飞行时间错峰落地（第 i 颗 30 + 8i tick），等待期间由
 * {@link #tick(MinecraftServer)} 每 tick 推进拖尾与落地判定。
 */
public final class PinkSheepMeteor {

	/** 陨石数量（陨石雨） */
	private static final int METEOR_COUNT = 3;
	/** 生成高度（玩家所在 y 之上） */
	private static final double SPAWN_HEIGHT = 55.0;
	/** 生成点水平偏移：越远越斜 */
	private static final double SPAWN_DIST_MIN = 22.0;
	private static final double SPAWN_DIST_MAX = 32.0;
	/** 第一颗的飞行时间（tick），后续每颗 +STAGGER_TICKS 错峰 */
	private static final int BASE_FLIGHT_TICKS = 30;
	private static final int STAGGER_TICKS = 8;
	/** 每颗陨石的坠落方向重复落点散布（格） */
	private static final double TARGET_SPREAD = 8.0;

	/** 爆炸半径：要求 ≥10，这里取 12 格（破坏地形） */
	public static final float EXPLOSION_RADIUS = 12.0F;
	/** 保底伤害：200 点 */
	public static final float GUARANTEED_DAMAGE = 200.0F;
	/** 保底伤害的生效半径（目标玩家在这个范围内必吃 200） */
	private static final double DAMAGE_RANGE = 30.0;
	/** 震屏广播半径 */
	private static final double SHAKE_RADIUS = 64.0;

	/** 标记：这颗火球是陨石（命中时不走原版小爆炸，改由本类引爆） */
	private static final String TAG_METEOR = "McanomalyMeteor";

	/** 陨石伤害类型：带 bypasses_* 标签，确保 200 点不被任何减伤吃掉 */
	private static final ResourceKey<DamageType> METEOR_DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "meteor_strike"));

	/** 落地前把羊挪走的安全边距（免得羊被自家陨石炸死，遭遇直接结束） */
	private static final double SHEEP_SAFE_MARGIN = 8.0;

	private static final List<PendingMeteor> PENDING = new CopyOnWriteArrayList<>();

	/** 一颗在途陨石 */
	private static final class PendingMeteor {
		final ServerLevel level;
		final Vec3 target;
		final ServerPlayer victim;
		final long detonateTick;
		final UUID fireball;
		final List<UUID> debris;

		PendingMeteor(ServerLevel level, Vec3 target, ServerPlayer victim, long detonateTick, UUID fireball,
				List<UUID> debris) {
			this.level = level;
			this.target = target;
			this.victim = victim;
			this.detonateTick = detonateTick;
			this.fireball = fireball;
			this.debris = debris;
		}
	}

	private PinkSheepMeteor() {
	}

	// ===== 触发 =====

	/** 对玩家发动陨石雨（最高档灾厄，无预警：生成时不出声，落地才是"交代"） */
	public static void strike(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		for (int i = 0; i < METEOR_COUNT; i++) {
			int flight = BASE_FLIGHT_TICKS + i * STAGGER_TICKS;
			// 落点：玩家周围随机散布
			double tx = player.getX() + (player.getRandom().nextDouble() - 0.5) * TARGET_SPREAD;
			double tz = player.getZ() + (player.getRandom().nextDouble() - 0.5) * TARGET_SPREAD;
			double ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
			Vec3 target = new Vec3(tx, ty, tz);

			// 起点：斜上方远处 → 轨迹明显是斜着砸下来
			double angle = player.getRandom().nextDouble() * Math.PI * 2;
			double dist = SPAWN_DIST_MIN + player.getRandom().nextDouble() * (SPAWN_DIST_MAX - SPAWN_DIST_MIN);
			Vec3 spawn = new Vec3(tx + Math.cos(angle) * dist, ty + SPAWN_HEIGHT,
					tz + Math.sin(angle) * dist);
			// 初速只是种子值（朝落点的匀速直线）；实际飞行由 guide() 每 tick 修正弹道
			Vec3 velocity = target.subtract(spawn).scale(1.0 / flight);

			var fireball = EntityType.FIREBALL.create(level);
			if (fireball == null)
				continue;
			fireball.setPos(spawn.x, spawn.y, spawn.z);
			fireball.setDeltaMovement(velocity);
			fireball.setNoGravity(true);
			fireball.getPersistentData().putBoolean(TAG_METEOR, true);
			level.addFreshEntity(fireball);

			// 冒火石头：伴随坠落的岩浆方块（只做视觉，落地不留方块）
			List<UUID> debris = new ArrayList<>();
			for (int d = 0; d < 2; d++) {
				var rock = FallingBlockEntity.fall(level, BlockPos.containing(spawn), Blocks.MAGMA_BLOCK.defaultBlockState());
				rock.disableDrop();
				rock.setPos(spawn.x + (player.getRandom().nextDouble() - 0.5) * 2.0,
						spawn.y + (player.getRandom().nextDouble() - 0.5) * 2.0,
						spawn.z + (player.getRandom().nextDouble() - 0.5) * 2.0);
				rock.setDeltaMovement(velocity.add((player.getRandom().nextDouble() - 0.5) * 0.25,
						(player.getRandom().nextDouble() - 0.5) * 0.15,
						(player.getRandom().nextDouble() - 0.5) * 0.25));
				level.addFreshEntity(rock);
				debris.add(rock.getUUID());
			}

			PENDING.add(new PendingMeteor(level, target, player, now + flight, fireball.getUUID(), debris));
		}
	}

	// ===== 每 tick 推进 =====

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty())
			return;
		for (PendingMeteor m : PENDING) {
			// 世界已卸载（例如服务器换图）：就地丢弃
			if (server.getLevel(m.level.dimension()) != m.level) {
				PENDING.remove(m);
				continue;
			}
			guide(m);
			long remaining = m.detonateTick - m.level.getGameTime();
			// 到达预定 tick，或已经贴到落点（提前撞上地形/实体）→ 引爆
			if (remaining <= 0 || tooClose(m)) {
				PENDING.remove(m);
				detonate(m);
			}
		}
	}

	/** 是否已经贴到落点（贴着地面时不必再等，避免火球先撞地穿帮） */
	private static boolean tooClose(PendingMeteor m) {
		Entity fireball = m.level.getEntities().get(m.fireball);
		return fireball != null && fireball.position().distanceToSqr(m.target) <= 2.25D; // 1.5 格
	}

	/**
	 * 每 tick 制导：把速度重设为「剩余位移 / 剩余 tick」。
	 *
	 * 为什么不一开始算好速度就撒手：火球有阻力（每 tick 掉速），纯直线预测会越飞越短，
	 * 到预定时刻火球还在半空 —— 就会出现"空中凭空爆炸"的穿帮。每 tick 修正一次，
	 * 既精确命中落点，也自动补偿阻力，轨迹仍然是斜着砸下来。
	 */
	private static void guide(PendingMeteor m) {
		Entity fireball = m.level.getEntities().get(m.fireball);
		if (fireball == null)
			return;
		long remaining = m.detonateTick - m.level.getGameTime();
		if (remaining > 0) {
			fireball.setDeltaMovement(m.target.subtract(fireball.position()).scale(1.0D / remaining));
		}
		m.level.sendParticles(ParticleTypes.FLAME, fireball.getX(), fireball.getY(), fireball.getZ(), 10, 0.45, 0.45,
				0.45, 0.02);
		m.level.sendParticles(ParticleTypes.LAVA, fireball.getX(), fireball.getY(), fireball.getZ(), 3, 0.3, 0.3, 0.3,
				0.0);
		m.level.sendParticles(ParticleTypes.LARGE_SMOKE, fireball.getX(), fireball.getY(), fireball.getZ(), 5, 0.5, 0.5,
				0.5, 0.01);
	}

	// ===== 命中拦截（供 PinkSheepCalamityHandler 的投射物事件调用）=====

	/** 这颗投射物是不是本系统发射的陨石 */
	public static boolean isMeteor(Entity entity) {
		return entity instanceof Fireball && entity.getPersistentData().getBoolean(TAG_METEOR);
	}

	/**
	 * 陨石提前撞到地形：取消原版 1 级小爆炸，立刻按陨石规则引爆。
	 *
	 * @return true = 已接管（调用方应取消该次命中）
	 */
	public static boolean onMeteorImpact(Entity projectile) {
		UUID id = projectile.getUUID();
		for (PendingMeteor m : PENDING) {
			if (m.fireball.equals(id)) {
				PENDING.remove(m);
				detonate(m);
				return true;
			}
		}
		// 已经引爆过（或在途记录被清掉）：残留的火球直接抹掉，避免它在别处再炸一次
		projectile.discard();
		return true;
	}

	// ===== 引爆 =====

	/**
	 * 陨石伤害来源：自定义类型 mcanomalyarchives:meteor_strike（死亡信息是"被陨石砸成了坑"）。
	 * 若该伤害类型资源被覆盖或丢失，退回原版 generic_kill（同样无视护甲与抗性提升），
	 * 绝不让服务器崩在"查不到伤害类型"这一步。
	 */
	private static DamageSource meteorSource(ServerLevel level) {
		return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(METEOR_DAMAGE_TYPE)
				.<DamageSource>map(DamageSource::new)
				.orElseGet(() -> level.damageSources().genericKill());
	}

	private static void detonate(PendingMeteor m) {
		ServerLevel level = m.level;
		Vec3 t = m.target;

		// 1) 清掉在途实体：火球别在别处再炸一次，岩浆块也别留下
		Entity fireball = level.getEntities().get(m.fireball);
		if (fireball != null)
			fireball.discard();
		for (UUID id : m.debris) {
			Entity rock = level.getEntities().get(id);
			if (rock != null)
				rock.discard();
		}

		// 2) 把爆炸范围内的粉羊先挪走：最高档灾厄不该把自己的本体炸没
		for (PinkSheepEntity sheep : level.getEntitiesOfClass(PinkSheepEntity.class,
				new net.minecraft.world.phys.AABB(t.x - (EXPLOSION_RADIUS + SHEEP_SAFE_MARGIN), level.getMinBuildHeight(),
						t.z - (EXPLOSION_RADIUS + SHEEP_SAFE_MARGIN), t.x + (EXPLOSION_RADIUS + SHEEP_SAFE_MARGIN),
						level.getMaxBuildHeight(), t.z + (EXPLOSION_RADIUS + SHEEP_SAFE_MARGIN)))) {
			if (sheep.isAlive())
				PinkSheepMechanics.teleportAway(sheep);
		}

		// 3) 大范围爆炸：半径 12 格，破坏地形（砸出陨石坑）
		ServerPlayer victim = m.victim;
		if (victim != null && victim.isAlive())
			victim.invulnerableTime = 0;                       // 别让无敌帧吃掉爆炸伤害
		level.explode(null, t.x, t.y, t.z, EXPLOSION_RADIUS, true, Level.ExplosionInteraction.TNT);

		// 4) 保底 200 点：爆炸之后再结算一次（爆炸没打死/被图腾挡住也必定补上）
		if (victim != null && victim.isAlive() && victim.level() == level
				&& victim.distanceToSqr(t.x, t.y, t.z) <= DAMAGE_RANGE * DAMAGE_RANGE) {
			victim.invulnerableTime = 0;
			victim.hurt(meteorSource(level), GUARANTEED_DAMAGE);
		}

		// 5) 演出：火球爆闪 + 岩浆四溅 + 双重巨响
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, t.x, t.y + 0.5, t.z, 3, 2.5, 0.5, 2.5, 0.0);
		level.sendParticles(ParticleTypes.LAVA, t.x, t.y + 0.5, t.z, 80, 4.0, 1.5, 4.0, 0.7);
		level.sendParticles(ParticleTypes.FLAME, t.x, t.y + 0.5, t.z, 120, 5.0, 2.0, 5.0, 0.15);
		level.playSound(null, t.x, t.y, t.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.55F);
		level.playSound(null, t.x, t.y, t.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F, 0.5F);

		// 6) 震动：附近所有玩家（含创造/旁观，震屏只是观感，不造成伤害）
		PacketDistributor.sendToPlayersNear(level, null, t.x, t.y, t.z, SHAKE_RADIUS,
				new StrangeTreeQuakePacket(1.6F, 45));
	}
}
