package net.mcreator.mcanomalyarchives.event;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PurpleStalkerHandler {

	// ==================== 可调配置 ====================

	/** 两次窥视之间的间隔（tick）：60~300 秒 */
	private static final int MIN_INTERVAL = 600;
	private static final int MAX_INTERVAL = 2400;

	/** 渐进逼近的距离档位（格）：遭遇次数越多越近 */
	private static final int[] DISTANCE_TIERS = { 30, 20, 12 };
	/** 每多少遭遇次数降一档距离 */
	private static final int SIGHTINGS_PER_TIER = 2;

	/** 生成角度：玩家背后/视野边缘（±90°~±180°） */
	private static final float ANGLE_OFFSET_MIN = 45f;
	private static final float ANGLE_OFFSET_MAX = 45f;

	/** 亮度阈值：低于此亮度视为"夜晚/低亮度"（高概率刷新） */
	private static final int DARK_BRIGHTNESS_THRESHOLD = 8;
	/** 夜晚/低亮度刷新概率 */
	private static final float DARK_SPAWN_CHANCE = 1.0f;
	/** 亮处刷新概率（较低，但不会完全不刷） */
	private static final float BRIGHT_SPAWN_CHANCE = 0.8f;

	/** 场景化生成点的尝试次数（树后/窗外），失败回退随机点 */
	private static final int SCENIC_ATTEMPTS = 12;

	/** 扫描节流：每 20 tick 检查一次是否有活跃窥视者 */
	private static final int SCAN_INTERVAL = 20;

	// ===================================================

	// 每玩家的下次生成时间戳（gameTime）
	private static final Map<UUID, Long> nextSpawnTime = new ConcurrentHashMap<>();

	public static void init() {
		NeoForge.EVENT_BUS.register(new PurpleStalkerHandler());
	}

	@SubscribeEvent
	public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;
		UUID uuid = player.getUUID();
		nextSpawnTime.remove(uuid);
		removeAllStalkers(player.getServer());
	}

	/** 玩家换维度时清理所有窥视者（避免留在旧维度直到超时） */
	@SubscribeEvent
	public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			nextSpawnTime.remove(player.getUUID());
			removeAllStalkers(player.getServer());
		}
	}

	@SubscribeEvent
	public void onServerTick(ServerTickEvent.Post event) {
		// 扫描节流：1 秒一次足够（生成间隔最小 120 秒）
		if (event.getServer().getTickCount() % SCAN_INTERVAL != 0) return;
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			processPlayer(player);
		}
	}

	private void processPlayer(ServerPlayer player) {
		UUID uuid = player.getUUID();

		// 仅生存模式
		if (player.gameMode.isCreative() || player.isSpectator()) return;

		// 检查同化完成标记
		if (!player.getData(McanomalyarchivesModAttachments.PURPLE_ASSIMILATION_COMPLETED)) return;

		// 检查是否已有窥视者存在（全局同时最多一个）
		if (hasAnyActiveStalker(player.getServer())) return;

		// 检查冷却（下次生成时间）
		long currentGameTime = player.level().getGameTime();
		long nextTime = nextSpawnTime.getOrDefault(uuid, 0L);
		if (currentGameTime < nextTime) return;

		// 夜晚/低亮度高概率，亮处低概率（抽不到就顺延一个间隔）
		boolean dark = isDarkArea(player);
		float chance = dark ? DARK_SPAWN_CHANCE : BRIGHT_SPAWN_CHANCE;
		if (player.getRandom().nextFloat() >= chance) {
			nextSpawnTime.put(uuid, currentGameTime + nextInterval(player));
			return;
		}

		// 生成窥视者
		spawnStalker(player);

		// 设置下次生成时间
		nextSpawnTime.put(uuid, currentGameTime + nextInterval(player));
	}

	private long nextInterval(ServerPlayer player) {
		return MIN_INTERVAL + player.getRandom().nextInt(MAX_INTERVAL - MIN_INTERVAL);
	}

	/** 玩家所在位置是否"夜晚/低亮度"（天空+方块光照 < 阈值） */
	private boolean isDarkArea(ServerPlayer player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition()) < DARK_BRIGHTNESS_THRESHOLD;
	}

	/** 按遭遇次数取距离档位：30 → 20 → 12 渐进逼近 */
	private int distanceForSightings(ServerPlayer player) {
		int sightings = player.getData(McanomalyarchivesModAttachments.PURPLE_STALKER_SIGHTINGS);
		int tier = Math.min(sightings / SIGHTINGS_PER_TIER, DISTANCE_TIERS.length - 1);
		return DISTANCE_TIERS[tier];
	}

	/** 全局是否已有窥视者存在（任何维度、任何玩家，同时最多一个） */
	private boolean hasAnyActiveStalker(MinecraftServer server) {
		if (server == null) return false;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof PurpleMonsterEntity pm && pm.isStalkerMode()) {
					return true;
				}
			}
		}
		return false;
	}

	private void spawnStalker(ServerPlayer player) {
		ServerLevel serverLevel = (ServerLevel) player.level();
		int distance = distanceForSightings(player);

		BlockPos spawnPos = pickScenicSpawnPos(player, serverLevel, distance);

		PurpleMonsterEntity stalker = new PurpleMonsterEntity(
			McanomalyarchivesModEntities.PURPLE_MONSTER.get(), serverLevel);
		stalker.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
		stalker.setYRot(player.getYRot());
		stalker.yBodyRot = player.getYRot();
		stalker.yHeadRot = player.getYRot();
		stalker.setTextureVariant(1); // 紫色纹理
		stalker.setStalkerMode(true);
		serverLevel.addFreshEntity(stalker);

		McanomalyarchivesMod.LOGGER.info("Stalker Purple Monster spawned for player {} at ({}, {}, {}), distance={}",
			player.getName().getString(),
			spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
			distance);
	}

	/**
	 * 场景化生成点：优先"树后/窗外"（邻接原木/树叶/玻璃类方块），
	 * 多次尝试失败后回退普通随机点（保证一定会出现）。
	 */
	private BlockPos pickScenicSpawnPos(ServerPlayer player, ServerLevel level, int distance) {
		RandomSource rng = player.getRandom();
		// 优先背后/视野边缘的角度
		for (int attempt = 0; attempt < SCENIC_ATTEMPTS; attempt++) {
			float angleOffset = ANGLE_OFFSET_MIN + rng.nextFloat() * ANGLE_OFFSET_MAX;
			if (rng.nextBoolean()) angleOffset = -angleOffset;
			BlockPos pos = groundPosAt(player, level, distance, player.getYRot() + angleOffset);
			if (pos != null && isScenicSpot(level, pos)) {
				return pos;
			}
		}
		// 回退：任意角度随机点多试几次，尽量避免生成在玩家身上
		for (int i = 0; i < 5; i++) {
			BlockPos fallback = groundPosAt(player, level, distance, rng.nextFloat() * 360f);
			if (fallback != null) return fallback;
		}
		return player.blockPosition();
	}

	/** 按角度和距离算地面坐标（找不到合适地面返回 null） */
	private BlockPos groundPosAt(ServerPlayer player, ServerLevel level, int distance, float angleDeg) {
		double angleRad = Math.toRadians(angleDeg);
		double spawnX = player.getX() + (-Math.sin(angleRad) * distance);
		double spawnZ = player.getZ() + (Math.cos(angleRad) * distance);
		int groundY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,
			BlockPos.containing(spawnX, player.getY(), spawnZ)).getY();
		if (groundY <= level.getMinBuildHeight() + 1) return null;
		BlockPos pos = new BlockPos((int) Math.floor(spawnX), groundY, (int) Math.floor(spawnZ));
		// 只接受"正常"的生成点，避免出现在树上/卡墙里/水上等奇怪位置
		return isValidStalkerSpot(level, pos) ? pos : null;
	}

	/**
	 * 生成点有效性：脚下是实心地面（不是树叶/液体），
	 * 站立点与头顶 2 格可通行（不卡进树冠/墙里）。
	 */
	private boolean isValidStalkerSpot(ServerLevel level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		if (!below.isSolid() || !below.getFluidState().isEmpty() || below.getBlock() instanceof LeavesBlock) {
			return false;
		}
		for (int i = 0; i < 3; i++) {
			BlockState state = level.getBlockState(pos.above(i));
			if (state.isSolid() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** 判断候选点是否"贴景"：邻接原木/树叶（树后），或邻接玻璃/玻璃板（窗外） */
	private boolean isScenicSpot(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockState adj = level.getBlockState(pos.relative(dir));
			if (isTreeCover(adj) || isGlass(adj)) return true;
		}
		// 头顶有树叶也算（树冠下）
		if (level.getBlockState(pos.above()).getBlock() instanceof LeavesBlock) return true;
		return false;
	}

	private boolean isTreeCover(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof LeavesBlock) return true;
		// 原木类：注册名含 log 或树皮类
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
		return path.contains("log") || path.contains("stem");
	}

	private boolean isGlass(BlockState state) {
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.contains("glass");
	}

	/** 跨所有维度清理窥视者（登出/换维度时调用） */
	private void removeAllStalkers(MinecraftServer server) {
		if (server == null) return;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof PurpleMonsterEntity pm && pm.isStalkerMode()) {
					entity.remove(Entity.RemovalReason.DISCARDED);
				}
			}
		}
	}
}
