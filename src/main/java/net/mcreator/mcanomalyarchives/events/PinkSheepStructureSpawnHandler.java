package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepHabitat;
import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepMechanics;
import net.mcreator.mcanomalyarchives.entity.PinkSheepEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UO-012 幸运粉羊 · 结构式一次性生成器（替代生物自然刷怪）。
 *
 * 行为：
 * - 只会在"平原/温和开阔群系 + 周围平坦(7x7 高差≤1)"的点生成；
 * - 不以生物刷怪方式周期性出现；生成器按每个玩家维护"该玩家区域是否已存在羊"，
 *   区域内没有羊且找到合适平原点时，羊以"浮现演出"出现（像结构被遇到）；
 * - 羊消失/转移后，若该玩家区域再次无羊，生成器会重新寻找新平原点投放（可再遇）；
 * - 128 格区域唯一（配合 EntityJoinLevelEvent 去重）。
 *
 * 【工程层】本类位于 events 包（自建、非 MCreator 生成区）。选点/群系/平坦规则已外移到
 * {@link PinkSheepHabitat}，本类只负责事件接线与生成演出。
 */
public class PinkSheepStructureSpawnHandler {

	/** 生成器生效时，粉羊在世界中的最大总量（防无限增殖；唯一性已限制局部，这里兜底） */
	private static final int MAX_TOTAL = 3;

	/** 玩家附近这个半径内有羊就不再生成 */
	private static final double NEAR_PLAYER_RADIUS = 200.0;

	/** 单次尝试的随机落点次数 */
	private static final int SPAWN_ATTEMPTS = 24;

	/** 每个玩家的生成节流（避免一进世界立刻刷） */
	private static final java.util.Map<UUID, Long> nextScanByPlayer = new ConcurrentHashMap<>();
	private static final long SCAN_INTERVAL = 200; // 10s

	public static void init() {
		NeoForge.EVENT_BUS.register(PinkSheepStructureSpawnHandler.class);
	}

	/** 禁止粉羊以"生物自然刷怪"方式出现（改由结构式生成器手动投放） */
	@SubscribeEvent
	public static void onSpawnPlacement(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck event) {
		if (event.getEntityType() != McanomalyarchivesModEntities.PINK_SHEEP.get())
			return;
		MobSpawnType type = event.getSpawnType();
		// 自然刷怪/区块刷怪/结构生成等都拦掉；保留 BUCKET/SPAWN_EGG(刷怪蛋)/COMMAND(召唤)以便测试
		if (type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION
				|| type == MobSpawnType.STRUCTURE || type == MobSpawnType.REINFORCEMENT
				|| type == MobSpawnType.MOB_SUMMONED) {
			event.setResult(
					net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		ServerLevel level = event.getServer().overworld();
		if (level == null)
			return;
		if (!PinkSheepGameRules.enabled(event.getServer()))
			return;
		// 世界总量兜底（唯一性已限制局部，这里防无限增殖）
		if (PinkSheepHabitat.countSheep(level) >= MAX_TOTAL)
			return;

		long now = level.getGameTime();
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (player.gameMode.isCreative() || player.isSpectator())
				continue;
			Long next = nextScanByPlayer.get(player.getUUID());
			if (next != null && now < next)
				continue;
			nextScanByPlayer.put(player.getUUID(), now + SCAN_INTERVAL);
			// 玩家附近是否已有羊
			if (PinkSheepHabitat.hasSheepNear(level, player.getX(), player.getZ(), NEAR_PLAYER_RADIUS))
				continue;
			trySpawnFor(player, level);
		}
	}

	private static void trySpawnFor(ServerPlayer player, ServerLevel level) {
		var rng = player.getRandom();
		Vec3 spot = PinkSheepHabitat.findSpot(level, player.getX(), player.getZ(), PinkSheepHabitat.SPAWN_MIN_DIST,
				PinkSheepHabitat.SPAWN_MAX_DIST, Heightmap.Types.WORLD_SURFACE, SPAWN_ATTEMPTS, rng, null);
		if (spot == null)
			return;

		PinkSheepEntity sheep = McanomalyarchivesModEntities.PINK_SHEEP.get().create(level);
		if (sheep == null)
			return;
		sheep.moveTo(spot.x, spot.y, spot.z, rng.nextFloat() * 360f, 0);
		sheep.setPersistenceRequired();
		PinkSheepMechanics.onSpawnWander(sheep); // 出生游荡期：等待玩家发现

		// 浮现演出
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, spot.x, spot.y, spot.z, 40, 1.2, 0.3,
				1.2, 0.05);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, spot.x, spot.y + 0.7, spot.z, 12,
				0.6, 0.4, 0.6, 0.1);
		level.playSound(null, BlockPos.containing(spot), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.HOSTILE, 1.2F,
				0.9F);
		level.addFreshEntity(sheep);
	}
}
