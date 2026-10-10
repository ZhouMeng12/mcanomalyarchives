package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.BlockBackup;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;
import net.mcreator.mcanomalyarchives.entity.StrangeTreeEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.network.StrangeTreeQuakePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** 世界查询/方块效果的 adapter；先检查已加载区块，不因恢复或寻路强制加载远处世界。 */
final class StrangeTreeWorld {
	private static final ResourceKey<DamageType> WAIST_CUT = ResourceKey.create(Registries.DAMAGE_TYPE,
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "waistcut"));

	private StrangeTreeWorld() { }
	static Point point(BlockPos pos) { return new Point(pos.getX(), pos.getY(), pos.getZ()); }
	static BlockPos pos(Point point) { return new BlockPos(point.x(), point.y(), point.z()); }
	static String dimension(ServerLevel level) { return level.dimension().location().toString(); }

	static boolean isLog(BlockState state) {
		return state.is(BlockTags.LOGS) || state.getBlock() == McanomalyarchivesModBlocks.STRANGETREELOG.get()
				|| state.getBlock() == McanomalyarchivesModBlocks.ORANGE_LOG.get()
				|| state.getBlock() == McanomalyarchivesModBlocks.STRIPPED_ORANGE_LOG.get();
	}

	static boolean isSapling(BlockState state) {
		return state.getBlock() instanceof SaplingBlock || state.getBlock() == McanomalyarchivesModBlocks.ORANGE_SAPLING.get();
	}

	private static boolean isTree(BlockState state) {
		return state.getBlock() == McanomalyarchivesModBlocks.STRANGETREELOG.get()
				|| state.getBlock() == McanomalyarchivesModBlocks.STRANGETREELEAVES.get();
	}

	static Set<Point> connectedLogs(ServerLevel level, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		seen.add(start);
		pending.push(start);
		while (!pending.isEmpty()) {
			BlockPos current = pending.pop();
			for (BlockPos next : new BlockPos[] {current.above(), current.below(), current.north(), current.south(), current.east(), current.west()}) {
				if (level.hasChunkAt(next) && !seen.contains(next) && isLog(level.getBlockState(next))) {
					seen.add(next);
					pending.push(next);
				}
			}
		}
		Set<Point> result = new HashSet<>();
		seen.forEach(position -> result.add(point(position)));
		return result;
	}

	/** 只捕获，不清除；调用方必须先把这些快照持久化，再执行 clear。 */
	static List<BlockBackup> capture(ServerLevel level, BlockPos base) {
		List<BlockBackup> result = new ArrayList<>();
		for (BlockPos location : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, 6, 4))) {
			if (!level.hasChunkAt(location)) return null;
			BlockState state = level.getBlockState(location);
			if (isTree(state)) result.add(new BlockBackup(point(location), NbtUtils.writeBlockState(state).toString()));
		}
		return result;
	}

	static void clear(ServerLevel level, List<BlockBackup> backups) {
		for (BlockBackup backup : backups) {
			BlockPos location = pos(backup.point());
			if (level.hasChunkAt(location) && isTree(level.getBlockState(location))) {
				level.setBlock(location, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** 全部相关区块就绪才执行。仅放回空气位置，保留玩家后来放置的其他方块。 */
	static boolean restore(ServerLevel level, StrangeTreeState state, BlockPos offset) {
		return StrangeTreePlacement.apply(entries(state, offset), access(level), attempted -> { });
	}

	/** 迁居使用相同生产写入路径，但在每次世界写入之前持久化可撤回的意图。 */
	static boolean placeRelocation(ServerLevel level, StrangeTreeState state, BlockPos offset,
			Consumer<List<Integer>> beforeWrite) {
		return StrangeTreePlacement.apply(entries(state, offset), access(level), beforeWrite);
	}

	static boolean undoRelocation(ServerLevel level, StrangeTreeState state) {
		if (state.undo() == null) return true;
		List<StrangeTreePlacement.Entry<BlockState>> all = entries(state, pos(state.undo().offset()));
		List<StrangeTreePlacement.Entry<BlockState>> attempted = new ArrayList<>();
		for (int index : state.undo().entries()) attempted.add(all.get(index));
		return StrangeTreePlacement.rollback(attempted, access(level));
	}

	/** 完整验证发生在转换/保存任何恢复状态之前，包括注册名与真实属性定义。 */
	static void validateJournal(StrangeTreeState.Snapshot snapshot) {
		for (BlockBackup backup : snapshot.blocks()) decode(backup.state());
		if (snapshot.undo() != null) {
			for (int index : snapshot.undo().entries()) {
				translated(snapshot.blocks().get(index).point(), pos(snapshot.undo().offset()));
			}
		}
	}

	private static List<StrangeTreePlacement.Entry<BlockState>> entries(StrangeTreeState state, BlockPos offset) {
		List<StrangeTreePlacement.Entry<BlockState>> entries = new ArrayList<>();
		for (int index = 0; index < state.blocks().size(); index++) {
			BlockBackup backup = state.blocks().get(index);
			Point target = translated(backup.point(), offset);
			BlockState block = decode(backup.state());
			entries.add(new StrangeTreePlacement.Entry<>(index, target, block));
		}
		return entries;
	}

	private static Point translated(Point point, BlockPos offset) {
		try {
			return new Point(Math.addExact(point.x(), offset.getX()),
					Math.addExact(point.y(), offset.getY()), Math.addExact(point.z(), offset.getZ()));
		} catch (ArithmeticException failure) {
			throw new IllegalArgumentException("Overflowing tree placement coordinates", failure);
		}
	}

	private static StrangeTreePlacement.Access<BlockState> access(ServerLevel level) {
		return new StrangeTreePlacement.Access<>() {
			@Override public boolean ready(Point point) {
				BlockPos target = pos(point);
				return level.isInWorldBounds(target) && level.hasChunkAt(target);
			}
			@Override public BlockState read(Point point) { return level.getBlockState(pos(point)); }
			@Override public boolean isAir(BlockState state) { return state.isAir(); }
			@Override public BlockState air() { return Blocks.AIR.defaultBlockState(); }
			@Override public boolean write(Point point, BlockState state) {
				try {
					return level.setBlock(pos(point), state, 2);
				} catch (RuntimeException failure) {
					McanomalyarchivesMod.LOGGER.warn("Tree placement failed at {} (journal retained)", pos(point), failure);
					throw failure;
				}
			}
		};
	}

	private static BlockState decode(String text) {
		StrangeTreeBlockCodec.Description description = StrangeTreeBlockCodec.parse(text);
		if (!BuiltInRegistries.BLOCK.containsKey(description.id())) {
			throw new IllegalArgumentException("Unregistered tree block: " + description.id());
		}
		BlockState state = BuiltInRegistries.BLOCK.get(description.id()).defaultBlockState();
		if (!isTree(state)) throw new IllegalArgumentException("Non-tree block in restoration journal");
		return StrangeTreeBlockCodec.decode(description, state);
	}

	static boolean loadedJournal(ServerLevel level, StrangeTreeState state, BlockPos offset) {
		for (BlockBackup backup : state.blocks()) {
			BlockPos target = pos(backup.point()).offset(offset);
			if (!level.isInWorldBounds(target) || !level.hasChunkAt(target)) return false;
		}
		return true;
	}

	static void churn(ServerLevel level, BlockPos base) {
		BlockPos ground = base;
		for (int count = 0; count < 16 && ground.getY() > level.getMinBuildHeight(); count++) {
			BlockState state = level.getBlockState(ground);
			if (!state.isAir() && state.getFluidState().isEmpty()) break;
			ground = ground.below();
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos surface = ground.offset(dx, 0, dz);
				if (!level.hasChunkAt(surface)) continue;
				BlockState top = level.getBlockState(surface);
				if (!(top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) || top.is(Blocks.COARSE_DIRT))
						|| !level.getBlockState(surface.below()).isSolid()) continue;
				double random = level.random.nextDouble();
				if (random < 0.30) {
					level.setBlock(surface, Blocks.DIRT.defaultBlockState(), 2);
					level.setBlock(surface.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				} else if (random < 0.45) {
					if (level.getBlockState(surface.below(2)).is(Blocks.DIRT)) {
						level.setBlock(surface.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
						level.setBlock(surface.below(2), Blocks.DIRT.defaultBlockState(), 2);
					}
					level.setBlock(surface, Blocks.DIRT.defaultBlockState(), 2);
				}
			}
		}
	}

	static int groundY(ServerLevel level, int x, int z) {
		int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
		while (y > level.getMinBuildHeight()) {
			BlockState state = level.getBlockState(new BlockPos(x, y, z));
			if (state.isAir() || state.getBlock() instanceof LeavesBlock || isLog(state) || isTree(state)) y--;
			else break;
		}
		return y;
	}

	static BlockPos emergence(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		double length = Math.sqrt(look.x * look.x + look.z * look.z);
		double lx = length < 1e-4 ? 0 : look.x / length;
		double lz = length < 1e-4 ? 1 : look.z / length;
		BlockPos playerPos = player.blockPosition(), best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -16; dx <= 16; dx++) {
			for (int dz = -16; dz <= 16; dz++) {
				if ((dx == 0 && dz == 0) || dx * lx + dz * lz >= 0) continue;
				int x = playerPos.getX() + dx, z = playerPos.getZ() + dz;
				if (!level.hasChunkAt(new BlockPos(x, playerPos.getY(), z))) continue;
				int y = groundY(level, x, z);
				BlockPos candidate = new BlockPos(x, y, z);
				BlockState ground = level.getBlockState(candidate);
				boolean dirt = ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT)
						|| ground.is(Blocks.ROOTED_DIRT) || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.PODZOL) || ground.is(Blocks.DIRT_PATH);
				if (!dirt || !level.getBlockState(candidate.below()).isSolid()) continue;
				double distance = candidate.distSqr(playerPos);
				if (distance >= 4 * 4 && distance < bestDistance) {
					bestDistance = distance;
					best = candidate;
				}
			}
		}
		return best;
	}

	static void track(ServerLevel level, StrangeTreeEntity tree, StrangeTreeState state, ServerPlayer player) {
		if (state.ambushed() || state.ambush() == null) return;
		Point destination = state.ambush();
		double playerDistance = player == null ? Double.MAX_VALUE
				: Math.pow(player.getX() - tree.getX(), 2) + Math.pow(player.getZ() - tree.getZ(), 2);
		double dx = destination.x() + 0.5 - tree.getX(), dz = destination.z() + 0.5 - tree.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance > 5 && playerDistance > 25) {
			double oldX = tree.getX(), oldZ = tree.getZ();
			double speed = speed(level, tree.blockPosition());
			if (speed <= 0) {
				double step = 3.1 / 20 * 5, px = -dz / distance, pz = dx / distance;
				BlockPos first = tree.blockPosition().offset((int) Math.round(px * step), 0, (int) Math.round(pz * step));
				BlockPos second = tree.blockPosition().offset((int) Math.round(-px * step), 0, (int) Math.round(-pz * step));
				if (speed(level, first) > 0) tree.setPos(oldX + px * step, tree.getY(), oldZ + pz * step);
				else if (speed(level, second) > 0) tree.setPos(oldX - px * step, tree.getY(), oldZ - pz * step);
			} else {
				double step = Math.min(distance, speed / 20 * 5);
				double x = oldX + dx / distance * step, z = oldZ + dz / distance * step;
				if (level.hasChunkAt(BlockPos.containing(x, tree.getY(), z))) tree.setPos(x, tree.getY(), z);
			}
			if (tree.getX() != oldX || tree.getZ() != oldZ) quake(tree, 0.08f, 10);
		}
		double remaining = Math.pow(destination.x() + 0.5 - tree.getX(), 2) + Math.pow(destination.z() + 0.5 - tree.getZ(), 2);
		if (remaining <= 25 || playerDistance <= 25) {
			state.arrive();
			quake(tree, 0.12f, 30);
		}
	}

	private static double speed(ServerLevel level, BlockPos position) {
		if (!level.hasChunkAt(position)) return 0;
		BlockState ground = level.getBlockState(new BlockPos(position.getX(), groundY(level, position.getX(), position.getZ()), position.getZ()));
		if (ground.is(Blocks.BEDROCK)) return 0;
		if (ground.is(Blocks.OBSIDIAN)) return 0.4;
		if (ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.COBBLESTONE)) return 1.5;
		return 3.1;
	}

	static BlockPos relocation(ServerLevel level, StrangeTreeState state, ServerPlayer avoid) {
		BlockPos from = pos(state.ground());
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			int distance = 20 + level.random.nextInt(21);
			int x = from.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = from.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, from.getY(), z))) continue;
			BlockPos ground = new BlockPos(x, groundY(level, x, z), z);
			if (avoid != null && ground.distSqr(avoid.blockPosition()) < 12 * 12) continue;
			BlockPos offset = ground.subtract(from);
			if (level.getBlockState(ground).isSolid() && level.getBlockState(ground.above()).isAir()
					&& level.getBlockState(ground.above(2)).isAir() && loadedJournal(level, state, offset)) return ground;
		}
		return null;
	}

	static void kill(ServerLevel level, UUID target) {
		if (target == null) return;
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
		if (player == null || player.level() != level || !player.isAlive() || player.isRemoved()) return;
		player.hurt(level.damageSources().source(WAIST_CUT), Float.MAX_VALUE);
		if (player.isAlive()) player.kill();
		if (player.isAlive()) player.setHealth(0);
	}

	static void quake(StrangeTreeEntity tree, float amplitude, int duration) {
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(tree, new StrangeTreeQuakePacket(amplitude, duration));
	}
}
