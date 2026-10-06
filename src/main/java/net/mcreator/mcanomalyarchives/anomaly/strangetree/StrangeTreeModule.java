package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.BlockBackup;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Effect;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Phase;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;
import net.mcreator.mcanomalyarchives.entity.StrangeTreeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** 怪树玩法唯一接入。持久状态在实体 NeoForgeData，弱缓存不保留世界或离线玩家。 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class StrangeTreeModule {
	private static MinecraftServer server;
	private static final Map<StrangeTreeEntity, Memory> trees = new WeakHashMap<>();
	private static final Map<UUID, StrangeTreeProgress> players = new HashMap<>();

	private static final class Memory {
		final StrangeTreeState state;
		final boolean blocked;
		final ArrayDeque<Long> breaks = new ArrayDeque<>();
		long loiterSince = -1;
		long savedRevision = -1;
		long startedTick = Long.MIN_VALUE;
		int lastVisual;
		boolean initialRecovery;

		Memory(StrangeTreeState state, boolean blocked, int visual) {
			this.state = state;
			this.blocked = blocked;
			lastVisual = visual;
			initialRecovery = state.phase() == Phase.RECOVERING;
		}
	}

	private StrangeTreeModule() { }

	@SubscribeEvent
	public static void onStarting(ServerStartingEvent event) {
		trees.clear();
		players.clear();
		server = event.getServer();
	}

	private static boolean current(ServerLevel level) {
		return server != null && level.getServer() == server && server.isSameThread();
	}

	/** 首个实体 tick 才做加载/恢复；不在 EntityJoinLevelEvent 中访问尚未 FULL 的区块。 */
	private static Memory memory(StrangeTreeEntity tree, ServerLevel level) {
		Memory existing = trees.get(tree);
		if (existing != null) return existing;
		StrangeTreeState state;
		boolean blocked = false;
		String dimension = StrangeTreeWorld.dimension(level);
		try {
			if (tree.getPersistentData().contains(StrangeTreeNbt.KEY)) {
				StrangeTreeState.Snapshot snapshot = StrangeTreeNbt.read(tree.getPersistentData().getCompound(StrangeTreeNbt.KEY));
				StrangeTreeWorld.validateJournal(snapshot);
				if (!dimension.equals(snapshot.dimension())) {
					if (snapshot.phase() != Phase.STANDING || !snapshot.blocks().isEmpty()) {
						throw new IllegalArgumentException("Active tree recovery journal belongs to another dimension");
					}
					state = new StrangeTreeState(dimension, StrangeTreeWorld.point(tree.blockPosition()));
				} else {
					state = StrangeTreeState.fromSnapshot(snapshot);
					state.recoverAfterLoad(); // 保存是完整快照；重载明确选择取消攻击、恢复原位。
				}
			} else {
				BlockPos base = tree.blockPosition();
				if (tree.getBurrowState() != 0 && tree.getBurrowState() != 1) {
					base = new BlockPos(base.getX(), StrangeTreeWorld.groundY(level, base.getX(), base.getZ()) + 1, base.getZ());
				}
				state = new StrangeTreeState(dimension, StrangeTreeWorld.point(base));
				if (tree.getBurrowState() != 0) {
					state.requestRecovery();
					McanomalyarchivesMod.LOGGER.warn("Legacy active strange tree {} has no block journal; "
							+ "resetting posture without inventing missing blocks", tree.getUUID());
				}
			}
		} catch (IllegalArgumentException failure) {
			// 不覆盖不认识/损坏/跨维度的快照，更不能在错误维度还原方块。
			blocked = true;
			state = new StrangeTreeState(dimension, StrangeTreeWorld.point(tree.blockPosition()));
			McanomalyarchivesMod.LOGGER.warn("Strange tree {} recovery journal preserved but quarantined: {}",
					tree.getUUID(), failure.getMessage());
		}
		Memory created = new Memory(state, blocked, tree.getBurrowState());
		trees.put(tree, created);
		if (blocked) {
			tree.setBurrowState(0);
			tree.setInvisible(false);
			tree.noPhysics = false;
		} else {
			save(tree, created); // 先持久化取消后的状态，再做任何地形恢复。
		}
		return created;
	}

	private static void save(StrangeTreeEntity tree, Memory memory) {
		if (memory.blocked) return;
		if (memory.savedRevision != memory.state.revision()
				|| !tree.getPersistentData().contains(StrangeTreeNbt.KEY)) {
			tree.getPersistentData().put(StrangeTreeNbt.KEY, StrangeTreeNbt.write(memory.state.snapshot()));
			memory.savedRevision = memory.state.revision();
		} else if (memory.state.phase() != Phase.STANDING) {
			StrangeTreeNbt.writeClock(tree.getPersistentData().getCompound(StrangeTreeNbt.KEY), memory.state);
		}
	}

	private static void visual(StrangeTreeEntity tree, Memory memory, boolean force) {
		int visual = memory.state.visualState();
		if (force || memory.lastVisual != visual) {
			tree.setBurrowState(visual);
			tree.setInvisible(false);
			tree.noPhysics = visual == 2;
			memory.lastVisual = visual;
		}
	}

	@SubscribeEvent
	public static void onTreeTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof StrangeTreeEntity tree) || !(tree.level() instanceof ServerLevel level)
				|| !current(level) || tree.isRemoved() || !level.hasChunkAt(tree.blockPosition())) return;
		Memory memory = memory(tree, level);
		if (memory.blocked) return;
		StrangeTreeState state = memory.state;
		long tick = server.getTickCount();
		if (memory.startedTick == tick) return; // 事件触发这一帧不额外消耗一 tick 动画时间。
		if (memory.initialRecovery) {
			memory.initialRecovery = false;
			if (restore(tree, level, memory)) return;
		}
		Effect effect = state.tick(tick % 5 == 0);
		switch (effect) {
			case BURY -> {
				tree.setPos(tree.getX(), tree.getY() - 8, tree.getZ());
				memory.loiterSince = -1;
			}
			case KILL -> {
				UUID target = state.consumeKillTarget();
				save(tree, memory); // 先消费一次性击杀目标；死亡事件不能重入后再打重生玩家。
				StrangeTreeWorld.kill(level, target);
			}
			case CHECK_CHASE -> {
				ServerPlayer target = target(level, state.target());
				state.resolveChase(target != null, target != null && target.distanceToSqr(tree) <= 25);
				if (state.phase() == Phase.RECOVERING) restore(tree, level, memory);
				else if (state.phase() == Phase.CHASE_SWING) StrangeTreeWorld.quake(tree, 0.35f, 20);
			}
			case RESTORE -> restore(tree, level, memory);
			case RELOCATE -> relocate(tree, level, memory);
			case BEGIN_SWING, NONE -> { }
		}
		if (state.phase() == Phase.BURIED && tick % 5 == 0) {
			ServerPlayer target = target(level, state.target());
			if (state.target() != null && (target == null || target.distanceToSqr(tree) > 64 * 64)) {
				state.targetGone(state.target());
				target = null;
			}
			StrangeTreeWorld.track(level, tree, state, target);
		}
		if (state.phase() == Phase.STANDING && tick % 20 == 0) {
			boolean nearby = !level.getEntitiesOfClass(Player.class, new AABB(tree.blockPosition()).inflate(4)).isEmpty();
			if (!nearby) memory.loiterSince = -1;
			else {
				if (memory.loiterSince < 0) memory.loiterSince = level.getGameTime();
				if (level.getGameTime() - memory.loiterSince >= 45 * 20) begin(tree, level, memory, true, null);
			}
		}
		visual(tree, memory, false);
		save(tree, memory);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (!(event.getLevel() instanceof ServerLevel level) || !current(level)
				|| !(event.getPlayer() instanceof ServerPlayer player) || event.isCanceled()) return;
		BlockPos cut = event.getPos();
		if (StrangeTreeWorld.isLog(event.getState())) {
			StrangeTreeProgress progress = progress(player, level);
			progress.recordBreak(StrangeTreeWorld.point(cut), level.getGameTime());
			List<StrangeTreeEntity> nearby = level.getEntitiesOfClass(StrangeTreeEntity.class, new AABB(cut).inflate(32));
			for (StrangeTreeEntity tree : nearby) {
				if (tree.isRemoved() || !level.hasChunkAt(tree.blockPosition())) continue;
				Memory memory = memory(tree, level);
				if (!memory.blocked && memory.state.phase() == Phase.STANDING) {
					if (progress.countTree(StrangeTreeWorld.point(cut), () -> StrangeTreeWorld.connectedLogs(level, cut))) {
						begin(tree, level, memory, false, null);
					}
					break; // 保留旧规则：一次伐木计数只触发第一棵站立树。
				}
			}
			for (StrangeTreeEntity tree : nearby) {
				if (tree.isRemoved() || !level.hasChunkAt(tree.blockPosition())) continue;
				Memory memory = memory(tree, level);
				if (memory.blocked || memory.state.phase() != Phase.BURIED) continue;
				if (memory.state.recordLog(player.getUUID(), StrangeTreeWorld.point(cut))) {
					BlockPos emerge = StrangeTreeWorld.emergence(level, player);
					if (emerge == null) StrangeTreeWorld.quake(tree, 0.12f, 30);
					else if (memory.state.emerge(emerge.distSqr(player.blockPosition()) > 25)) {
						tree.setPos(emerge.getX() + 0.5, emerge.getY() + 1, emerge.getZ() + 0.5);
						float yaw = (float) (Math.toDegrees(Math.atan2(player.getZ() - tree.getZ(), player.getX() - tree.getX())) - 90);
						tree.setYRot(yaw);
						tree.setYHeadRot(yaw);
						tree.setYBodyRot(yaw);
						memory.startedTick = server.getTickCount();
						StrangeTreeWorld.quake(tree, 0.35f, 60);
					}
				}
				visual(tree, memory, false);
				save(tree, memory);
			}
		}
		for (StrangeTreeEntity tree : level.getEntitiesOfClass(StrangeTreeEntity.class, new AABB(cut).inflate(16))) {
			if (tree.isRemoved() || !level.hasChunkAt(tree.blockPosition())) continue;
			Memory memory = memory(tree, level);
			if (memory.blocked || memory.state.phase() != Phase.STANDING) continue;
			long now = level.getGameTime();
			memory.breaks.addLast(now);
			while (!memory.breaks.isEmpty() && now - memory.breaks.peekFirst() > 100) memory.breaks.removeFirst();
			if (memory.breaks.size() >= 10) begin(tree, level, memory, true, player.getUUID());
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPlace(BlockEvent.EntityPlaceEvent event) {
		if (!(event.getLevel() instanceof ServerLevel level) || !current(level)
				|| !(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()
				|| !StrangeTreeWorld.isSapling(event.getPlacedBlock())) return;
		cancelForPlanting(player);
		if (progress(player, level).replant(StrangeTreeWorld.point(event.getPos()), level.getGameTime())) cancelForPlanting(player);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onExplosion(ExplosionEvent.Start event) {
		if (!(event.getLevel() instanceof ServerLevel level) || !current(level) || event.isCanceled()) return;
		Vec3 center = event.getExplosion().center();
		for (StrangeTreeEntity tree : level.getEntitiesOfClass(StrangeTreeEntity.class,
				new AABB(BlockPos.containing(center)).inflate(16))) {
			if (tree.isRemoved() || !level.hasChunkAt(tree.blockPosition())) continue;
			Memory memory = memory(tree, level);
			if (!memory.blocked && memory.state.phase() == Phase.STANDING) begin(tree, level, memory, true, null);
		}
	}

	private static void begin(StrangeTreeEntity tree, ServerLevel level, Memory memory, boolean relocation, UUID avoid) {
		BlockPos base = tree.blockPosition();
		List<BlockBackup> backups = StrangeTreeWorld.capture(level, base);
		if (backups == null) return;
		// 以实际脚底为锚点，不能用清树前的 WORLD_SURFACE（本模组树冠也参与高度图）。
		Point ground = new Point(base.getX(), base.getY() - 1, base.getZ());
		boolean started = relocation
				? memory.state.beginRelocation(StrangeTreeWorld.point(base), ground, backups, avoid)
				: memory.state.beginBurrow(StrangeTreeWorld.point(base), ground, backups);
		if (!started) return;
		save(tree, memory); // 关键顺序：完整快照写入实体 NeoForgeData 后才能清树。
		StrangeTreeWorld.clear(level, backups);
		StrangeTreeWorld.churn(level, base);
		memory.breaks.clear();
		memory.loiterSince = -1;
		memory.startedTick = server.getTickCount();
		visual(tree, memory, false);
		StrangeTreeWorld.quake(tree, relocation ? 0.3f : 0.4f, relocation ? 50 : 60);
	}

	private static boolean restore(StrangeTreeEntity tree, ServerLevel level, Memory memory) {
		BlockPos base = StrangeTreeWorld.pos(memory.state.origin());
		if (!level.isInWorldBounds(base) || !level.hasChunkAt(base)) return false;
		if (memory.state.undo() != null) {
			if (!StrangeTreeWorld.undoRelocation(level, memory.state)) return false;
			memory.state.clearPlacementUndo();
			save(tree, memory);
		}
		if (!StrangeTreeWorld.restore(level, memory.state, BlockPos.ZERO)) return false;
		tree.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		memory.state.finishStanding(memory.state.origin());
		memory.breaks.clear();
		memory.loiterSince = -1;
		visual(tree, memory, true);
		save(tree, memory);
		return true;
	}

	private static void relocate(StrangeTreeEntity tree, ServerLevel level, Memory memory) {
		StrangeTreeState state = memory.state;
		BlockPos destination = StrangeTreeWorld.relocation(level, state, target(level, state.target()));
		BlockPos offset = destination == null ? BlockPos.ZERO : destination.subtract(StrangeTreeWorld.pos(state.ground()));
		if (destination == null || !StrangeTreeWorld.placeRelocation(level, state, offset, attempted -> {
			state.recordPlacementUndo(StrangeTreeWorld.point(offset), attempted);
			save(tree, memory);
		})) {
			state.requestRecovery();
			save(tree, memory);
			restore(tree, level, memory); // 原实现找不到落点会丢失树身，现在回滚完整快照。
			return;
		}
		// 整体提交完成才消耗撤回计划；失败/重载须先清理目的地，再恢复原位。
		state.clearPlacementUndo();
		BlockPos base = destination.above();
		tree.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		state.finishStanding(StrangeTreeWorld.point(base));
		memory.breaks.clear();
		memory.loiterSince = -1;
		visual(tree, memory, true);
		save(tree, memory);
	}

	private static ServerPlayer target(ServerLevel level, UUID player) {
		ServerPlayer result = player == null ? null : server.getPlayerList().getPlayer(player);
		return result != null && result.level() == level && result.isAlive() && !result.isRemoved() ? result : null;
	}

	private static StrangeTreeProgress progress(ServerPlayer player, ServerLevel level) {
		String dimension = StrangeTreeWorld.dimension(level);
		StrangeTreeProgress progress = players.get(player.getUUID());
		if (progress == null || !dimension.equals(progress.dimension())) {
			progress = new StrangeTreeProgress(dimension);
			players.put(player.getUUID(), progress);
		}
		return progress;
	}

	private static void cancelForPlanting(ServerPlayer player) {
		for (Map.Entry<StrangeTreeEntity, Memory> entry : new ArrayList<>(trees.entrySet())) {
			StrangeTreeEntity tree = entry.getKey();
			Memory memory = entry.getValue();
			if (tree == null || tree.isRemoved() || memory.blocked || !memory.state.canCancelByPlanting(player.getUUID())
					|| !(tree.level() instanceof ServerLevel level)) continue;
			memory.state.requestRecovery();
			save(tree, memory);
			restore(tree, level, memory);
		}
	}

	private static void playerGone(ServerPlayer player) {
		if (server == null || player.getServer() != server || !server.isSameThread()) return;
		players.remove(player.getUUID());
		for (Map.Entry<StrangeTreeEntity, Memory> entry : new ArrayList<>(trees.entrySet())) {
			StrangeTreeEntity tree = entry.getKey();
			Memory memory = entry.getValue();
			if (tree == null || tree.isRemoved() || memory.blocked || !(tree.level() instanceof ServerLevel level)) continue;
			memory.state.targetGone(player.getUUID());
			save(tree, memory);
			if (memory.state.phase() == Phase.RECOVERING) restore(tree, level, memory);
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) playerGone(player);
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		if (event.getEntity() instanceof ServerPlayer player) playerGone(player);
	}

	@SubscribeEvent
	public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) playerGone(player);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) playerGone(player);
	}

	@SubscribeEvent
	public static void onEntityLeave(EntityLeaveLevelEvent event) {
		if (!(event.getEntity() instanceof StrangeTreeEntity tree) || !(event.getLevel() instanceof ServerLevel level)
				|| server == null || level.getServer() != server) return;
		if (server.isSameThread()) trees.remove(tree);
		else {
			MinecraftServer owner = server;
			owner.execute(() -> { if (server == owner) trees.remove(tree); });
		}
	}

	@SubscribeEvent
	public static void onStopping(ServerStoppingEvent event) {
		if (event.getServer() == server) stop();
	}

	@SubscribeEvent
	public static void onStopped(ServerStoppedEvent event) {
		if (event.getServer() == server) stop();
	}

	private static void stop() {
		// 不在停服期间操作未加载地形；状态已在实体标签，下一次加载安全恢复。
		trees.clear();
		players.clear();
		server = null;
	}
}
