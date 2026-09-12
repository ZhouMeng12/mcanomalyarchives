package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.StrangeTreeEntity;
import net.mcreator.mcanomalyarchives.network.StrangeTreeQuakePacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 怪树迁居（UO-011 实验4「风雨」）：
 * 树不喜欢周围有人活动/环境剧烈变动，会钻入地里，在远处寻找其它可扎根位置（只换位置，不夹人）。
 *
 * 触发条件（state 0 站立树，任一满足）：
 * - 久留：玩家在树 8 格内连续停留 ≥90 秒；
 * - 爆炸：树 16 格内发生爆炸；
 * - 大量破坏：树 16 格内 5 秒内被破坏 ≥8 个方块。
 *
 * 流程：清树干/树叶 + 翻土 + 钻地动画 + 地震 → 3 秒后选新位置（距原基座 20~40 格随机方向，
 * 避开玩家）→ 平移放置树干/树叶方块 → 实体移到新基座 → 恢复站立（state 0）。
 */
public class StrangeTreeRelocateHandler {

    /** 久留：玩家与树的水平最大距离（格） */
    private static final int LOITER_RANGE = 4;
    /** 久留：连续停留达到此秒数 → 迁居 */
    private static final int LOITER_SECONDS = 45;
    /** 爆炸：树与爆炸中心的最大距离（格） */
    private static final int EXPLOSION_RANGE = 16;
    /** 大量破坏：树与破坏点的最大距离（格） */
    private static final int MASS_BREAK_RANGE = 16;
    /** 大量破坏：窗口内破坏方块数达到此值 → 迁居 */
    private static final int MASS_BREAK_COUNT = 10;
    /** 大量破坏：统计窗口（tick，5 秒） */
    private static final int MASS_BREAK_WINDOW_TICKS = 100;

    /** 迁居距离范围（格） */
    private static final int RELOCATE_MIN_DIST = 20;
    private static final int RELOCATE_MAX_DIST = 40;
    /** 新位置与玩家的最小水平距离（格，避免迁到玩家脸上） */
    private static final int RELOCATE_PLAYER_MIN_DIST = 12;
    /** 找新位置的尝试次数 */
    private static final int RELOCATE_ATTEMPTS = 24;

    /** 钻地动画时长（tick） */
    private static final int BURROW_TICKS = 60;

    /** 扫描节流：每 20 tick 检查一次久留 */
    private static final int SCAN_INTERVAL = 20;

    /** 每棵树的久留开始时刻（gameTime）：树 UUID → 玩家开始持续在旁的时刻 */
    private static final Map<UUID, Long> loiterSince = new HashMap<>();
    /** 每棵树的破坏时间窗（gameTime）：树 UUID → 最近的破坏时刻列表 */
    private static final Map<UUID, Deque<Long>> breakTimes = new HashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.register(new StrangeTreeRelocateHandler());
    }

    // ==================== 触发源 1：久留 ====================

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        if (tick % SCAN_INTERVAL != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) player.level();
            // 只扫玩家附近的站立树
            List<StrangeTreeEntity> trees = level.getEntitiesOfClass(StrangeTreeEntity.class,
                    new AABB(player.blockPosition()).inflate(64.0));
            long now = level.getGameTime();
            for (StrangeTreeEntity tree : trees) {
                if (tree.isRemoved()) {
                    loiterSince.remove(tree.getUUID());
                    breakTimes.remove(tree.getUUID());
                    continue;
                }
                if (tree.getBurrowState() != 0) {
                    loiterSince.remove(tree.getUUID());
                    continue;
                }
                UUID treeId = tree.getUUID();
                boolean loitering = !level.getEntitiesOfClass(Player.class,
                        new AABB(tree.blockPosition()).inflate(LOITER_RANGE)).isEmpty();
                if (loitering) {
                    loiterSince.putIfAbsent(treeId, now);
                    if (now - loiterSince.get(treeId) >= (long) LOITER_SECONDS * 20) {
                        loiterSince.remove(treeId);
                        triggerRelocate(tree, level, player);
                    }
                } else {
                    loiterSince.remove(treeId);
                }
            }
        }
    }

    // ==================== 触发源 2：爆炸 ====================

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Vec3 center = event.getExplosion().center();
        for (StrangeTreeEntity tree : level.getEntitiesOfClass(StrangeTreeEntity.class,
                new AABB(BlockPos.containing(center)).inflate(EXPLOSION_RANGE))) {
            if (tree.isRemoved() || tree.getBurrowState() != 0) continue;
            triggerRelocate(tree, level, null);
        }
    }

    // ==================== 触发源 3：大量方块破坏 ====================

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        for (StrangeTreeEntity tree : level.getEntitiesOfClass(StrangeTreeEntity.class,
                new AABB(event.getPos()).inflate(MASS_BREAK_RANGE))) {
            if (tree.isRemoved() || tree.getBurrowState() != 0) continue;
            UUID treeId = tree.getUUID();
            Deque<Long> times = breakTimes.computeIfAbsent(treeId, k -> new ArrayDeque<>());
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > MASS_BREAK_WINDOW_TICKS) {
                times.pollFirst();
            }
            if (times.size() >= MASS_BREAK_COUNT) {
                breakTimes.remove(treeId);
                triggerRelocate(tree, level, event.getPlayer() instanceof Player p ? p : null);
            }
        }
    }

    // ==================== 迁居流程 ====================

    /** 执行迁居：清树+钻地动画 → 3 秒后选新位置重新扎根 */
    private void triggerRelocate(StrangeTreeEntity tree, ServerLevel level, Player avoidPlayer) {
        BlockPos oldBase = tree.blockPosition();
        // 记录旧基座地面层位置（用于平移方块）
        int oldY = groundY(level, oldBase.getX(), oldBase.getZ());
        BlockPos oldGround = new BlockPos(oldBase.getX(), oldY, oldBase.getZ());

        // 清方块 + 翻土 + 钻地动画 + 地震
        List<Map.Entry<BlockPos, BlockState>> cleared = StrangeTreeBurrowHandler.clearTreeBlocks(level, oldBase);
        StrangeTreeBurrowHandler.churnDirt(level, oldBase);
        tree.setBurrowState(1);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(tree, new StrangeTreeQuakePacket(0.3f, 50));

        McanomalyarchivesMod.queueServerWork(BURROW_TICKS, () -> {
            if (tree.isRemoved()) return;
            if (tree.getBurrowState() != 1) return;
            // 选新位置
            BlockPos newGround = pickNewGround(level, oldGround, avoidPlayer);
            if (newGround == null) {
                // 找不到合适位置：原地恢复站立
                restoreAt(tree, level, oldGround);
                return;
            }
            // 平移放置树干/树叶方块
            int dx = newGround.getX() - oldGround.getX();
            int dy = newGround.getY() - oldGround.getY();
            int dz = newGround.getZ() - oldGround.getZ();
            for (Map.Entry<BlockPos, BlockState> entry : cleared) {
                BlockPos p = entry.getKey().offset(dx, dy, dz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, entry.getValue(), 2);
                }
            }
            // 实体移到新基座、恢复站立
            restoreAt(tree, level, newGround);
        });
    }

    /** 在指定基座恢复站立：实体回地面方块顶部、可见、无碰撞解除、state 0 */
    private static void restoreAt(StrangeTreeEntity tree, ServerLevel level, BlockPos ground) {
        // ground 是地面方块所在的 y 层，实体脚底应站在其上方一格
        tree.setPos(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5);
        tree.setInvisible(false);
        tree.noPhysics = false;
        tree.setBurrowState(0);
    }

    /** 选新位置：随机方向 + 距离 20~40 格，地面层可站立、避开玩家 */
    private static BlockPos pickNewGround(ServerLevel level, BlockPos from, Player avoidPlayer) {
        for (int i = 0; i < RELOCATE_ATTEMPTS; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            int dist = RELOCATE_MIN_DIST + level.random.nextInt(RELOCATE_MAX_DIST - RELOCATE_MIN_DIST + 1);
            int x = from.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = from.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = groundY(level, x, z);
            BlockPos ground = new BlockPos(x, y, z);
            if (avoidPlayer != null && ground.distSqr(avoidPlayer.blockPosition()) < (double) RELOCATE_PLAYER_MIN_DIST * RELOCATE_PLAYER_MIN_DIST) {
                continue;
            }
            if (isStandable(level, ground)) {
                return ground;
            }
        }
        return null;
    }

    /** 判断脚下位置可站立（ground 为地面方块，站其上方） */
    private static boolean isStandable(ServerLevel level, BlockPos ground) {
        return level.getBlockState(ground).isSolid()
                && level.getBlockState(ground.above()).isAir()
                && level.getBlockState(ground.above(2)).isAir();
    }

    /**
     * 地面层高度：从 WORLD_SURFACE（最高非空气）向下扫描，跳过树叶/原木（树冠、树干），
     * 找到真正的"地表"方块 y。
     */
    private static int groundY(ServerLevel level, int x, int z) {
        int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
        while (y > level.getMinBuildHeight()) {
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(new BlockPos(x, y, z));
            if (st.isAir()) {
                y--;
                continue;
            }
            if (st.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
                    || st.is(net.minecraft.tags.BlockTags.LOGS)) {
                y--;
                continue;
            }
            return y;
        }
        return y;
    }
}
