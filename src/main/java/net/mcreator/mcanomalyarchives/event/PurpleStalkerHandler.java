package net.mcreator.mcanomalyarchives.event;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStalkerWorld;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

public class PurpleStalkerHandler {

	// ==================== 可调配置 ====================

	/** 两次窥视之间的间隔（tick）：3~6 分钟 */
	private static final int MIN_INTERVAL = 3600;
	private static final int MAX_INTERVAL = 7200;

	/** 渐进逼近的距离档位（格）：遭遇次数越多越近 */
	private static final int[] DISTANCE_TIERS = { 30, 20, 12 };
	/** 每多少遭遇次数降一档距离 */
	private static final int SIGHTINGS_PER_TIER = 2;

	/** 生成角度：视野边缘（±70°~±130°），之后再检查所有观察者视线。 */
	private static final float ANGLE_OFFSET_MIN = 70f;
	private static final float ANGLE_OFFSET_MAX = 60f;

	/** 亮度阈值：低于此亮度视为"夜晚/低亮度"（高概率刷新） */
	private static final int DARK_BRIGHTNESS_THRESHOLD = 8;
	/** 夜晚/低亮度刷新概率 */
	private static final float DARK_SPAWN_CHANCE = 1.0f;
	/** 亮处刷新概率（较低，但不会完全不刷） */
	private static final float BRIGHT_SPAWN_CHANCE = 0.2f;

	/** 场景化生成点的尝试次数（树后/窗外），失败回退随机点 */
	private static final int SCENIC_ATTEMPTS = 12;

	/** 扫描节流：每 20 tick 检查一次是否有活跃窥视者 */
	private static final int SCAN_INTERVAL = 20;

	// ===================================================

	// 轮转扫描游标；玩家冷却保存在附件中，重登不会立即补刷。
	private static int playerCursor;
	private static final net.mcreator.mcanomalyarchives.anomaly.purple.SharedStalkerSession<PurpleMonsterEntity> sharedSession =
		new net.mcreator.mcanomalyarchives.anomaly.purple.SharedStalkerSession<>();
	private static final String OWNER_KEY = "PurpleStalkerOwner";

	public static void init() {
		NeoForge.EVENT_BUS.register(new PurpleStalkerHandler());
	}

	@SubscribeEvent
	public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player
				&& player.getData(McanomalyarchivesModAttachments.PURPLE_ASSIMILATION_COMPLETED)) {
			player.setData(McanomalyarchivesModAttachments.PURPLE_STALKER_NEXT,
					Math.max(player.getData(McanomalyarchivesModAttachments.PURPLE_STALKER_NEXT),
							player.level().getGameTime() + MIN_INTERVAL));
		}
	}

	@SubscribeEvent
	public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;
		UUID uuid = player.getUUID();
		removeOwnedStalker(uuid);
	}

	/** 玩家换维度时只清理其关联的窥视者（避免留在旧维度直到超时） */
	@SubscribeEvent
	public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			removeOwnedStalker(player.getUUID());
		}
	}

	@SubscribeEvent
	public void onServerTick(ServerTickEvent.Post event) {
		// 扫描节流：1 秒一次；轮换首位，避免共享名额总被同一玩家占用。
		if (event.getServer().getTickCount() % SCAN_INTERVAL != 0) return;
		PurpleMonsterEntity active = sharedSession.current();
		if (active != null && (active.isRemoved() || !active.isAlive() || !active.isStalkerMode())) sharedSession.clear();
		if (PurpleStoryModule.hasAnyActive()) { dismissActive(); return; }
		var players = event.getServer().getPlayerList().getPlayers();
		if (players.isEmpty()) return;
		int first = Math.floorMod(playerCursor++, players.size());
		for (int i = 0; i < players.size(); i++) processPlayer(players.get((first + i) % players.size()));
	}

	private void processPlayer(ServerPlayer player) {
		// 仅生存模式
		if (!player.isAlive() || player.isRemoved() || player.gameMode.isCreative() || player.isSpectator()
				|| PurpleStoryModule.hasAnyActive()) return;

		// 检查同化完成标记
		if (!player.getData(McanomalyarchivesModAttachments.PURPLE_ASSIMILATION_COMPLETED)) return;

		// 检查是否已有窥视者存在（全局同时最多一个）
		if (hasAnyActiveStalker()) return;

		// 检查冷却（下次生成时间）
		long currentGameTime = player.level().getGameTime();
		long nextTime = player.getData(McanomalyarchivesModAttachments.PURPLE_STALKER_NEXT);
		if (nextTime == 0) {
			player.setData(McanomalyarchivesModAttachments.PURPLE_STALKER_NEXT, currentGameTime + nextInterval(player));
			return;
		}
		if (currentGameTime < nextTime) return;

		// 夜晚/低亮度高概率，亮处低概率（抽不到就顺延一个间隔）
		boolean dark = isDarkArea(player);
		float chance = dark ? DARK_SPAWN_CHANCE : BRIGHT_SPAWN_CHANCE;
		if (player.getRandom().nextFloat() >= chance) {
			player.setData(McanomalyarchivesModAttachments.PURPLE_STALKER_NEXT, currentGameTime + nextInterval(player));
			return;
		}

		// 生成窥视者
		spawnStalker(player);

		// 设置下次生成时间
		player.setData(McanomalyarchivesModAttachments.PURPLE_STALKER_NEXT, currentGameTime + nextInterval(player));
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
	private boolean hasAnyActiveStalker() {
		return hasActiveEncounter();
	}

	public static boolean hasActiveEncounter() {
		return sharedSession.current() != null && !sharedSession.current().isRemoved();
	}

	public static void dismissActive() {
		PurpleMonsterEntity actor = sharedSession.current();
		sharedSession.clear();
		if (actor != null) actor.discard();
	}

	private void spawnStalker(ServerPlayer player) {
		ServerLevel serverLevel = (ServerLevel) player.level();
		int distance = distanceForSightings(player);

		BlockPos spawnPos = pickScenicSpawnPos(player, serverLevel, distance);
		if (spawnPos == null) return;

		PurpleMonsterEntity stalker = new PurpleMonsterEntity(
			McanomalyarchivesModEntities.PURPLE_MONSTER.get(), serverLevel);
		stalker.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
		stalker.setYRot(player.getYRot());
		stalker.yBodyRot = player.getYRot();
		stalker.yHeadRot = player.getYRot();
		stalker.setTextureVariant(1); // 紫色纹理
		stalker.setStalkerMode(true);
		stalker.getPersistentData().putUUID(OWNER_KEY, player.getUUID());
		if (!serverLevel.addFreshEntity(stalker)) return;
		net.mcreator.mcanomalyarchives.anomaly.purple.PurpleEncounterEffects.materialize(stalker);

		McanomalyarchivesMod.LOGGER.info("Stalker Purple Monster spawned for player {} at ({}, {}, {}), distance={}",
			player.getName().getString(),
			spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
			distance);
	}

	/**
	 * 真正窗位单独验证；其余候选为树边或露天位置，失败则本轮不出现。
	 */
	private BlockPos pickScenicSpawnPos(ServerPlayer player, ServerLevel level, int distance) {
		RandomSource rng = player.getRandom();
		if (player.getData(McanomalyarchivesModAttachments.PURPLE_STALKER_SIGHTINGS) >= 4
				&& rng.nextFloat() < (level.isRaining() && level.isNight() ? .9f : .5f)) {
			BlockPos window = PurpleStalkerWorld.window(player);
			if (window != null) return window;
		}
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
		return null;
	}

	/** 按角度和距离算地面坐标（找不到合适地面返回 null） */
	private BlockPos groundPosAt(ServerPlayer player, ServerLevel level, int distance, float angleDeg) {
		double angleRad = Math.toRadians(angleDeg);
		double spawnX = player.getX() + (-Math.sin(angleRad) * distance);
		double spawnZ = player.getZ() + (Math.cos(angleRad) * distance);
		if (!level.hasChunkAt(BlockPos.containing(spawnX, player.getY(), spawnZ))) return null;
		int groundY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,
			BlockPos.containing(spawnX, player.getY(), spawnZ)).getY();
		int top = Math.min(groundY, player.blockPosition().getY() + 8);
		int bottom = Math.max(level.getMinBuildHeight() + 1, player.blockPosition().getY() - 12);
		for (int y = top; y >= bottom; y--) {
			BlockPos pos = BlockPos.containing(spawnX, y, spawnZ);
			if (PurpleStalkerWorld.canStand(level, pos)
					&& PurpleStalkerWorld.outdoors(level, pos)
					&& !PurpleStalkerWorld.watched(level, PurpleStalkerWorld.eyeAt(pos))) return pos;
		}
		return null;
	}

	/** 窗位不在这里猜测，只给树林候选加权。 */
	private boolean isScenicSpot(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockState adj = level.getBlockState(pos.relative(dir));
			if (isTreeCover(adj)) return true;
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

	/** 只清理离场玩家关联的共享遭遇（登出/换维度时调用） */
	private void removeOwnedStalker(UUID owner) {
		PurpleMonsterEntity stalker = sharedSession.closeForOwner(owner);
		if (stalker != null) stalker.discard();
	}

	@SubscribeEvent
	public void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
		if (!event.getLevel().isClientSide() && event.getEntity() instanceof PurpleMonsterEntity stalker && stalker.isStalkerMode()) {
			if (PurpleStoryModule.hasAnyActive()) { event.setCanceled(true); return; }
			UUID owner = stalker.getPersistentData().hasUUID(OWNER_KEY) ? stalker.getPersistentData().getUUID(OWNER_KEY) : null;
			if (!sharedSession.open(stalker.getUUID(), owner, stalker)) event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
		if (!event.getLevel().isClientSide() && event.getEntity() instanceof PurpleMonsterEntity stalker) {
			sharedSession.leave(stalker.getUUID());
			if (stalker.isStalkerMode() && stalker.getPersistentData().hasUUID(OWNER_KEY)) {
				ServerPlayer owner = ((ServerLevel)event.getLevel()).getServer().getPlayerList()
						.getPlayer(stalker.getPersistentData().getUUID(OWNER_KEY));
				if (owner != null) owner.setData(McanomalyarchivesModAttachments.PURPLE_STALKER_NEXT,
						owner.level().getGameTime() + nextInterval(owner));
			}
		}
	}

	@SubscribeEvent
	public void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
		sharedSession.clear();
		playerCursor = 0;
	}
}
