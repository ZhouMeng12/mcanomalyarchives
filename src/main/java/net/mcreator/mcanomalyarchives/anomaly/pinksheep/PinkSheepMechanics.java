package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.mcreator.mcanomalyarchives.entity.PinkSheepEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * UO-012 幸运粉羊 · 观察者效应状态机与转移行为。
 *
 * 【工程层归属】本类位于 anomaly 包，不属于 MCreator 生成区：
 * MCreator 重新生成 PinkSheep 元素时不会触碰本文件，粉羊的全部机制逻辑都保留在这里。
 * 生成区的 {@code PinkSheepEntity} 只剩构造/属性/两个防 NPE 覆写，被覆盖后重贴壳即可。
 *
 * 状态存在本类的 WeakHashMap 里（键是实体实例，实体被 GC 后条目自动消失），
 * 因此实体类里不需要任何自定义字段 —— 这也是"薄壳"能成立的前提。
 * 状态是瞬时的（宽限/游荡/注视），存档重载后会由
 * PinkSheepLookHandler#onSheepJoin 重新进入出生游荡期，无需持久化。
 */
public final class PinkSheepMechanics {

	/** 转移后宽限 tick（2 秒内不因无人注视被再次移除，避免无限乱跳） */
	public static final int GRACE_AFTER_TELEPORT = 40;
	/** 游荡期 tick：刚出现/刚转移时给玩家的发现窗口（600 = 30s） */
	public static final int WANDER_TICKS = 600;
	/** 转移落点尝试次数 */
	private static final int TELEPORT_ATTEMPTS = 96;

	/** 转移回调：由 PinkSheepCalamityHandler 注册，用于清空该羊的各档灾厄触发记录 */
	public static Consumer<PinkSheepEntity> teleportCallback = sheep -> {
	};

	private static final Map<PinkSheepEntity, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

	/** 每只羊的瞬时状态 */
	public static final class State {
		public boolean watched;
		public int graceTicks;
		public int wanderTicks;
		public boolean everWatched;
	}

	private PinkSheepMechanics() {
	}

	private static State stateOf(PinkSheepEntity sheep) {
		synchronized (STATES) {
			return STATES.computeIfAbsent(sheep, key -> new State());
		}
	}

	// ===== 状态查询 / 修改 =====

	public static boolean isWatched(PinkSheepEntity sheep) {
		synchronized (STATES) {
			State s = STATES.get(sheep);
			return s != null && s.watched;
		}
	}

	public static void setWatched(PinkSheepEntity sheep, boolean watched) {
		stateOf(sheep).watched = watched;
	}

	/** 一旦被注视过 → 游荡期结束，转为严格观察者效应 */
	public static void markWatchedOnce(PinkSheepEntity sheep) {
		stateOf(sheep).everWatched = true;
	}

	/** 进入/重置游荡期（出生、转移后调用） */
	public static void startWanderPeriod(PinkSheepEntity sheep) {
		State s = stateOf(sheep);
		s.wanderTicks = WANDER_TICKS;
		s.everWatched = false;
	}

	/** 出生即进入游荡期（等待玩家发现），保证野外能自然遇到 */
	public static void onSpawnWander(PinkSheepEntity sheep) {
		startWanderPeriod(sheep);
	}

	/** 是否处于转移后宽限期（每次调用递减 1 tick） */
	public static boolean isInGracePeriod(PinkSheepEntity sheep) {
		State s = stateOf(sheep);
		if (s.graceTicks > 0) {
			s.graceTicks--;
			return true;
		}
		return false;
	}

	/** 当前是否应因"无人注视"而继续存在（false = 允许立即消失；每次调用递减游荡计时） */
	public static boolean shouldPersistWithoutWatcher(PinkSheepEntity sheep) {
		if (isInGracePeriod(sheep))
			return true;
		State s = stateOf(sheep);
		if (!s.everWatched && s.wanderTicks > 0) {
			s.wanderTicks--;
			return true;
		}
		return false;
	}

	/** 剩余游荡 tick（调试/自检用） */
	public static int wanderTicksLeft(PinkSheepEntity sheep) {
		synchronized (STATES) {
			State s = STATES.get(sheep);
			return s == null ? 0 : s.wanderTicks;
		}
	}

	/** 实体被移除时丢弃状态（WeakHashMap 兜底，这里做显式清理） */
	public static void forget(PinkSheepEntity sheep) {
		STATES.remove(sheep);
	}

	// ===== 行为 =====

	/**
	 * 消散并转移到最近玩家 40~80 格外的合法落点，播"消失→浮现"演出。
	 * 找不到合法落点则原地弃置（discard），避免卡在玩家面前。
	 */
	public static void teleportAway(PinkSheepEntity sheep) {
		if (!(sheep.level() instanceof ServerLevel level))
			return;
		Player nearest = level.getNearestPlayer(sheep, 64.0);
		double baseX = nearest != null ? nearest.getX() : sheep.getX();
		double baseZ = nearest != null ? nearest.getZ() : sheep.getZ();
		Vec3 target = PinkSheepHabitat.findSpot(level, baseX, baseZ, PinkSheepHabitat.TELEPORT_MIN_DIST,
				PinkSheepHabitat.TELEPORT_MAX_DIST, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, TELEPORT_ATTEMPTS,
				sheep.getRandom(), sheep);
		if (target == null) {
			forget(sheep);
			sheep.discard();
			return;
		}
		// 消散演出
		level.sendParticles(ParticleTypes.SCULK_SOUL, sheep.getX(), sheep.getY() + 0.6, sheep.getZ(), 24, 0.4, 0.4, 0.4,
				0.02);
		sheep.playSound(SoundEvents.SCULK_BLOCK_SPREAD, 1.0F, 1.0F);

		sheep.teleportTo(target.x, target.y, target.z);

		State s = stateOf(sheep);
		s.watched = false;
		s.graceTicks = GRACE_AFTER_TELEPORT; // 转移后宽限，避免无限乱跳
		startWanderPeriod(sheep);            // 转移后重新进入游荡期（等待再次被发现）

		// 新位置对该玩家可重新触发各档灾厄
		teleportCallback.accept(sheep);

		// 出现演出
		level.sendParticles(ParticleTypes.SCULK_SOUL, sheep.getX(), sheep.getY() + 0.6, sheep.getZ(), 24, 0.4, 0.4, 0.4,
				0.02);
		sheep.playSound(SoundEvents.SCULK_CATALYST_BLOOM, 1.0F, 1.0F);
	}
}
