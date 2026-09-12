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
 * 完整时序（三颗错峰，触发后约 5 秒砸完）：
 * <pre>
 *   t=0.0s  触发（静默，不出声、天上还没东西）
 *   t=1.5s  第 1 颗出现在斜上方 60 格高空，几乎悬停 → 开始加速下坠
 *   t=2.0s  第 2 颗出现      t=2.5s 第 3 颗出现
 *   t=4.0s  第 1 颗落地      t=4.5s / t=5.0s 第 2、3 颗落地
 * </pre>
 *
 * 要点：
 * - 【延迟】触发后先静默 {@link #START_DELAY_TICKS}，给玩家"要出事了"的空白感，
 *   而不是立刻砸脸；三颗之间再各错 {@link #SPAWN_STAGGER_TICKS}。
 * - 【下落过程】每颗用 {@link #FALL_TICKS} 下落，位置按二次曲线缓入：
 *   前段几乎不动（高悬在天上肉眼可见），后段越来越快砸下来 —— 有完整的"下落"而不是瞬移。
 * - 【下落的震颤】只在下落阶段持续震屏，振幅随接近地面递增（0.10 → 0.85），
 *   落地时再来一次 1.6 的大震；延迟期与落地后都不震。
 *   震屏圆心取【落点】而不是火球：火球在 60 格高空，拿它当圆心地面玩家收不到震动。
 * - 【锁定】延迟 + 下落期间落点跟着玩家走（陨石锁定）。总时长约 5 秒，
 *   若落点固定在触发位置，玩家冲刺就能躲开这个"必杀档"。
 * - 冒火石头：大火球 + FLAME/LAVA/LARGE_SMOKE 拖尾 + 2 块岩浆方块伴随坠落（disableDrop）。
 * - 落地不依赖火球自带的 1 级小爆炸：命中事件里取消原版爆炸，由本类统一引爆
 *   （半径 {@link #EXPLOSION_RADIUS} 格 ≥ 要求 10 格，破坏地形，砸出坑）。
 * - 落地给目标玩家结算【保底 {@link #GUARANTEED_DAMAGE} 点】伤害，伤害类型
 *   mcanomalyarchives:meteor_strike 带 bypasses_armor/enchantments/resistance/shield/cooldown 标签，
 *   护甲、保护附魔、抗性药水、盾牌、无敌帧都拦不住。
 */
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
	/** 保底伤害：200 点 */
	public static final float GUARANTEED_DAMAGE = 200.0F;
	/** 保底伤害的生效半径（目标玩家在这个范围内必吃 200） */
	private static final double DAMAGE_RANGE = 30.0;

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

	/** 陨石伤害类型：带 bypasses_* 标签，确保 200 点不被任何减伤吃掉 */
	private static final ResourceKey<DamageType> METEOR_DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "meteor_strike"));

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
		/** 落点相对目标的偏移：每 tick 用它 + 玩家当前位置重算落点（锁定追踪） */
		final double offsetX;
		final double offsetZ;
		/** 当前落点：随玩家移动而更新；玩家没了/换维度就冻结在最后位置 */
		Vec3 target;
		final List<UUID> debris = new ArrayList<>();
		UUID fireball;
		boolean spawned;

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
		}
	}

	private PinkSheepMeteor() {
	}

	// ===== 触发 =====

	/**
	 * 对玩家发动陨石雨：先延迟，再一颗颗从天上砸下来。
	 * 触发时完全静默（最高档灾厄无预警，压迫感交给下落过程本身）。
	 */
	public static void strike(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		for (int i = 0; i < METEOR_COUNT; i++) {
			// 落点：玩家周围随机散布（偏移量记下来，下落期间跟着玩家跑）
			double offsetX = (player.getRandom().nextDouble() - 0.5) * TARGET_SPREAD;
			double offsetZ = (player.getRandom().nextDouble() - 0.5) * TARGET_SPREAD;
			double tx = player.getX() + offsetX;
			double tz = player.getZ() + offsetZ;
			double ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
			Vec3 target = new Vec3(tx, ty, tz);

			// 出现点：斜上方远处 → 下坠轨迹明显是斜的
			double angle = player.getRandom().nextDouble() * Math.PI * 2;
			double dist = SPAWN_DIST_MIN + player.getRandom().nextDouble() * (SPAWN_DIST_MAX - SPAWN_DIST_MIN);
			Vec3 spawn = new Vec3(tx + Math.cos(angle) * dist, ty + SPAWN_HEIGHT, tz + Math.sin(angle) * dist);

			long spawnTick = now + START_DELAY_TICKS + (long) i * SPAWN_STAGGER_TICKS;
			PENDING.add(new PendingMeteor(level, spawn, target, offsetX, offsetZ, player, spawnTick));
		}
	}

	/**
	 * 下落期间重算落点：跟着玩家走（陨石锁定）。
	 * 延迟 + 下落总共约 5 秒，若落点固定在触发位置，玩家冲刺就能躲开这个"必杀档"；
	 * 锁定的代价是轨迹会略微拐弯，观感上就是"追着人砸"。
	 * 玩家死亡/离开/换维度时落点冻结在最后位置，陨石照常砸下。
	 */
	private static void updateTarget(PendingMeteor m) {
		ServerPlayer victim = m.victim;
		if (victim == null || !victim.isAlive() || victim.level() != m.level)
			return;
		double tx = victim.getX() + m.offsetX;
		double tz = victim.getZ() + m.offsetZ;
		double ty = m.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
		m.target = new Vec3(tx, ty, tz);
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
			if (server.getLevel(m.level.dimension()) != m.level) {
				PENDING.remove(m);
				continue;
			}
			long levelTime = m.level.getGameTime();

			// 阶段一：延迟期 —— 天上还没有东西，什么都不做（也不震）
			if (levelTime < m.spawnTick)
				continue;

			// 阶段二：到点出现
			if (!m.spawned) {
				spawnEntities(m);
				m.spawned = true;
			}

			// 阶段三：下落（锁定落点 + 制导 + 拖尾 + 震颤）
			updateTarget(m);
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

	/** 到点让这颗陨石出现在天上：大火球 + 伴随坠落的岩浆块 */
	private static void spawnEntities(PendingMeteor m) {
		double speed = 1.0 / FALL_TICKS;
		Vec3 roughVelocity = m.target.subtract(m.spawn).scale(speed * 0.9);

		var fireball = EntityType.FIREBALL.create(m.level);
		if (fireball != null) {
			fireball.setPos(m.spawn.x, m.spawn.y, m.spawn.z);
			fireball.setDeltaMovement(Vec3.ZERO); // 起步几乎悬停，由 guide() 逐步加速
			fireball.setNoGravity(true);
			fireball.getPersistentData().putBoolean(TAG_METEOR, true);
			m.level.addFreshEntity(fireball);
			m.fireball = fireball.getUUID();
		}

		for (int d = 0; d < 2; d++) {
			var rock = FallingBlockEntity.fall(m.level, BlockPos.containing(m.spawn), Blocks.MAGMA_BLOCK.defaultBlockState());
			rock.disableDrop(); // 只做视觉：落地不留方块、不砸地形
			rock.setPos(m.spawn.x + (m.level.getRandom().nextDouble() - 0.5) * 2.0,
					m.spawn.y + (m.level.getRandom().nextDouble() - 0.5) * 2.0,
					m.spawn.z + (m.level.getRandom().nextDouble() - 0.5) * 2.0);
			rock.setDeltaMovement(roughVelocity.add((m.level.getRandom().nextDouble() - 0.5) * 0.25, 0.0,
					(m.level.getRandom().nextDouble() - 0.5) * 0.25));
			m.level.addFreshEntity(rock);
			m.debris.add(rock.getUUID());
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
			// 缓入进度 p(k)=(k/total)²：本 tick 吃掉「剩余距离」的 factor 比例。
			// 用剩余距离而不是固定直线插值，这样落点在动（锁定追踪）时依然精确命中。
			double p = progress * progress;
			double pNext = Math.pow(Mth.clamp((elapsed + 1.0D) / total, 0.0D, 1.0D), 2.0D);
			double left = 1.0D - p;
			double factor = left <= 1.0E-6D ? 1.0D : (pNext - p) / left;
			fireball.setDeltaMovement(m.target.subtract(fireball.position()).scale(factor));
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
	public static boolean onMeteorImpact(Entity projectile) {
		UUID id = projectile.getUUID();
		for (PendingMeteor m : PENDING) {
			if (id.equals(m.fireball)) {
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

		// 6) 落地大震：附近所有玩家（含创造/旁观，震屏只是观感，不造成伤害）
		PacketDistributor.sendToPlayersNear(level, null, t.x, t.y, t.z, SHAKE_RADIUS,
				new StrangeTreeQuakePacket(IMPACT_SHAKE, IMPACT_SHAKE_TICKS));
	}
}
