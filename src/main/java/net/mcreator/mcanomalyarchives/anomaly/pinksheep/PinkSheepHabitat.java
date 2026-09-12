package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.mcreator.mcanomalyarchives.entity.PinkSheepEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.List;

/**
 * UO-012 幸运粉羊 · 栖息地规则（选点 / 群系白名单 / 平坦判定 / 区域唯一）。
 *
 * 【工程层归属】本类位于 anomaly 包，不属于 MCreator 生成区：
 * MCreator 重新生成代码（PinkSheep 元素、注册表、渲染器）不会触碰本文件。
 * 生成区里的 PinkSheepEntity 只是薄壳，真正的规则全部在这里。
 *
 * 结构式生成（PinkSheepStructureSpawnHandler）与转移落点（PinkSheepMechanics）
 * 共用同一套规则，保证"刷在哪"和"转移去哪"完全一致。
 */
public final class PinkSheepHabitat {

	/** 允许出现粉羊的群系：平原 + 温和开阔（避免森林/山地/水域等不"开阔"的地方） */
	public static final List<ResourceKey<Biome>> ALLOWED_BIOMES = List.of(
			ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("plains")),
			ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("sunflower_plains")),
			ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("meadow")),
			ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("forest")),
			ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("birch_forest")),
			ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("old_growth_birch_forest")),
			ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("flower_forest")),
			ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("cherry_grove")));

	/** 结构式生成的环形距离（离玩家 48~160 格，像"结构被遇到"而不是贴脸刷怪） */
	public static final int SPAWN_MIN_DIST = 48;
	public static final int SPAWN_MAX_DIST = 160;

	/** 转移落点的环形距离（离最近玩家 40~80 格，保证转移后仍可被再次发现） */
	public static final int TELEPORT_MIN_DIST = 40;
	public static final int TELEPORT_MAX_DIST = 80;

	/** 平坦检测范围（单边格数，7x7） */
	public static final int FLAT_HALF = 3;

	/** 区域唯一半径：同一区域内只允许存在一只粉羊 */
	public static final double UNIQUE_RADIUS = 128.0;

	private PinkSheepHabitat() {
	}

	/** 该点是否属于允许群系 */
	public static boolean isAllowedBiome(ServerLevel level, BlockPos pos) {
		Holder<Biome> holder = level.getBiome(pos);
		for (ResourceKey<Biome> key : ALLOWED_BIOMES) {
			if (holder.is(key))
				return true;
		}
		return false;
	}

	/** 7x7 范围内地表高度差 ≤1，且头顶 3 格无方块遮挡（保证可见、可被注视） */
	public static boolean isFlat(ServerLevel level, BlockPos pos) {
		int base = pos.getY();
		for (int dx = -FLAT_HALF; dx <= FLAT_HALF; dx++) {
			for (int dz = -FLAT_HALF; dz <= FLAT_HALF; dz++) {
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX() + dx, pos.getZ() + dz);
				if (Math.abs(y - base) > 1)
					return false;
			}
		}
		for (int dy = 1; dy <= 3; dy++) {
			if (!level.getBlockState(pos.above(dy)).isAir() && !level.getBlockState(pos.above(dy)).canBeReplaced())
				return false;
		}
		return true;
	}

	/** 环形带内寻找合法落点；找不到返回 null（调用方自行决定"原地不动"或"放弃生成"） */
	@Nullable
	public static Vec3 findSpot(ServerLevel level, double baseX, double baseZ, double minDist, double maxDist,
			Heightmap.Types heightmap, int attempts, RandomSource random, @Nullable PinkSheepEntity exclude) {
		for (int attempt = 0; attempt < attempts; attempt++) {
			double dist = minDist + random.nextDouble() * (maxDist - minDist);
			double angle = random.nextDouble() * Math.PI * 2;
			double x = baseX + Math.cos(angle) * dist;
			double z = baseZ + Math.sin(angle) * dist;
			int y = level.getHeight(heightmap, (int) Math.floor(x), (int) Math.floor(z));
			if (y <= level.getMinBuildHeight() + 2)
				continue;
			BlockPos pos = new BlockPos((int) Math.floor(x), y, (int) Math.floor(z));
			if (!isAllowedBiome(level, pos))
				continue;
			if (!isFlat(level, pos))
				continue;
			// 区域唯一：UNIQUE_RADIUS 内已有另一只活粉羊则换点
			if (findSheepNear(level, x, z, UNIQUE_RADIUS, exclude) != null)
				continue;
			return new Vec3(x + 0.5, y + 0.5, z + 0.5);
		}
		return null;
	}

	/** 指定水平位置附近是否已有粉羊（exclude 通常传"自己"） */
	@Nullable
	public static PinkSheepEntity findSheepNear(ServerLevel level, double x, double z, double radius,
			@Nullable PinkSheepEntity exclude) {
		AABB box = new AABB(x - radius, level.getMinBuildHeight(), z - radius, x + radius, level.getMaxBuildHeight(),
				z + radius);
		for (PinkSheepEntity other : level.getEntitiesOfClass(PinkSheepEntity.class, box)) {
			if (other != exclude && other.isAlive())
				return other;
		}
		return null;
	}

	/** 世界内粉羊总数（总量兜底用） */
	public static long countSheep(ServerLevel level) {
		long total = 0;
		for (var entity : level.getEntities().getAll()) {
			if (entity instanceof PinkSheepEntity s && s.isAlive())
				total++;
		}
		return total;
	}

	/** 玩家附近是否已有粉羊（生成器节流用） */
	public static boolean hasSheepNear(ServerLevel level, double x, double z, double radius) {
		return findSheepNear(level, x, z, radius, null) != null;
	}
}
