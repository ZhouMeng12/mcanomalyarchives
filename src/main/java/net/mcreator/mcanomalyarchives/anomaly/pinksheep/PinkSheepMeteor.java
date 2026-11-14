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

/** Visible meteors follow fixed routes; actual collisions determine explosion positions and damage. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "mcanomalyarchives")
public final class PinkSheepMeteor {

	/** 陨石数量（陨石雨） */
	private static final int METEOR_COUNT = 3;
	/** 触发后的静默延迟：1.5 秒内什么都不发生（延迟感） */
	private static final int START_DELAY_TICKS = 30;
	/** 三颗陨石出现的间隔（第 i 颗 = 延迟 + i × 这个值） */
	private static final int SPAWN_STAGGER_TICKS = 10;
	/** 单颗陨石的下落时长：2.5 秒的可见下坠过程 */
	private static final int FALL_TICKS = 50;

	/** 出现高度（玩家所在 y 之上）：够高才能看清整条下坠轨迹 */
	private static final double SPAWN_HEIGHT = 60.0;
	/** 水平偏移：越远轨迹越斜 */
	private static final double SPAWN_DIST_MIN = 22.0;
	private static final double SPAWN_DIST_MAX = 32.0;
	/** 落点相对玩家的散布（格） */
	private static final double TARGET_SPREAD = 8.0;

	/** 爆炸半径：要求 ≥10，这里取 12 格（破坏地形） */
	public static final float EXPLOSION_RADIUS = 12.0F;
	/** 下落期震屏：起始 / 落地前振幅，以及刷新间隔（tick） */
	private static final float FALL_SHAKE_MIN = 0.10F;
	private static final float FALL_SHAKE_MAX = 0.85F;
	private static final int FALL_SHAKE_INTERVAL = 2;
	/** 落地大震振幅与时长 */
	private static final float IMPACT_SHAKE = 1.6F;
	private static final int IMPACT_SHAKE_TICKS = 45;
	/** 震屏广播半径（以落点为圆心，保证地面玩家全程都吃得到震颤） */
	private static final double SHAKE_RADIUS = 80.0;

	/** 标记：这颗火球是陨石（命中时不走原版小爆炸，改由本类引爆） */
	private static final String TAG_METEOR = "McanomalyMeteor";

	/** 落地前把羊挪走的安全边距（免得羊被自家陨石炸死，遭遇直接结束） */
	private static final double SHEEP_SAFE_MARGIN = 8.0;

	private static final List<PendingMeteor> PENDING = new CopyOnWriteArrayList<>();

	/**
	 * 一颗陨石的行程单：先等 {@code spawnTick} 出现，再花 {@code FALL_TICKS} 落下来。
	 * 火球实体是到点才创建的（延迟期天上什么都没有）。
	 */
	private static final class PendingMeteor {
		final ServerLevel level;
		final Vec3 spawn;
		final ServerPlayer victim;
		final long spawnTick;
		final long impactTick;
		/** Initial scatter, sampled once. */
		final double offsetX;
		final double offsetZ;
		/** Intended endpoint; replaced only by the actual collision position during detonation. */
		Vec3 target;
		final List<UUID> debris = new ArrayList<>();
		UUID fireball;
		boolean spawned;
		boolean cancelled;
		final PinkSheepMeteorFlight flight;

		PendingMeteor(ServerLevel level, Vec3 spawn, Vec3 target, double offsetX, double offsetZ, ServerPlayer victim,
				long spawnTick) {
			this.level = level;
			this.spawn = spawn;
			this.target = target;
			this.offsetX = offsetX;
			this.offsetZ = offsetZ;
			this.victim = victim;
			this.spawnTick = spawnTick;
			this.impactTick = spawnTick + FALL_TICKS;
			this.flight = new PinkSheepMeteorFlight(spawn, target, FALL_TICKS);
		}
	}

	private PinkSheepMeteor() {
	}

	// ===== 触发 =====

	/**
	 * 对玩家发动陨石雨：先延迟，再一颗颗从天上砸下来。
	 * 触发时完全静默（最高档灾厄无预警，压迫感交给下落过程本身）。
	 */
	public static boolean canStrike(ServerPlayer player, ServerLevel level) {
		return level.hasChunkAt(player.blockPosition()) && level.canSeeSky(player.blockPosition())
				&& player.getY() + 24 < level.getMaxBuildHeight();
	}

	public static boolean strike(ServerPlayer player, ServerLevel level) {
		if (!canStrike(player, level)) return false;
		int created = 0;
		long now = level.getGameTime();
		for (int i = 0; i < METEOR_COUNT; i++) {
			// Snapshot the initial aim; subsequent player movement does not steer the meteor.
			double offsetX = (player.getRandom().nextDouble() - 0.5) * TARGET_SPREAD;
			double offsetZ = (player.getRandom().nextDouble() - 0.5) * TARGET_SPREAD;
			double tx = player.getX() + offsetX;
			double tz = player.getZ() + offsetZ;
			if (!level.hasChunkAt(BlockPos.containing(tx, player.getY(), tz))) continue;
			double ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
			Vec3 target = new Vec3(tx, ty, tz);

			// 出现点：斜上方远处 → 下坠轨迹明显是斜的
			double angle = player.getRandom().nextDouble() * Math.PI * 2;
			double dist = SPAWN_DIST_MIN + player.getRandom().nextDouble() * (SPAWN_DIST_MAX - SPAWN_DIST_MIN);
			Vec3 spawn = new Vec3(tx + Math.cos(angle) * dist, Math.min(ty + SPAWN_HEIGHT, level.getMaxBuildHeight() - 2), tz + Math.sin(angle) * dist);

			if (spawn.y - ty < 20 || !PinkSheepAccidents.loaded(level, spawn, target) || !blastLoaded(level, target)
					|| !level.getBlockState(BlockPos.containing(spawn)).isAir()) continue;
			long spawnTick = now + START_DELAY_TICKS + (long) i * SPAWN_STAGGER_TICKS;
			PENDING.add(new PendingMeteor(level, spawn, target, offsetX, offsetZ, player, spawnTick));
			created++;
		}
		return created > 0;
	}

	// ===== 每 tick 推进 =====

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty())
			return;
		float maxShake = 0.0F;
		Vec3 shakeAt = null;
		ServerLevel shakeLevel = null;
		for (PendingMeteor m : PENDING) {
			// 世界已卸载（例如服务器换图）：就地丢弃
			if (m.cancelled || server.getLevel(m.level.dimension()) != m.level) {
				cancel(m);
				continue;
			}
			long levelTime = m.level.getGameTime();
			if (!m.spawned && (!net.mcreator.mcanomalyarchives.events.PinkSheepGameRules.enabled(server)
					|| !m.victim.isAlive() || m.victim.isRemoved() || m.victim.level() != m.level
					|| m.victim.isCreative() || m.victim.isSpectator()
					|| server.getPlayerList().getPlayer(m.victim.getUUID()) != m.victim)) {
				cancel(m); continue;
			}

			// 阶段一：延迟期 —— 天上还没有东西，什么都不做（也不震）
			if (levelTime < m.spawnTick)
				continue;

			// 阶段二：到点出现
			if (!m.spawned) {
				if (!PinkSheepAccidents.loaded(m.level, m.spawn, m.target)
						|| !m.level.getBlockState(BlockPos.containing(m.spawn)).isAir()) { cancel(m); continue; }
				spawnEntities(m);
				m.spawned = true;
			}

			// Fixed route, visible trail and local shake.
			Entity active = m.fireball == null ? null : m.level.getEntity(m.fireball);
			if (active == null || !active.isAlive() || active.isRemoved()
					|| !PinkSheepAccidents.loaded(m.level, active.position(), m.flight.position(levelTime - m.spawnTick + 1))) {
				cancel(m); continue;
			}
			float shake = guide(m, levelTime);
			if (shake > maxShake) {
				maxShake = shake;
				// 以【落点】为圆心，不能用火球位置：火球在 60 格高空，
				// 拿它当圆心的话地面玩家距离约 66 格，开局阶段直接收不到震动。
				shakeAt = m.target;
				shakeLevel = m.level;
			}

			// 阶段四：落地（到点，或已经贴到落点 / 提前撞上地形）
			if (levelTime >= m.impactTick || tooClose(m)) {
				PENDING.remove(m);
				m.target = active.position();
				detonate(m);
			}
		}

		// 下落期的持续震颤：每 FALL_SHAKE_INTERVAL tick 刷新一次，振幅随接近地面变大
		if (maxShake > 0.0F && shakeLevel != null && shakeAt != null
				&& shakeLevel.getGameTime() % FALL_SHAKE_INTERVAL == 0) {
			PacketDistributor.sendToPlayersNear(shakeLevel, null, shakeAt.x, shakeAt.y, shakeAt.z, SHAKE_RADIUS,
					new StrangeTreeQuakePacket(maxShake, FALL_SHAKE_INTERVAL * 6));
		}
	}

	/** Create the visible projectile; never alter a block at the spawn location. */
	private static void spawnEntities(PendingMeteor m) {

		var fireball = EntityType.FIREBALL.create(m.level);
		if (fireball != null) {
			fireball.setPos(m.spawn.x, m.spawn.y, m.spawn.z);
			fireball.setDeltaMovement(Vec3.ZERO); // 起步几乎悬停，由 guide() 逐步加速
			fireball.setNoGravity(true);
			fireball.getPersistentData().putBoolean(TAG_METEOR, true);
			if (m.level.addFreshEntity(fireball)) m.fireball = fireball.getUUID();
		}

	}

	/** 是否已经贴到落点（贴着地面时不必再等，避免火球先撞地穿帮） */
	private static boolean tooClose(PendingMeteor m) {
		if (m.fireball == null)
			return false;
		Entity fireball = m.level.getEntities().get(m.fireball);
		return fireball != null && fireball.position().distanceToSqr(m.target) <= 2.25D; // 1.5 格
	}

	/**
	 * 每 tick 制导：位置沿二次曲线缓入（前段慢、后段快）。
	 *
	 * <p>进度 p(k) = (已下落 tick / 总下落 tick)²，下一 tick 的目标位置就是
	 * 起点 +（落点−起点）× p(k+1)，速度取差值。这样：
	 * 开头几 tick 几乎悬停在天上（看得见的那颗"冒火石头"），越接近地面越快，
	 * 而且必定在预定 tick 精确砸到落点（p(total)=1）。
	 * 顺带自动补偿火球自身的阻力，不会出现"预定时刻还在半空"的空中爆炸穿帮。
	 *
	 * @return 本 tick 应施加的震屏振幅（0 = 不震）
	 */
	private static float guide(PendingMeteor m, long levelTime) {
		double total = (double) FALL_TICKS;
		double elapsed = (double) (levelTime - m.spawnTick);
		double progress = Mth.clamp(elapsed / total, 0.0D, 1.0D);

		Entity fireball = m.fireball == null ? null : m.level.getEntities().get(m.fireball);
		if (fireball != null) {
			fireball.setDeltaMovement(m.flight.velocity(fireball.position(), levelTime - m.spawnTick));
			m.level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
					Blocks.MAGMA_BLOCK.defaultBlockState()), fireball.getX(), fireball.getY(), fireball.getZ(),
					6, .5, .5, .5, .01);
			// 拖尾：一路冒火，从天上看就是一条火线
			m.level.sendParticles(ParticleTypes.FLAME, fireball.getX(), fireball.getY(), fireball.getZ(), 12, 0.5, 0.5,
					0.5, 0.02);
			m.level.sendParticles(ParticleTypes.LAVA, fireball.getX(), fireball.getY(), fireball.getZ(), 4, 0.3, 0.3,
					0.3, 0.0);
			m.level.sendParticles(ParticleTypes.LARGE_SMOKE, fireball.getX(), fireball.getY(), fireball.getZ(), 6, 0.6,
					0.6, 0.6, 0.01);
		}

		// 震颤随下坠加深：0.10 → 0.85（越近越猛）
		return FALL_SHAKE_MIN + (FALL_SHAKE_MAX - FALL_SHAKE_MIN) * (float) (progress * progress);
	}

	// ===== 命中拦截（供 PinkSheepCalamityHandler 的投射物事件调用）=====

	/** 这颗投射物是不是本系统发射的陨石 */
	public static boolean isMeteor(Entity entity) {
		return entity instanceof Fireball && entity.getPersistentData().getBoolean(TAG_METEOR);
	}

	/**
	 * 陨石提前撞到地形/实体：取消原版 1 级小爆炸，立刻按陨石规则引爆。
	 *
	 * @return true = 已接管（调用方应取消该次命中）
	 */
	public static boolean onMeteorImpact(Entity projectile, Vec3 hitLocation) {
		UUID id = projectile.getUUID();
		for (PendingMeteor m : PENDING) {
			if (id.equals(m.fireball)) {
				PENDING.remove(m);
				m.target = hitLocation;
				detonate(m);
				return true;
			}
		}
		// 已经引爆过（或在途记录被清掉）：残留的火球直接抹掉，避免它在别处再炸一次
		projectile.discard();
		return true;
	}

	// ===== 引爆 =====

	private static void detonate(PendingMeteor m) {
		ServerLevel level = m.level;
		Vec3 t = m.target;

		// 1) 清掉在途实体：火球别在别处再炸一次，岩浆块也别留下
		if (m.fireball != null) {
			Entity fireball = level.getEntities().get(m.fireball);
			if (fireball != null)
				fireball.discard();
		}
		for (UUID id : m.debris) {
			Entity rock = level.getEntities().get(id);
			if (rock != null)
				rock.discard();
		}

		if (!blastLoaded(level, t)) return;

		// 2) 把爆炸范围内的粉羊先挪走：最高档灾厄不该把自己的本体炸没
		for (PinkSheepEntity sheep : level.getEntitiesOfClass(PinkSheepEntity.class,
				new net.minecraft.world.phys.AABB(t.x - (EXPLOSION_RADIUS + SHEEP_SAFE_MARGIN), level.getMinBuildHeight(),
						t.z - (EXPLOSION_RADIUS + SHEEP_SAFE_MARGIN), t.x + (EXPLOSION_RADIUS + SHEEP_SAFE_MARGIN),
						level.getMaxBuildHeight(), t.z + (EXPLOSION_RADIUS + SHEEP_SAFE_MARGIN)))) {
			if (sheep.isAlive())
				PinkSheepMechanics.teleportAway(sheep);
		}

		// 3) 大范围爆炸：半径 12 格，破坏地形（砸出陨石坑）
		//    服务端世界规则可关掉地形破坏（只保留伤害与演出），不受客户端舒适度影响
		Level.ExplosionInteraction interaction = net.mcreator.mcanomalyarchives.config.ServerGameplayConfig
				.meteorTerrainDamage() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
		level.explode(null, t.x, t.y, t.z, EXPLOSION_RADIUS, interaction == Level.ExplosionInteraction.TNT,
				interaction);

		// 5) 演出：火球爆闪 + 岩浆四溅 + 双重巨响
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, t.x, t.y + 0.5, t.z, 3, 2.5, 0.5, 2.5, 0.0);
		level.sendParticles(ParticleTypes.LAVA, t.x, t.y + 0.5, t.z, 80, 4.0, 1.5, 4.0, 0.7);
		level.sendParticles(ParticleTypes.FLAME, t.x, t.y + 0.5, t.z, 120, 5.0, 2.0, 5.0, 0.15);
		level.playSound(null, t.x, t.y, t.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.55F);
		level.playSound(null, t.x, t.y, t.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F, 0.5F);

		// 6) 落地大震：附近所有玩家（含创造/旁观，震屏只是观感，不造成伤害）
		PacketDistributor.sendToPlayersNear(level, null, t.x, t.y, t.z, SHAKE_RADIUS,
				new StrangeTreeQuakePacket(IMPACT_SHAKE, IMPACT_SHAKE_TICKS));
	}

	private static boolean blastLoaded(ServerLevel level, Vec3 center) {
		for (int dx = -32; dx <= 32; dx += 8) for (int dz = -32; dz <= 32; dz += 8)
			if (!level.hasChunkAt(BlockPos.containing(center.x + dx, center.y, center.z + dz))) return false;
		return true;
	}
	private static void cancel(PendingMeteor m) {
		if (m.fireball != null) { Entity entity = m.level.getEntity(m.fireball); if (entity != null) entity.discard(); }
		for (UUID id : m.debris) { Entity entity = m.level.getEntity(id); if (entity != null) entity.discard(); }
		PENDING.remove(m);
	}
	@net.neoforged.bus.api.SubscribeEvent
	public static void onDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
		for (PendingMeteor m : PENDING) if (m.victim == event.getEntity() && !m.spawned) m.cancelled = true;
	}
	@net.neoforged.bus.api.SubscribeEvent
	public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
		for (PendingMeteor m : List.copyOf(PENDING)) cancel(m);
	}
}
