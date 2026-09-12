package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.StrangeTreeEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 怪树埋伏-偷袭链（UO-011 第 2、3 阶段）：
 * - 树钻地潜伏（state 2）后锁定砍树的玩家，地底向玩家最近砍树位置追踪（约 3 格/秒）；
 * - 到达埋伏点 5 格内进入埋伏（AMBUSH），不再移动；
 * - 埋伏中该玩家再砍 1 棵原木 → 从玩家背后 5 格钻出（zuanchu 动画，树面向玩家）→
 *   jiaren 镰刀夹击动画 → 无视护甲一击必杀（genericKill + kill 兜底）；
 * - 夹空/流程结束/潜伏 90 秒无砍树 → 恢复站立（还原树干/树叶方块、实体回原位）。
 */
public class StrangeTreeAmbushHandler {

    /** 追踪范围：玩家砍树点与树的水平最大距离（格） */
    private static final int TRACK_RANGE = 32;
    /** 地底追踪速度（格/秒，泥土/草地等普通地表，对应文档 3.1 m/s） */
    private static final double TRACK_SPEED_BPS = 3.1;
    /** 石头类地表穿透速度（文档实验5：石头 1.5 m/s） */
    private static final double STONE_SPEED_BPS = 1.5;
    /** 黑曜石地表穿透速度（文档实验5：黑曜石 0.4 m/s） */
    private static final double OBSIDIAN_SPEED_BPS = 0.4;
    /** 基岩：不可穿透（速度 0，绕行） */
    private static final double BEDROCK_SPEED_BPS = 0.0;
    /** 进入埋伏的距离（格） */
    private static final int AMBUSH_DIST = 5;
    /** 潜伏后无任何砍树活动 → 恢复站立（秒） */
    private static final int IDLE_RESET_SECONDS = 90;
    /** 目标玩家离开此距离（格）→ 放弃目标回待机 */
    private static final int TARGET_ABANDON_DIST = 64;

    /** zuanchu 动画时长（tick） */
    private static final int ZUANCHU_TICKS = 30;
    /** jiaren 动画时长（tick） */
    private static final int JIAREN_TICKS = 40;
    /** 夹击秒杀判定在 jiaren 中的时刻（tick，1.08 秒 ≈ 21.6 tick，取整 22） */
    private static final int KILL_AT_TICK = 21;

    /** 埋伏时的震颤：小幅度（区别于钻地/钻出的大地震） */
    private static final float AMBUSH_QUAKE_AMP = 0.12f;
    private static final int AMBUSH_QUAKE_TICKS = 30;
    /** 钻出时的震颤幅度 */
    private static final float EMERGE_QUAKE_AMP = 0.35f;
    private static final int EMERGE_QUAKE_TICKS = 60;
    /** 地底移动持续震颤（每 5 tick 移动时刷新） */
    private static final float MOVE_QUAKE_AMP = 0.08f;
    private static final int MOVE_QUAKE_TICKS = 10;

    /** 追杀模式：反复夹击的间隔（tick） */
    private static final int CHASE_ATTACK_INTERVAL = 30; // 1.5 秒
    /** 追杀模式：玩家距离 ≤ 此值（格）→ 击杀 */
    private static final int CHASE_KILL_DIST = 5;
    /** 寻找可钻出泥土的搜索半径（格） */
    private static final int DIRT_SEARCH_RADIUS = 16;
    /** 钻出点与玩家的保底水平距离（格，避免钻到玩家脚边） */
    private static final int MIN_EMERGE_DIST = 4;

    /** 追杀中的树：树 UUID → 目标玩家 UUID */
    private static final Map<UUID, UUID> chasingTrees = new HashMap<>();

    /** 潜伏树状态表：树 UUID → 状态 */
    private static final Map<UUID, AmbushState> ambushes = new HashMap<>();
    /** 偷袭中/恢复站立时需要回到的原基座：树 UUID → 地表位置 */
    private static final Map<UUID, BlockPos> restoreGrounds = new HashMap<>();

    private static final class AmbushState {
        /** 树所在服务端世界 */
        final ServerLevel level;
        /** 锁定的目标玩家（可空） */
        UUID targetUuid;
        /** 埋伏点：玩家最近砍树位置 */
        BlockPos ambushPos;
        /** 是否已进入埋伏（到达埋伏点 5 格内） */
        boolean ambushed;
        /** 原基座（地表）位置：恢复站立时树回到这里 */
        final BlockPos groundPos;
        /** 最近一次砍树活动时刻（gameTime），用于空闲超时 */
        long lastBreakGameTime;

        AmbushState(ServerLevel level, BlockPos groundPos, long now) {
            this.level = level;
            this.groundPos = groundPos;
            this.lastBreakGameTime = now;
        }
    }

    public static void init() {
        NeoForge.EVENT_BUS.register(new StrangeTreeAmbushHandler());
    }

    // ==================== 砍树事件：锁定目标 / 触发钻出 ====================

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!StrangeTreeBurrowHandler.isLog(event.getState())) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        ServerLevel level = (ServerLevel) event.getLevel();
        long now = level.getGameTime();

        // 找玩家 32 格内所有潜伏（state 2）的怪树
        List<StrangeTreeEntity> trees = level.getEntitiesOfClass(StrangeTreeEntity.class,
                new AABB(event.getPos()).inflate(TRACK_RANGE));
        for (StrangeTreeEntity tree : trees) {
            if (tree.getBurrowState() != 2 || tree.isRemoved()) continue;
            UUID treeId = tree.getUUID();
            AmbushState st = ambushes.computeIfAbsent(treeId, k -> new AmbushState(level, findGroundPos(level, tree), now));
            st.lastBreakGameTime = now;

            // 未锁定目标或就是当前玩家 → 锁定，埋伏点更新为最新砍树位置（自动追踪）
            if (st.targetUuid == null || st.targetUuid.equals(player.getUUID())) {
                st.targetUuid = player.getUUID();
                st.ambushPos = event.getPos().immutable();
                if (st.ambushed) {
                    // 已埋伏 + 该玩家再砍 1 棵 → 尝试钻出偷袭
                    // 钻出点必须在玩家背后/侧面（视野盲区）的泥土上；找不到则保持潜伏等待
                    BlockPos emergePos = findEmergenceDirt(level, player.blockPosition(), player.getLookAngle());
                    if (emergePos == null) {
                        // 背后/侧面无泥土可钻出：树继续潜伏，等待玩家走到泥土旁；小震颤提示"找不到出口"
                        sendTrackingQuake(tree, AMBUSH_QUAKE_AMP, AMBUSH_QUAKE_TICKS);
                        st.lastBreakGameTime = now;
                        continue;
                    }
                    ambushes.remove(treeId);
                    triggerEmergence(tree, player, level, st.groundPos, emergePos);
                }
            }
        }
    }

    // ==================== 服务端 tick：地底追踪 / 超时 / 登记 ====================

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        if (tick % 5 != 0) return;

        // 清理失效条目 + 目标失效 + 追踪移动 + 空闲超时
        Iterator<Map.Entry<UUID, AmbushState>> it = ambushes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AmbushState> e = it.next();
            AmbushState st = e.getValue();
            ServerLevel level = st.level;
            StrangeTreeEntity tree = level.getEntity(e.getKey()) instanceof StrangeTreeEntity t ? t : null;
            if (tree == null || tree.isRemoved() || tree.getBurrowState() != 2) {
                it.remove();
                continue;
            }
            long now = level.getGameTime();

            // 目标失效：登出或离开太远 → 放弃目标回待机
            if (st.targetUuid != null) {
                ServerPlayer target = level.getServer().getPlayerList().getPlayer(st.targetUuid);
                if (target == null || target.level() != level
                        || target.distanceToSqr(tree.getX(), tree.getY(), tree.getZ()) > (double) TARGET_ABANDON_DIST * TARGET_ABANDON_DIST) {
                    st.targetUuid = null;
                    st.ambushed = false;
                    st.lastBreakGameTime = now;
                }
            }

            // 地底追踪：向埋伏点移动（保持地下隐藏高度），速度受地表材质影响（实验5 材质穿透）
            if (!st.ambushed) {
                // 保底：树已潜到玩家脚下（≤5 格）也算埋伏完成，避免玩家跑动导致永远追不上
                double playerDistSqr = st.targetUuid != null
                        ? playerDistSqr(level, st.targetUuid, tree) : Double.MAX_VALUE;
                if (st.ambushPos != null && playerDistSqr > (double) AMBUSH_DIST * AMBUSH_DIST) {
                    double dx = st.ambushPos.getX() + 0.5 - tree.getX();
                    double dz = st.ambushPos.getZ() + 0.5 - tree.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > AMBUSH_DIST) {
                        double oldX = tree.getX(), oldZ = tree.getZ();
                        // 当前列地表材质决定的穿透速度（基岩=0 不可穿）
                        double speed = penetrationSpeed(level, tree.blockPosition());
                        if (speed <= 0) {
                            // 基岩阻挡：尝试垂直方向绕行（实验5：树绕过基岩）
                            double px = -dz / dist, pz = dx / dist; // 垂直方向 1
                            double nx = dz / dist, nz = -dx / dist; // 垂直方向 2
                            double sideStep = TRACK_SPEED_BPS / 20.0 * 5;
                            if (canPenetrate(level, tree.blockPosition().offset((int) Math.round(px * sideStep), 0, (int) Math.round(pz * sideStep)))) {
                                tree.setPos(tree.getX() + px * sideStep, tree.getY(), tree.getZ() + pz * sideStep);
                            } else if (canPenetrate(level, tree.blockPosition().offset((int) Math.round(nx * sideStep), 0, (int) Math.round(nz * sideStep)))) {
                                tree.setPos(tree.getX() + nx * sideStep, tree.getY(), tree.getZ() + nz * sideStep);
                            }
                            // 两侧都挡住：原地等待（埋伏点更新后重试）
                        } else {
                            double step = speed / 20.0 * 5; // 每 5 tick 的步进
                            if (step >= dist) {
                                tree.setPos(st.ambushPos.getX() + 0.5, tree.getY(), st.ambushPos.getZ() + 0.5);
                            } else {
                                tree.setPos(tree.getX() + dx / dist * step, tree.getY(), tree.getZ() + dz / dist * step);
                            }
                        }
                        // 地底移动持续震动：树确实发生了位移（含绕行）时给追踪玩家发小震颤
                        if (tree.getX() != oldX || tree.getZ() != oldZ) {
                            sendTrackingQuake(tree, MOVE_QUAKE_AMP, MOVE_QUAKE_TICKS);
                        }
                    }
                }
                if (st.ambushPos != null) {
                    double dpx = st.ambushPos.getX() + 0.5 - tree.getX();
                    double dpz = st.ambushPos.getZ() + 0.5 - tree.getZ();
                    if (dpx * dpx + dpz * dpz <= (double) AMBUSH_DIST * AMBUSH_DIST
                            || playerDistSqr <= (double) AMBUSH_DIST * AMBUSH_DIST) {
                        st.ambushed = true; // 到达 → 埋伏
                        // 埋伏：小幅度震颤提示玩家（区别于钻地/钻出的大地震）
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(tree,
                                new net.mcreator.mcanomalyarchives.network.StrangeTreeQuakePacket(AMBUSH_QUAKE_AMP, AMBUSH_QUAKE_TICKS));
                    }
                }
            }

            // 空闲超时：潜伏后长时间无砍树 → 恢复站立
            if (now - st.lastBreakGameTime > (long) IDLE_RESET_SECONDS * 20) {
                restoreStanding(tree, level, st.groundPos);
                it.remove();
            }
        }

        // 每 20 tick 登记玩家附近的潜伏树（惰性发现，避免漏掉无砍树事件的树）
        if (tick % 20 == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                ServerLevel level = (ServerLevel) player.level();
                List<StrangeTreeEntity> trees = level.getEntitiesOfClass(StrangeTreeEntity.class,
                        new AABB(player.blockPosition()).inflate(TRACK_RANGE));
                for (StrangeTreeEntity tree : trees) {
                    if (tree.getBurrowState() != 2 || tree.isRemoved()) continue;
                    ambushes.computeIfAbsent(tree.getUUID(), k -> new AmbushState(level, findGroundPos(level, tree), level.getGameTime()));
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // 目标下线：清目标（树回待机，后续超时恢复站立）
        UUID uuid = player.getUUID();
        for (AmbushState st : ambushes.values()) {
            if (uuid.equals(st.targetUuid)) {
                st.targetUuid = null;
                st.ambushed = false;
            }
        }
        // 追杀中的树：目标下线 → 终止追杀（下一轮调度检测到 chasingTrees 被移除即恢复站立）
        chasingTrees.entrySet().removeIf(e -> uuid.equals(e.getValue()));
    }

    /**
     * 补种免疫强化：某玩家累计补种达标后，令其锁定的所有怪树彻底放弃本次袭击。
     * 树立即恢复站立（还原方块、实体回原位、state 0），并从埋伏表中移除。
     * 追杀模式中的树也一并终止。
     */
    public static void abandonPlayer(Player player) {
        UUID uuid = player.getUUID();
        Iterator<Map.Entry<UUID, AmbushState>> it = ambushes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AmbushState> e = it.next();
            AmbushState st = e.getValue();
            if (!uuid.equals(st.targetUuid)) continue;
            StrangeTreeEntity tree = st.level.getEntity(e.getKey()) instanceof StrangeTreeEntity t ? t : null;
            it.remove();
            if (tree == null || tree.isRemoved()) continue;
            // 只处理仍潜伏（state 2）的树；钻出/夹击流程中（3/4）不打断
            if (tree.getBurrowState() == 2) {
                restoreStanding(tree, st.level, st.groundPos);
            }
        }
        // 终止追杀：该玩家作为目标的追杀树立即恢复站立（种树苗停止追杀）
        Iterator<Map.Entry<UUID, UUID>> cit = chasingTrees.entrySet().iterator();
        while (cit.hasNext()) {
            Map.Entry<UUID, UUID> e = cit.next();
            if (!uuid.equals(e.getValue())) continue;
            cit.remove();
            if (player.getServer() == null) continue;
            for (ServerLevel sl : player.getServer().getAllLevels()) {
                if (sl.getEntity(e.getKey()) instanceof StrangeTreeEntity tree && !tree.isRemoved()
                        && (tree.getBurrowState() == 3 || tree.getBurrowState() == 4)) {
                    restoreStanding(tree, sl, findGroundPos(sl, tree));
                    break;
                }
            }
        }
    }

    // ==================== 钻出偷袭 ====================

    /**
     * 钻出偷袭：emergePos 已由 onBlockBreak 预先选好（玩家背后/侧面泥土，视野盲区）。
     * 泥土距玩家 ≤5 格 → 正常钻出夹击秒杀；>5 格 → 追杀模式（反复夹击直到玩家靠近/种树苗）。
     */
    private void triggerEmergence(StrangeTreeEntity tree, Player player, ServerLevel level, BlockPos groundPos, BlockPos emergePos) {
        // 记录恢复站立时的原基座
        restoreGrounds.put(tree.getUUID(), groundPos);
        BlockPos playerPos = player.blockPosition();

        // 追杀判定：钻出点（背后/侧面泥土）距玩家 >5 格 → 追杀模式
        double emergeDist = Math.sqrt(emergePos.distSqr(playerPos));
        boolean chaseMode = emergeDist > CHASE_KILL_DIST;

        // 复位实体：回到地表（站在地面方块顶部）、可见、无碰撞、面向玩家
        tree.setPos(emergePos.getX() + 0.5, emergePos.getY() + 1, emergePos.getZ() + 0.5);
        tree.setInvisible(false);
        tree.noPhysics = false;
        float yaw = (float) (Math.toDegrees(Math.atan2(player.getZ() - tree.getZ(), player.getX() - tree.getX())) - 90.0);
        tree.setYRot(yaw);
        tree.setYHeadRot(yaw);
        tree.setYBodyRot(yaw);
        tree.setBurrowState(3); // 钻出中：zuanchu 动画
        sendTrackingQuake(tree, EMERGE_QUAKE_AMP, EMERGE_QUAKE_TICKS);

        UUID targetUuid = player.getUUID();
        // 链式：钻出 1.5s → 夹击中 → 判定
        McanomalyarchivesMod.queueServerWork(ZUANCHU_TICKS, () -> {
            if (!tree.isRemoved() && tree.getBurrowState() == 3) {
                tree.setBurrowState(4); // 夹击中：jiaren 动画
            }
        });
        if (chaseMode) {
            // 追杀模式：反复夹击，直到玩家距离 ≤5 击杀；种树苗（abandonPlayer）终止
            chasingTrees.put(tree.getUUID(), targetUuid);
            scheduleChaseAttack(tree, level, groundPos, targetUuid, 0);
        } else {
            // 正常模式：钻出 → 夹击 → 必杀 → 恢复站立
            McanomalyarchivesMod.queueServerWork(ZUANCHU_TICKS + KILL_AT_TICK, () -> {
                if (!tree.isRemoved() && tree.getBurrowState() == 4) {
                    killTarget(level, targetUuid);
                }
            });
            McanomalyarchivesMod.queueServerWork(ZUANCHU_TICKS + JIAREN_TICKS, () -> {
                if (!tree.isRemoved() && tree.getBurrowState() == 4) {
                    restoreStanding(tree, level, groundPos);
                }
            });
        }
    }

    /** 追杀模式：反复播放 jiaren 夹击动画，每轮判定玩家距离，≤5 格击杀；玩家种树苗则终止 */
    private void scheduleChaseAttack(StrangeTreeEntity tree, ServerLevel level, BlockPos groundPos, UUID targetUuid, int round) {
        McanomalyarchivesMod.queueServerWork(CHASE_ATTACK_INTERVAL, () -> {
            if (tree.isRemoved()) return;
            // 玩家种树苗已终止（chasingTrees 被移除）→ 恢复站立
            if (!chasingTrees.containsKey(tree.getUUID())) {
                restoreStanding(tree, level, groundPos);
                return;
            }
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetUuid);
            if (target == null || target.level() != level || !target.isAlive()) {
                chasingTrees.remove(tree.getUUID());
                restoreStanding(tree, level, groundPos);
                return;
            }
            double dist = Math.sqrt(target.distanceToSqr(tree.getX(), tree.getY(), tree.getZ()));
            if (dist <= CHASE_KILL_DIST) {
                chasingTrees.remove(tree.getUUID());
                tree.setBurrowState(4); // 夹击动画
                McanomalyarchivesMod.queueServerWork(KILL_AT_TICK, () -> {
                    if (!tree.isRemoved() && tree.getBurrowState() == 4) {
                        killTarget(level, targetUuid);
                    }
                });
                McanomalyarchivesMod.queueServerWork(KILL_AT_TICK + JIAREN_TICKS, () -> {
                    if (!tree.isRemoved() && tree.getBurrowState() == 4) {
                        restoreStanding(tree, level, groundPos);
                    }
                });
            } else {
                // 未靠近：再次播放夹击动画，继续追杀（每轮动画 40 tick）
                tree.setBurrowState(4);
                sendTrackingQuake(tree, EMERGE_QUAKE_AMP, 20);
                McanomalyarchivesMod.queueServerWork(JIAREN_TICKS, () -> {
                    if (!tree.isRemoved() && tree.getBurrowState() == 4) {
                        tree.setBurrowState(3); // 短暂回钻出姿态，准备下一轮
                        scheduleChaseAttack(tree, level, groundPos, targetUuid, round + 1);
                    }
                });
            }
        });
    }

    /** 给追踪该实体的所有玩家发屏幕震颤 */
    private static void sendTrackingQuake(StrangeTreeEntity tree, float amp, int ticks) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(tree,
                new net.mcreator.mcanomalyarchives.network.StrangeTreeQuakePacket(amp, ticks));
    }

    /** 腰斩伤害类型（死亡消息："%1$s被腰斩了"） */
    private static final ResourceKey<DamageType> WAIST_CUT_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "waistcut"));

    /** 镰刀夹击秒杀：直接击杀被锁定的玩家（无视距离/护甲/血量，图腾兜底补 kill） */
    private void killTarget(ServerLevel level, UUID targetUuid) {
        if (targetUuid == null) return;
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetUuid);
        if (target == null || target.level() != level || target.isRemoved() || !target.isAlive()) return;
        // 一击必杀：用自定义"腰斩"伤害源（保留死亡消息），创造模式/无敌帧等 hurt 无效时 setHealth 兜底
        DamageSource waist = level.damageSources().source(WAIST_CUT_KEY);
        target.hurt(waist, Float.MAX_VALUE);
        if (target.isAlive()) {
            target.kill(); // 不死图腾等兜底
        }
        if (target.isAlive()) {
            target.setHealth(0.0F); // 最终兜底：无视一切直接清零
        }
    }

    // ==================== 恢复站立 ====================

    /** 恢复站立：还原树干/树叶方块、实体回原位、可见、state 0 */
    private static void restoreStanding(StrangeTreeEntity tree, ServerLevel level, BlockPos groundPos) {
        // 还原方块（钻地时记录的）
        List<Map.Entry<BlockPos, BlockState>> cleared = StrangeTreeBurrowHandler.takeClearedBlocks(tree.getUUID());
        if (cleared != null) {
            for (Map.Entry<BlockPos, BlockState> entry : cleared) {
                if (level.getBlockState(entry.getKey()).isAir()) {
                    level.setBlock(entry.getKey(), entry.getValue(), 2);
                }
            }
        }
        // 实体回原位：优先用钻地瞬间记录的原基座（树追踪移动后 groundPos 可能已偏离），
        // y 用基座自身（blockPosition 即站立脚底层），避免 +0.5 造成埋地/悬空
        BlockPos base = StrangeTreeBurrowHandler.takeBurrowPosition(tree.getUUID());
        if (base == null) base = groundPos;
        if (base != null) {
            tree.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        }
        tree.setInvisible(false);
        tree.noPhysics = false;
        tree.setBurrowState(0);
        restoreGrounds.remove(tree.getUUID());
    }

    // ==================== 工具 ====================

    /** 地表高度（WORLD_SURFACE） */
    private static int surfaceY(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
    }

    /**
     * 地面层高度：从 WORLD_SURFACE（最高非空气）向下扫描，跳过树叶/原木（树冠、树干），
     * 找到真正的"地表"方块 y（草方块/泥土/石头等）。用于泥土钻出判定——
     * 否则在树林里 surfaceY 会停在树冠树叶上，永远判定不出泥土。
     */
    private static int groundY(ServerLevel level, int x, int z) {
        int y = surfaceY(level, x, z);
        while (y > level.getMinBuildHeight()) {
            BlockState st = level.getBlockState(new BlockPos(x, y, z));
            if (st.isAir()) {
                y--;
                continue;
            }
            if (st.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
                    || st.is(BlockTags.LOGS)) {
                y--;
                continue;
            }
            return y;
        }
        return y;
    }

    /** 目标玩家到树的水平距离平方（玩家不在线/不同世界 → 无穷大） */
    private static double playerDistSqr(ServerLevel level, UUID playerUuid, StrangeTreeEntity tree) {
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerUuid);
        if (target == null || target.level() != level) return Double.MAX_VALUE;
        double dx = target.getX() - tree.getX();
        double dz = target.getZ() - tree.getZ();
        return dx * dx + dz * dz;
    }

    /** 判断脚下位置可站立 */
    private static boolean isStandable(ServerLevel level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y - 1, z)).isSolid();
    }

    /** 找树钻地前的原基座地表位置（地面层高度） */
    private static BlockPos findGroundPos(ServerLevel level, StrangeTreeEntity tree) {
        BlockPos p = tree.blockPosition();
        int y = groundY(level, p.getX(), p.getZ());
        return new BlockPos(p.getX(), y, p.getZ());
    }

    // ==================== 材质穿透（实验5 缠根） ====================

    /**
     * 树所在列地表材质决定的穿透速度（格/秒）：
     * 基岩=0（不可穿透，绕行）｜黑曜石=0.4｜石头类=1.5｜其余（泥土/草/沙）=3.1
     */
    private static double penetrationSpeed(ServerLevel level, BlockPos pos) {
        BlockState surface = level.getBlockState(new BlockPos(pos.getX(), groundY(level, pos.getX(), pos.getZ()), pos.getZ()));
        if (surface.is(Blocks.BEDROCK)) return BEDROCK_SPEED_BPS;
        if (surface.is(Blocks.OBSIDIAN)) return OBSIDIAN_SPEED_BPS;
        if (surface.is(BlockTags.BASE_STONE_OVERWORLD) || surface.is(Blocks.COBBLESTONE)) return STONE_SPEED_BPS;
        return TRACK_SPEED_BPS;
    }

    /** 该列地表是否可穿透（非基岩） */
    private static boolean canPenetrate(ServerLevel level, BlockPos pos) {
        return penetrationSpeed(level, pos) > 0;
    }

    // ==================== 泥土钻出（实验5 材质限制） ====================

    /** 判断某位置地面层是否为泥土类（草方块/泥土/灰化土/土径/菌丝等，树只能在这些方块上钻出） */
    private static boolean isDirt(ServerLevel level, BlockPos pos) {
        int y = groundY(level, pos.getX(), pos.getZ());
        BlockState state = level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.DIRT_PATH);
    }

    /**
     * 在玩家背后/侧面半区（与水平视线夹角 >90°，即视野盲区）内搜索可钻出泥土，
     * 取距离玩家最近的一个；无则返回 null。
     * 树只能从泥土钻出（实验5：石头/黑曜石/基岩不能钻出），但地底移动时可以穿过它们。
     */
    private static BlockPos findEmergenceDirt(ServerLevel level, BlockPos playerPos, Vec3 lookDir) {
        // 水平视线方向
        double lx = lookDir.x, lz = lookDir.z;
        double lLen = Math.sqrt(lx * lx + lz * lz);
        if (lLen < 1e-4) {
            lx = 0;
            lz = 1;
        } else {
            lx /= lLen;
            lz /= lLen;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -DIRT_SEARCH_RADIUS; dx <= DIRT_SEARCH_RADIUS; dx++) {
            for (int dz = -DIRT_SEARCH_RADIUS; dz <= DIRT_SEARCH_RADIUS; dz++) {
                // 排除玩家自身格
                if (dx == 0 && dz == 0) continue;
                int x = playerPos.getX() + dx;
                int z = playerPos.getZ() + dz;
                int y = groundY(level, x, z);
                BlockPos candidate = new BlockPos(x, y, z);
                if (!isDirt(level, candidate)) continue;
                if (!isStandable(level, x, y, z)) continue;
                // 视野盲区判定：与视线夹角 >90°（点积 < 0）才算背后/侧面，正面不钻出
                double dot = dx * lx + dz * lz;
                if (dot >= 0) continue;
                double dist = candidate.distSqr(playerPos);
                // 保底距离：钻出点至少离玩家 3 格远，不钻到脚边
                if (dist < (double) MIN_EMERGE_DIST * MIN_EMERGE_DIST) continue;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }
        return best;
    }
}
