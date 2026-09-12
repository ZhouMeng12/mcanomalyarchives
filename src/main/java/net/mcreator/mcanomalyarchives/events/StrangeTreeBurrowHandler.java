package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.StrangeTreeEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.network.StrangeTreeQuakePacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 怪树钻地（UO-011）：
 * - 玩家在怪树 32 格内砍原木/树，累计 8 棵（连通原木簇计棵：同一棵树只计 1 棵）触发钻地；
 * - 砍后 10 秒内在破坏位置 3 格内补种树苗 → 计数清零（补种免疫）；
 * - 补种免疫强化：累计补种 3 棵 → 令该玩家锁定的所有怪树彻底放弃本次袭击（恢复站立）；
 * - 钻地：清除树干/树叶、翻土（草/泥土换位隆起）、换整树模型播 zuandi、
 *   屏幕地震，3 秒后实体埋入地下隐藏。
 */
public class StrangeTreeBurrowHandler {

    private static final int LOG_RANGE = 32;
    /** 钻地阈值：砍 8 棵完整树（连通原木簇，非方块数） */
    private static final int THRESHOLD = 8;
    private static final int REPLANT_WINDOW_TICKS = 200; // 10 秒
    private static final int REPLANT_RANGE = 3;
    /** 补种免疫强化：累计补种达到此数 → 树彻底放弃本次袭击 */
    private static final int REPLANT_ABANDON_THRESHOLD = 3;
    private static final int CLEAR_RANGE_XZ = 4;
    private static final int CLEAR_Y_MAX = 6;
    private static final int CHURN_RANGE = 3;
    private static final int BURROW_TO_BURIED_TICKS = 60; // 3 秒，与 zuandi 同步
    /** 钻地后实体埋入地下的深度（格） */
    private static final int BURIED_DEPTH = 8;

    // 钻地后实体保持可见（不 setInvisible），始终是同一个实体：
    // 玩家可自行加 glowing 等效果在地底观察其移动；渲染器对 state 2 也正常渲染。
    // （原实现钻地后 setInvisible(true)，会让发光描边一并消失，看起来像实体被清除重建）

    /** 每玩家伐木计数（按棵树） */
    private static final Map<UUID, Integer> breakCounts = new HashMap<>();
    /** 每玩家最近的砍树记录（用于补种免疫） */
    private static final Map<UUID, List<BreakRecord>> recentBreaks = new HashMap<>();
    /** 每玩家累计补种数（≥3 触发彻底放弃） */
    private static final Map<UUID, Integer> replantCounts = new HashMap<>();
    /** 每玩家已计棵的原木位置集合（用于"一棵树只计一次"） */
    private static final Map<UUID, Set<BlockPos>> countedLogs = new HashMap<>();
    /** 钻地时被清除的怪树树干/树叶方块（按树实体 UUID），供恢复站立时还原 */
    private static final Map<UUID, List<Map.Entry<BlockPos, BlockState>>> clearedTreeBlocks = new HashMap<>();
    /** 钻地瞬间的原基座位置（树 UUID → 地表站立点），供恢复站立时实体回原位（树追踪移动后仍用钻地时位置） */
    private static final Map<UUID, BlockPos> burrowPositions = new HashMap<>();

    private record BreakRecord(BlockPos pos, long gameTime) {
    }

    public static void init() {
        NeoForge.EVENT_BUS.register(new StrangeTreeBurrowHandler());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID uid = player.getUUID();
            breakCounts.remove(uid);
            recentBreaks.remove(uid);
            replantCounts.remove(uid);
            countedLogs.remove(uid);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!isLog(event.getState())) return;
        Player player = event.getPlayer();
        UUID uid = player.getUUID();
        recordBreak(player, event.getPos(), event.getLevel());

        StrangeTreeEntity tree = findStandingTree(event.getLevel(), event.getPos());
        if (tree == null) return;
        // 按棵树计数：同一棵连通原木簇只计 1 棵
        if (!countTree(uid, event.getPos(), event.getLevel())) return;
        int count = breakCounts.merge(uid, 1, Integer::sum);
        if (count >= THRESHOLD) {
            breakCounts.put(uid, 0);
            countedLogs.remove(uid); // 新一轮重新计棵
            replantCounts.put(uid, 0);
            triggerBurrow(tree);
        }
    }

    @SubscribeEvent
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isSapling(event.getPlacedBlock())) return;
        UUID uid = player.getUUID();
        // 追杀终止：只要种下树苗，正在追杀该玩家的怪树立即停止（恢复站立）
        StrangeTreeAmbushHandler.abandonPlayer(player);
        // 补种免疫：砍树后短时间内补种树苗 → 计数清零
        if (withinReplantWindow(player, event.getPos(), event.getLevel())) {
            breakCounts.put(uid, 0);
            countedLogs.remove(uid); // 计数清零，重新计棵
            // 强化：累计补 3 棵 → 令该玩家锁定的怪树彻底放弃本次袭击
            int replants = replantCounts.merge(uid, 1, Integer::sum);
            if (replants >= REPLANT_ABANDON_THRESHOLD) {
                replantCounts.put(uid, 0);
                StrangeTreeAmbushHandler.abandonPlayer(player);
            }
        }
    }

    /** 判断是否为一棵"新树"：该位置此前未计过棵 → 做连通簇标记并计 1 棵；同一棵树后续原木 → 不重复计 */
    private static boolean countTree(UUID uid, BlockPos pos, LevelAccessor level) {
        Set<BlockPos> counted = countedLogs.computeIfAbsent(uid, k -> new HashSet<>());
        if (counted.contains(pos)) return false;
        counted.addAll(floodFillLogs(level, pos));
        return true;
    }

    /** 从被破坏的原木位置出发，6 方向连通所有原木方块（同一棵树） */
    private static Set<BlockPos> floodFillLogs(LevelAccessor level, BlockPos start) {
        Set<BlockPos> cluster = new HashSet<>();
        Deque<BlockPos> stack = new ArrayDeque<>();
        cluster.add(start);
        stack.push(start);
        while (!stack.isEmpty()) {
            BlockPos cur = stack.pop();
            for (BlockPos next : new BlockPos[]{
                    cur.above(), cur.below(), cur.north(), cur.south(), cur.east(), cur.west()}) {
                if (!cluster.contains(next) && isLog(level.getBlockState(next))) {
                    cluster.add(next);
                    stack.push(next);
                }
            }
        }
        return cluster;
    }

    private void recordBreak(Player player, BlockPos pos, LevelAccessor level) {
        long time = level instanceof Level l ? l.getGameTime() : 0;
        List<BreakRecord> list = recentBreaks.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        list.add(new BreakRecord(pos, time));
        // 清理过期记录
        list.removeIf(r -> time - r.gameTime() > REPLANT_WINDOW_TICKS);
    }

    private boolean withinReplantWindow(Player player, BlockPos pos, LevelAccessor level) {
        long time = level instanceof Level l ? l.getGameTime() : 0;
        List<BreakRecord> list = recentBreaks.get(player.getUUID());
        if (list == null) return false;
        for (BreakRecord r : list) {
            if (time - r.gameTime() <= REPLANT_WINDOW_TICKS
                    && Math.abs(r.pos().getX() - pos.getX()) <= REPLANT_RANGE
                    && Math.abs(r.pos().getY() - pos.getY()) <= REPLANT_RANGE
                    && Math.abs(r.pos().getZ() - pos.getZ()) <= REPLANT_RANGE) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.getBlock() == McanomalyarchivesModBlocks.STRANGETREELOG.get()
                || state.getBlock() == McanomalyarchivesModBlocks.ORANGE_LOG.get()
                || state.getBlock() == McanomalyarchivesModBlocks.STRIPPED_ORANGE_LOG.get();
    }

    public static boolean isSapling(BlockState state) {
        return state.getBlock() instanceof SaplingBlock
                || state.getBlock() == McanomalyarchivesModBlocks.ORANGE_SAPLING.get();
    }

    /** 取出某棵怪树钻地时被清除的方块列表（供恢复站立），无则返回 null */
    public static List<Map.Entry<BlockPos, BlockState>> takeClearedBlocks(UUID treeId) {
        return clearedTreeBlocks.remove(treeId);
    }

    /** 取出某棵怪树钻地瞬间的原基座位置（供恢复站立时实体回原位），无则返回 null */
    public static BlockPos takeBurrowPosition(UUID treeId) {
        return burrowPositions.remove(treeId);
    }

    private StrangeTreeEntity findStandingTree(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof Level l)) return null;
        List<StrangeTreeEntity> list = l.getEntitiesOfClass(StrangeTreeEntity.class, new AABB(pos).inflate(LOG_RANGE));
        for (StrangeTreeEntity tree : list) {
            if (tree.getBurrowState() == 0) return tree;
        }
        return null;
    }

    private void triggerBurrow(StrangeTreeEntity tree) {
        ServerLevel level = (ServerLevel) tree.level();
        BlockPos base = tree.blockPosition();
        // 记录钻地瞬间的原基座（此时树尚未被追踪移动），供恢复站立时实体回原位
        burrowPositions.put(tree.getUUID(), base.immutable());
        // 记录被清除的树干/树叶，供偷袭恢复站立时还原
        List<Map.Entry<BlockPos, BlockState>> cleared = clearTreeBlocks(level, base);
        if (cleared != null && !cleared.isEmpty()) {
            clearedTreeBlocks.put(tree.getUUID(), cleared);
        }
        churnDirt(level, base);
        tree.setBurrowState(1); // 钻地中：整树模型 + zuandi 动画
        // 地震效果（发给追踪该实体的玩家）
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(tree, new StrangeTreeQuakePacket(0.4f, 60));
        // 3 秒后埋入地下：保持可见（不 invisible），实体始终存在，发光等效果不中断
        McanomalyarchivesMod.queueServerWork(BURROW_TO_BURIED_TICKS, () -> {
            if (!tree.isRemoved() && tree.getBurrowState() == 1) {
                tree.setBurrowState(2);
                tree.setInvisible(false);
                tree.noPhysics = true;
                tree.setPos(tree.getX(), tree.getY() - BURIED_DEPTH, tree.getZ());
            }
        });
    }

    /** 清除范围内的怪树树干/树叶（直接换空气，不触发破坏事件 → 树叶不会再生），并返回被清除的方块列表 */
    public static List<Map.Entry<BlockPos, BlockState>> clearTreeBlocks(ServerLevel level, BlockPos base) {
        List<Map.Entry<BlockPos, BlockState>> cleared = new ArrayList<>();
        for (int dy = 0; dy <= CLEAR_Y_MAX; dy++) {
            for (int dx = -CLEAR_RANGE_XZ; dx <= CLEAR_RANGE_XZ; dx++) {
                for (int dz = -CLEAR_RANGE_XZ; dz <= CLEAR_RANGE_XZ; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (st.getBlock() == McanomalyarchivesModBlocks.STRANGETREELOG.get()
                            || st.getBlock() == McanomalyarchivesModBlocks.STRANGETREELEAVES.get()) {
                        cleared.add(new java.util.AbstractMap.SimpleEntry<>(p.immutable(), st));
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        return cleared;
    }

    /** 翻土：树根处草/泥土换位、泥土隆起（钻地现场感） */
    public static void churnDirt(ServerLevel level, BlockPos base) {
        // 清树后向下找第一个非空气块 = 地表（树方块已变空气，直接搜即可）
        BlockPos ground = base;
        for (int i = 0; i < 16 && ground.getY() > level.getMinBuildHeight(); i++) {
            BlockState st = level.getBlockState(ground);
            if (!st.isAir() && st.getFluidState().isEmpty()) break;
            ground = ground.below();
        }
        for (int dx = -CHURN_RANGE; dx <= CHURN_RANGE; dx++) {
            for (int dz = -CHURN_RANGE; dz <= CHURN_RANGE; dz++) {
                BlockPos surface = ground.offset(dx, 0, dz);
                BlockState top = level.getBlockState(surface);
                BlockState below = level.getBlockState(surface.below());
                if (!(top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) || top.is(Blocks.COARSE_DIRT))
                        || !below.isSolid()) continue;
                double r = level.random.nextDouble();
                if (r < 0.30) {
                    // 30%：草下沉一格、泥土翻上来
                    level.setBlock(surface, Blocks.DIRT.defaultBlockState(), 2);
                    level.setBlock(surface.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                } else if (r < 0.45) {
                    // 15%：深层泥土顶到地表形成土堆
                    if (level.getBlockState(surface.below(2)).is(Blocks.DIRT)) {
                        level.setBlock(surface, Blocks.DIRT.defaultBlockState(), 2);
                        level.setBlock(surface.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                        level.setBlock(surface.below(2), Blocks.DIRT.defaultBlockState(), 2);
                    } else {
                        level.setBlock(surface, Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
