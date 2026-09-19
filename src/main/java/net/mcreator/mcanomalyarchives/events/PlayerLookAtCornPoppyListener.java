package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.mcreator.mcanomalyarchives.block.CornPoppyBlock;
import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;
import net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerLookAtCornPoppyListener {
    private static final int TICKS_PER_SECOND = 20;
    private static final int ADDICTION_TRIGGER_TIME = 10 * TICKS_PER_SECOND;
    private static final int LEVEL_UP_INTERVAL = 10 * TICKS_PER_SECOND;
    private static final int ADDICTION_DURATION = 30 * TICKS_PER_SECOND;
    private static final int MAX_ADDICTION_LEVEL = 10;
    private static final int MAX_SAD_LEVEL = 5;
    private static final int SAD_BASE_DURATION = 30 * TICKS_PER_SECOND;
    private static final int SAD_DURATION_PER_LEVEL = 10 * TICKS_PER_SECOND;
    private static final int IDLE_CLEANUP_TIME = 60 * TICKS_PER_SECOND;
    private static final int DEATH_LOOK_TIME = 10 * TICKS_PER_SECOND;
    private static final int DAMAGE_TICK_INTERVAL = 40;
    private static final int SAD_BUFFER_TIME = 1 * TICKS_PER_SECOND;

    /** 看红伪云：最远 24 格内的云才算（云很大，太远就谈不上"盯着看"） */
    private static final double CLOUD_VIEW_RANGE = 24.0D;
    /** 视线与云方向的夹角阈值：约 30°（云体宽 4 格，给宽松一点才不会因为偏一点就断） */
    private static final double CLOUD_COS_LIMIT = Math.cos(Math.toRadians(30.0D));

    private static final TagKey<EntityType<?>> NPC_TAG = TagKey.create(
        Registries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "npc")
    );

    private static class PlayerPoppyState {
        int lookingTime;
        int addictionLevel;
        boolean hasAddiction;
        long lastLookTime;
        int sadLevel;
        boolean shouldApplySad;
        int timeAfterMaxLevel;
        int damageTickCounter;
        Vec3 frozenPosition;
        float frozenYaw;
        float frozenPitch;
        boolean isFrozen;
        int bufferTime;
        boolean inBufferPeriod;
        double originalMoveSpeed;
        boolean speedModified;
        BlockPos poppyPos;
        /**
         * 正在看的是**红伪云**而不是虞美人。
         *
         * 红伪云接进了同一套上瘾系统（作者 2026-09-18：「变红后看这个红伪云会上瘾」）：
         * 盯满 10 秒上瘾、每 10 秒升级、10 级定身再 10 秒丧命、移开视线转悲伤 —— 全部沿用。
         * 这个标记只用来区分"看的是云"，需要真去改方块的地方就不会误伤
         * （比如安布拉那个把虞美人看成枯死花的特例）。
         */
        boolean watchingCloud;
    }

    private static final Map<UUID, PlayerPoppyState> playerStates = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> currentWatchingPoppy = new ConcurrentHashMap<>();

    // NPC 状态独立管理
    private static final Map<UUID, PlayerPoppyState> npcStates = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> npcCurrentWatchingPoppy = new ConcurrentHashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.register(new PlayerLookAtCornPoppyListener());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // 节流：每 5 tick 执行一次（判定阈值以真实 tick 计，计数按 5 累加对齐）
        if (event.getServer().getTickCount() % 5 != 0) return;
        // 处理玩家
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            PlayerPoppyState state = playerStates.computeIfAbsent(playerId, k -> new PlayerPoppyState());

            boolean isLookingAtPoppy = isPlayerLookingAtCornPoppy(player);
            // 红伪云走同一套上瘾逻辑：不盯虞美人时再看它在不在视线里
            boolean isLookingAtCloud = !isLookingAtPoppy && isPlayerLookingAtRedCloud(player);
            state.watchingCloud = isLookingAtCloud;
            long currentTime = System.currentTimeMillis();

            if (isLookingAtPoppy || isLookingAtCloud) {
                state.lastLookTime = currentTime;
                state.poppyPos = currentWatchingPoppy.get(playerId);
                handleLookingAtPoppy(player, state);
            } else {
                currentWatchingPoppy.remove(playerId);
                state.poppyPos = null;
                handleNotLookingAtPoppy(player, state);
            }

            if (state.isFrozen && state.frozenPosition != null) {
                player.setPos(state.frozenPosition);
                player.setYRot(state.frozenYaw);
                player.setXRot(state.frozenPitch);
                player.setDeltaMovement(Vec3.ZERO);
            }

            if (!player.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
                if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                }
                if (player.hasEffect(MobEffects.WEAKNESS)) {
                    player.removeEffect(MobEffects.WEAKNESS);
                }
            }

            cleanupIdleState(playerId, state, playerStates);
        }

        // 处理NPC
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity livingEntity)) continue;
                if (!(livingEntity.getType().is(NPC_TAG) && !(livingEntity instanceof Player))) continue;
                UUID npcId = livingEntity.getUUID();
                PlayerPoppyState npcState = npcStates.computeIfAbsent(npcId, k -> new PlayerPoppyState());

                boolean isLookingAtPoppy = isEntityLookingAtCornPoppy(livingEntity);
                // NPC 也会盯着红伪云上瘾（同一套逻辑；只是它们不会去看方块那一套特例）
                boolean isLookingAtCloud = !isLookingAtPoppy && isEntityLookingAtRedCloud(livingEntity);
                npcState.watchingCloud = isLookingAtCloud;
                long currentTime = System.currentTimeMillis();

                if (isLookingAtPoppy || isLookingAtCloud) {
                    npcState.lastLookTime = currentTime;
                    npcState.poppyPos = npcCurrentWatchingPoppy.get(npcId);
                    handleNPCLookingAtPoppy(livingEntity, npcState);
                } else {
                    npcCurrentWatchingPoppy.remove(npcId);
                    npcState.poppyPos = null;
                    handleNPCNotLookingAtPoppy(livingEntity, npcState);
                }

                if (npcState.isFrozen && npcState.frozenPosition != null) {
                    livingEntity.setPos(npcState.frozenPosition);
                    livingEntity.setYRot(npcState.frozenYaw);
                    livingEntity.setXRot(npcState.frozenPitch);
                    livingEntity.setDeltaMovement(Vec3.ZERO);
                }

                if (!livingEntity.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
                    if (livingEntity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                        livingEntity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    }
                    if (livingEntity.hasEffect(MobEffects.WEAKNESS)) {
                        livingEntity.removeEffect(MobEffects.WEAKNESS);
                    }
                }

                cleanupIdleState(npcId, npcState, npcStates);
            }
        }
    }

    // ========== 玩家相关方法 (保持不变) ==========

    /**
     * 玩家是不是在盯着红伪云看。
     *
     * <p>和虞美人不同，这里不能靠"沿着视线打方块"——云是实体。做法是：
     * 取视线方向与"眼睛→云体中心"的夹角，夹角小于 {@link #CLOUD_VIEW_ANGLE} 就算在看它
     * （云体宽 4 格，所以角度阈值给得比较宽松），取最近的那一只。
     *
     * <p>命中后把云的方块坐标写进 {@code currentWatchingPoppy}：这样上瘾系统里的
     * "定身时强制面朝目标"（{@code getLookAtAngles}）对云也照常生效，不用改那套逻辑。
     */
    private static boolean isPlayerLookingAtRedCloud(Player player) {
        SmallStangeCloudEntity cloud = findRedCloudInView(player.level(), player.getEyePosition(1f), player.getViewVector(1f));
        if (cloud == null) {
            return false;
        }
        currentWatchingPoppy.put(player.getUUID(), cloud.blockPosition());
        return true;
    }

    private static boolean isEntityLookingAtRedCloud(LivingEntity entity) {
        SmallStangeCloudEntity cloud = findRedCloudInView(entity.level(), entity.getEyePosition(1f), entity.getViewVector(1f));
        if (cloud == null) {
            return false;
        }
        npcCurrentWatchingPoppy.put(entity.getUUID(), cloud.blockPosition());
        return true;
    }

    /**
     * 视线锥里最近的一只**红伪云**（够近 + 夹角够小）。
     *
     * 红伪云不是独立生物：它是 SmallStangeCloudEntity 的变种，靠 isRed() 这个同步标记区分
     * （作者 2026-09-18：红伪云作为伪云的一个变种，不要再加一个生物）。
     */
    private static SmallStangeCloudEntity findRedCloudInView(Level level, Vec3 eye, Vec3 viewVector) {
        Vec3 look = viewVector.normalize();
        SmallStangeCloudEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (SmallStangeCloudEntity cloud : level.getEntitiesOfClass(SmallStangeCloudEntity.class,
                new net.minecraft.world.phys.AABB(eye, eye).inflate(CLOUD_VIEW_RANGE))) {
            if (!cloud.isRed()) {
                continue; // 白的/黑的都不算
            }
            Vec3 toCloud = cloud.position().add(0.0D, -0.33D, 0.0D).subtract(eye); // 模型悬在原点下方
            double distSqr = toCloud.lengthSqr();
            if (distSqr < 0.25D) {
                return cloud; // 已经贴在脸上
            }
            if (distSqr >= bestDistSqr) {
                continue;
            }
            if (toCloud.normalize().dot(look) >= CLOUD_COS_LIMIT) {
                best = cloud;
                bestDistSqr = distSqr;
            }
        }
        return best;
    }

    private static boolean isPlayerLookingAtCornPoppy(Player player) {
        Level level = player.level();
        Vec3 start = player.getEyePosition(1f);
        Vec3 end = player.getEyePosition(1f).add(player.getViewVector(1f).scale(5));
        Vec3 direction = end.subtract(start).normalize();

        double distance = start.distanceTo(end);
        Vec3 current = start;
        
        for (double t = 0; t < distance; t += 0.1) {
            current = start.add(direction.scale(t));
            BlockPos pos = BlockPos.containing(current);
            
            if (pos.getY() < -64 || pos.getY() > 320) {
                break;
            }
            
            Block block = level.getBlockState(pos).getBlock();
            
            if (block == McanomalyarchivesModBlocks.CORN_POPPY.get() || 
                block == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
                
                if (block == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
                    level.setBlock(pos, McanomalyarchivesModBlocks.CORN_POPPY.get().defaultBlockState(), 3);
                }
                
                long currentGameTime = level.getGameTime();
                CornPoppyBlock.setLastWatchedTime(pos, currentGameTime);
                CornPoppyBlock.clearAngryState(pos);
                currentWatchingPoppy.put(player.getUUID(), pos);
                return true;
            }
            
            // 视线采样点所在列上下各 1 格也检测（玩家平视/微俯视时花在脚下层也能命中）
            BlockPos below = pos.below();
            Block blockBelow = level.getBlockState(below).getBlock();
            if (blockBelow == McanomalyarchivesModBlocks.CORN_POPPY.get() ||
                blockBelow == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
                if (blockBelow == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
                    level.setBlock(below, McanomalyarchivesModBlocks.CORN_POPPY.get().defaultBlockState(), 3);
                }
                long currentGameTime = level.getGameTime();
                CornPoppyBlock.setLastWatchedTime(below, currentGameTime);
                CornPoppyBlock.clearAngryState(below);
                currentWatchingPoppy.put(player.getUUID(), below);
                return true;
            }
            
            if (!level.getBlockState(pos).isSolid() && !level.getBlockState(pos).isAir()) {
                continue;
            }
            
            if (level.getBlockState(pos).isSolid()) {
                break;
            }
        }

        currentWatchingPoppy.remove(player.getUUID());
        return false;
    }

    private static float[] getLookAtAngles(LivingEntity entity, BlockPos targetPos) {
        Vec3 eyePos = entity.getEyePosition(1f);
        Vec3 targetCenter = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        Vec3 direction = targetCenter.subtract(eyePos).normalize();

        double x = direction.x;
        double y = direction.y;
        double z = direction.z;

        double horizontalDistance = Math.sqrt(x * x + z * z);
        
        float yaw = (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0f);
        float pitch = (float) -Math.toDegrees(Math.atan2(y, horizontalDistance));
        
        return new float[]{yaw, pitch};
    }

    private static void handleLookingAtPoppy(Player player, PlayerPoppyState state) {
        state.lookingTime += 5; // 回调每 5 tick 一次，按真实 tick 累计
        state.shouldApplySad = false;
        state.inBufferPeriod = false;
        state.bufferTime = 0;

        if (player.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
            player.removeEffect(McanomalyarchivesModMobEffects.SAD);
            state.sadLevel = 0;
            state.shouldApplySad = false;
            state.hasAddiction = true;
            state.addictionLevel = 1;
            state.timeAfterMaxLevel = 0;
            state.isFrozen = false;
            player.addEffect(new MobEffectInstance(
                McanomalyarchivesModMobEffects.ADDICTE,
                ADDICTION_DURATION,
                0,
                false,
                true
            ));
            return;
        }

        if (!state.hasAddiction && state.lookingTime >= ADDICTION_TRIGGER_TIME) {
            state.hasAddiction = true;
            state.addictionLevel = 1;
            state.timeAfterMaxLevel = 0;
            player.addEffect(new MobEffectInstance(
                McanomalyarchivesModMobEffects.ADDICTE,
                ADDICTION_DURATION,
                0,
                false,
                true
            ));
        } else if (state.hasAddiction && state.lookingTime > 0 && state.lookingTime % LEVEL_UP_INTERVAL == 0) {
            if (state.addictionLevel < MAX_ADDICTION_LEVEL) {
                state.addictionLevel = Math.min(state.addictionLevel + 1, MAX_ADDICTION_LEVEL);
            }
            state.timeAfterMaxLevel = 0;
            player.addEffect(new MobEffectInstance(
                McanomalyarchivesModMobEffects.ADDICTE,
                ADDICTION_DURATION,
                state.addictionLevel - 1,
                false,
                true
            ));
        }

        if (state.hasAddiction && state.addictionLevel >= MAX_ADDICTION_LEVEL) {
            if (!state.isFrozen) {
                state.isFrozen = true;
                state.frozenPosition = player.position();
                if (state.poppyPos != null) {
                    float[] angles = getLookAtAngles(player, state.poppyPos);
                    state.frozenYaw = angles[0];
                    state.frozenPitch = angles[1];
                } else {
                    state.frozenYaw = player.getYRot();
                    state.frozenPitch = player.getXRot();
                }
            }
            state.timeAfterMaxLevel += 5;
            if (state.timeAfterMaxLevel >= DEATH_LOOK_TIME) {
                DamageSource brainDamageSource = getBrainDamageSource(player.level());
                forceKillPlayer(player, brainDamageSource);
                resetPlayerAddictionState(player, state);
                state.timeAfterMaxLevel = 0;
                state.isFrozen = false;
            }
        }

        if (state.hasAddiction) {
            applyMovementRestriction(player, state);
        }

        if (player.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
            state.damageTickCounter += 5;
            int sadEffectLevel = Math.max(0, player.getEffect(McanomalyarchivesModMobEffects.SAD).getAmplifier());
            if (state.damageTickCounter >= DAMAGE_TICK_INTERVAL && sadEffectLevel >= 2) {
                float damage = (sadEffectLevel - 1) * 1.0f;
                player.hurt(player.damageSources().magic(), damage);
                state.damageTickCounter = 0;
            }
        }
    }

    private static void applyMovementRestriction(Player player, PlayerPoppyState state) {
        int addictionLevel = state.addictionLevel;
        int effectiveLevel = Math.min(addictionLevel, 6);

        int slownessLevel = Math.min(9, effectiveLevel * 2);
        player.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SLOWDOWN,
            60,
            slownessLevel,
            false,
            false
        ));

        if (addictionLevel >= 1 && player instanceof ServerPlayer serverPlayer) {
            var attribute = serverPlayer.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                if (!state.speedModified) {
                    state.originalMoveSpeed = attribute.getBaseValue();
                    state.speedModified = true;
                }
                double slowMultiplier = Math.max(0.01, 1.0 - effectiveLevel * 0.12);
                attribute.setBaseValue(state.originalMoveSpeed * slowMultiplier);
            }
        }

        if (state.poppyPos != null) {
            float[] angles = getLookAtAngles(player, state.poppyPos);
            float targetYaw = angles[0];
            float targetPitch = angles[1];
            
            float currentYaw = player.getYRot();
            float currentPitch = player.getXRot();
            
            float viewLimit = 180f - effectiveLevel * 30f;
            viewLimit = Math.max(5f, viewLimit);
            
            float yawDiff = currentYaw - targetYaw;
            float pitchDiff = currentPitch - targetPitch;
            
            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;
            
            float maxDiff = viewLimit / 2;
            
            if (Math.abs(yawDiff) > maxDiff) {
                float newYaw = targetYaw + (yawDiff > 0 ? maxDiff : -maxDiff);
                player.setYRot(newYaw);
            } else {
                float yawRecoverySpeed = effectiveLevel * 1.2f;
                float newYaw = currentYaw - yawDiff * yawRecoverySpeed * 0.1f;
                player.setYRot(newYaw);
            }
            
            if (Math.abs(pitchDiff) > maxDiff) {
                float newPitch = targetPitch + (pitchDiff > 0 ? maxDiff : -maxDiff);
                player.setXRot(newPitch);
            } else {
                float pitchRecoverySpeed = effectiveLevel * 1.2f;
                float newPitch = currentPitch - pitchDiff * pitchRecoverySpeed * 0.1f;
                player.setXRot(newPitch);
            }
        }
    }

    private static void handleNotLookingAtPoppy(Player player, PlayerPoppyState state) {
        if (state.hasAddiction) {
            state.isFrozen = false;

            if (!state.inBufferPeriod && !state.shouldApplySad) {
                state.inBufferPeriod = true;
                state.bufferTime = 0;
            }

            if (state.inBufferPeriod) {
                state.bufferTime += 5;

                if (state.bufferTime >= SAD_BUFFER_TIME) {
                    state.inBufferPeriod = false;
                    state.shouldApplySad = true;
                    state.sadLevel = state.addictionLevel;
                    state.timeAfterMaxLevel = 0;

                    int sadDuration = SAD_BASE_DURATION + (state.sadLevel * SAD_DURATION_PER_LEVEL);
                    int sadEffectLevel = Math.min(state.sadLevel, MAX_SAD_LEVEL);

                    player.removeEffect(McanomalyarchivesModMobEffects.ADDICTE);
                    player.addEffect(new MobEffectInstance(
                        McanomalyarchivesModMobEffects.SAD,
                        sadDuration,
                        sadEffectLevel - 1,
                        false,
                        true
                    ));

                    int slownessLevel = Math.min(sadEffectLevel - 1, 4);
                    if (slownessLevel >= 0) {
                        player.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            sadDuration,
                            slownessLevel,
                            false,
                            false
                        ));
                    }

                    int weaknessLevel = Math.min((sadEffectLevel - 1) / 2, 4);
                    if (weaknessLevel >= 0) {
                        player.addEffect(new MobEffectInstance(
                            MobEffects.WEAKNESS,
                            sadDuration,
                            weaknessLevel,
                            false,
                            false
                        ));
                    }
                }
            }
        }

        if (!player.hasEffect(McanomalyarchivesModMobEffects.ADDICTE) &&
            !player.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
            state.addictionLevel = 0;
            state.hasAddiction = false;
            state.shouldApplySad = false;
            state.lookingTime = 0;
            state.timeAfterMaxLevel = 0;
            state.damageTickCounter = 0;
            state.isFrozen = false;
            state.inBufferPeriod = false;
            state.bufferTime = 0;

            if (player instanceof ServerPlayer serverPlayer && state.speedModified) {
                var attribute = serverPlayer.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attribute != null) {
                    attribute.setBaseValue(state.originalMoveSpeed);
                }
                state.speedModified = false;
                state.originalMoveSpeed = 0;
            }
        }
    }

    // ========== NPC 相关方法 ==========

    private static boolean isEntityLookingAtCornPoppy(LivingEntity entity) {
        Level level = entity.level();
        Vec3 start = entity.getEyePosition(1f);
        Vec3 end = entity.getEyePosition(1f).add(entity.getViewVector(1f).scale(5));
        Vec3 direction = end.subtract(start).normalize();

        double distance = start.distanceTo(end);
        Vec3 current = start;
        
        for (double t = 0; t < distance; t += 0.1) {
            current = start.add(direction.scale(t));
            BlockPos pos = BlockPos.containing(current);
            
            if (pos.getY() < -64 || pos.getY() > 320) {
                break;
            }
            
            Block block = level.getBlockState(pos).getBlock();
            
            if (block == McanomalyarchivesModBlocks.CORN_POPPY.get() || 
                block == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
                
                if (block == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
                    level.setBlock(pos, McanomalyarchivesModBlocks.CORN_POPPY.get().defaultBlockState(), 3);
                }
                
                long currentGameTime = level.getGameTime();
                CornPoppyBlock.setLastWatchedTime(pos, currentGameTime);
                CornPoppyBlock.clearAngryState(pos);
                npcCurrentWatchingPoppy.put(entity.getUUID(), pos);
                return true;
            }
            
            // 视线采样点所在列上下各 1 格也检测
            BlockPos below = pos.below();
            Block blockBelow = level.getBlockState(below).getBlock();
            if (blockBelow == McanomalyarchivesModBlocks.CORN_POPPY.get() ||
                blockBelow == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
                if (blockBelow == McanomalyarchivesModBlocks.SAD_POPPY.get()) {
                    level.setBlock(below, McanomalyarchivesModBlocks.CORN_POPPY.get().defaultBlockState(), 3);
                }
                long currentGameTime = level.getGameTime();
                CornPoppyBlock.setLastWatchedTime(below, currentGameTime);
                CornPoppyBlock.clearAngryState(below);
                npcCurrentWatchingPoppy.put(entity.getUUID(), below);
                return true;
            }
            
            if (!level.getBlockState(pos).isSolid() && !level.getBlockState(pos).isAir()) {
                continue;
            }
            
            if (level.getBlockState(pos).isSolid()) {
                break;
            }
        }

        npcCurrentWatchingPoppy.remove(entity.getUUID());
        return false;
    }

    private static void handleNPCLookingAtPoppy(LivingEntity entity, PlayerPoppyState state) {
        state.lookingTime += 5; // 回调每 5 tick 一次，按真实 tick 累计
        state.shouldApplySad = false;
        state.inBufferPeriod = false;
        state.bufferTime = 0;

        if (entity.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
            entity.removeEffect(McanomalyarchivesModMobEffects.SAD);
            state.sadLevel = 0;
            state.shouldApplySad = false;
            state.hasAddiction = true;
            state.addictionLevel = 1;
            state.timeAfterMaxLevel = 0;
            state.isFrozen = false;
            entity.addEffect(new MobEffectInstance(
                McanomalyarchivesModMobEffects.ADDICTE,
                ADDICTION_DURATION,
                0,
                false,
                true
            ));
            return;
        }

        if (!state.hasAddiction && state.lookingTime >= ADDICTION_TRIGGER_TIME) {
            if (entity instanceof AnbulaEntity && !state.watchingCloud) {
                if (state.poppyPos != null) {
                    entity.level().setBlock(state.poppyPos,
                        McanomalyarchivesModBlocks.DEAD_POPPY.get().defaultBlockState(), 3);
                    CornPoppyBlock.removeFromAllMaps(state.poppyPos);
                    state.poppyPos = null;
                }
                state.lookingTime = 0;
                state.addictionLevel = 0;
                state.hasAddiction = false;
                state.timeAfterMaxLevel = 0;
                state.isFrozen = false;
                if (entity instanceof Mob mob) mob.getNavigation().stop();
            } else {
                state.hasAddiction = true;
                state.addictionLevel = 1;
                state.timeAfterMaxLevel = 0;
                entity.addEffect(new MobEffectInstance(
                    McanomalyarchivesModMobEffects.ADDICTE,
                    ADDICTION_DURATION,
                    0,
                    false,
                    true
                ));
            }
        } else if (state.hasAddiction && state.lookingTime > 0 && state.lookingTime % LEVEL_UP_INTERVAL == 0) {
            if (state.addictionLevel < MAX_ADDICTION_LEVEL) {
                state.addictionLevel = Math.min(state.addictionLevel + 1, MAX_ADDICTION_LEVEL);
            }
            state.timeAfterMaxLevel = 0;
            entity.addEffect(new MobEffectInstance(
                McanomalyarchivesModMobEffects.ADDICTE,
                ADDICTION_DURATION,
                state.addictionLevel - 1,
                false,
                true
            ));
        }

        if (state.hasAddiction && state.addictionLevel >= MAX_ADDICTION_LEVEL) {
            if (!state.isFrozen) {
                state.isFrozen = true;
                state.frozenPosition = entity.position();
                if (state.poppyPos != null) {
                    float[] angles = getLookAtAngles(entity, state.poppyPos);
                    state.frozenYaw = angles[0];
                    state.frozenPitch = angles[1];
                } else {
                    state.frozenYaw = entity.getYRot();
                    state.frozenPitch = entity.getXRot();
                }
            }
            state.timeAfterMaxLevel += 5;
            if (state.timeAfterMaxLevel >= DEATH_LOOK_TIME) {
                DamageSource brainDamageSource = getBrainDamageSource(entity.level());
                forceKillNPC(entity, brainDamageSource);
                state.timeAfterMaxLevel = 0;
                state.isFrozen = false;
            }
        }

        if (state.hasAddiction) {
            applyNPCMovementRestriction(entity, state);
        }

        if (entity.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
            state.damageTickCounter += 5;
            int sadEffectLevel = Math.max(0, entity.getEffect(McanomalyarchivesModMobEffects.SAD).getAmplifier());
            if (state.damageTickCounter >= DAMAGE_TICK_INTERVAL && sadEffectLevel >= 2) {
                float damage = (sadEffectLevel - 1) * 1.0f;
                entity.hurt(entity.damageSources().magic(), damage);
                state.damageTickCounter = 0;
            }
        }
    }

    private static void applyNPCMovementRestriction(LivingEntity entity, PlayerPoppyState state) {
        int addictionLevel = state.addictionLevel;
        int effectiveLevel = Math.min(addictionLevel, 6);

        int slownessLevel = Math.min(9, effectiveLevel * 2);
        entity.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SLOWDOWN,
            60,
            slownessLevel,
            false,
            false
        ));

        if (addictionLevel >= 1) {
            var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                if (!state.speedModified) {
                    state.originalMoveSpeed = attribute.getBaseValue();
                    state.speedModified = true;
                }
                double slowMultiplier = Math.max(0.01, 1.0 - effectiveLevel * 0.12);
                attribute.setBaseValue(state.originalMoveSpeed * slowMultiplier);
            }
        }

        if (state.poppyPos != null) {
            float[] angles = getLookAtAngles(entity, state.poppyPos);
            float targetYaw = angles[0];
            float targetPitch = angles[1];
            
            float currentYaw = entity.getYRot();
            float currentPitch = entity.getXRot();
            
            float viewLimit = 180f - effectiveLevel * 30f;
            viewLimit = Math.max(5f, viewLimit);
            
            float yawDiff = currentYaw - targetYaw;
            float pitchDiff = currentPitch - targetPitch;
            
            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;
            
            float maxDiff = viewLimit / 2;
            
            if (Math.abs(yawDiff) > maxDiff) {
                float newYaw = targetYaw + (yawDiff > 0 ? maxDiff : -maxDiff);
                entity.setYRot(newYaw);
            } else {
                float yawRecoverySpeed = effectiveLevel * 1.2f;
                float newYaw = currentYaw - yawDiff * yawRecoverySpeed * 0.1f;
                entity.setYRot(newYaw);
            }
            
            if (Math.abs(pitchDiff) > maxDiff) {
                float newPitch = targetPitch + (pitchDiff > 0 ? maxDiff : -maxDiff);
                entity.setXRot(newPitch);
            } else {
                float pitchRecoverySpeed = effectiveLevel * 1.2f;
                float newPitch = currentPitch - pitchDiff * pitchRecoverySpeed * 0.1f;
                entity.setXRot(newPitch);
            }
        }
    }

    private static void handleNPCNotLookingAtPoppy(LivingEntity entity, PlayerPoppyState state) {
        if (state.hasAddiction) {
            state.isFrozen = false;

            if (!state.inBufferPeriod && !state.shouldApplySad) {
                state.inBufferPeriod = true;
                state.bufferTime = 0;
            }

            if (state.inBufferPeriod) {
                state.bufferTime += 5;

                if (state.bufferTime >= SAD_BUFFER_TIME) {
                    state.inBufferPeriod = false;
                    state.shouldApplySad = true;
                    state.sadLevel = state.addictionLevel;
                    state.timeAfterMaxLevel = 0;

                    int sadDuration = SAD_BASE_DURATION + (state.sadLevel * SAD_DURATION_PER_LEVEL);
                    int sadEffectLevel = Math.min(state.sadLevel, MAX_SAD_LEVEL);

                    entity.removeEffect(McanomalyarchivesModMobEffects.ADDICTE);
                    entity.addEffect(new MobEffectInstance(
                        McanomalyarchivesModMobEffects.SAD,
                        sadDuration,
                        sadEffectLevel - 1,
                        false,
                        true
                    ));

                    int slownessLevel = Math.min(sadEffectLevel - 1, 4);
                    if (slownessLevel >= 0) {
                        entity.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            sadDuration,
                            slownessLevel,
                            false,
                            false
                        ));
                    }

                    int weaknessLevel = Math.min((sadEffectLevel - 1) / 2, 4);
                    if (weaknessLevel >= 0) {
                        entity.addEffect(new MobEffectInstance(
                            MobEffects.WEAKNESS,
                            sadDuration,
                            weaknessLevel,
                            false,
                            false
                        ));
                    }
                }
            }
        }

        if (!entity.hasEffect(McanomalyarchivesModMobEffects.ADDICTE) &&
            !entity.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
            state.addictionLevel = 0;
            state.hasAddiction = false;
            state.shouldApplySad = false;
            state.lookingTime = 0;
            state.timeAfterMaxLevel = 0;
            state.damageTickCounter = 0;
            state.isFrozen = false;
            state.inBufferPeriod = false;
            state.bufferTime = 0;

            if (state.speedModified) {
                var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attribute != null) {
                    attribute.setBaseValue(state.originalMoveSpeed);
                }
                state.speedModified = false;
                state.originalMoveSpeed = 0;
            }
        }
    }

    // ========== 公共工具方法 ==========

    private static void resetPlayerAddictionState(Player player, PlayerPoppyState state) {
        player.removeEffect(McanomalyarchivesModMobEffects.ADDICTE);
        player.removeEffect(McanomalyarchivesModMobEffects.SAD);
        state.addictionLevel = 0;
        state.hasAddiction = false;
        state.shouldApplySad = false;
        state.lookingTime = 0;
        state.timeAfterMaxLevel = 0;
        state.damageTickCounter = 0;
        state.isFrozen = false;
        state.inBufferPeriod = false;
        state.bufferTime = 0;
        state.sadLevel = 0;
        if (state.speedModified) {
            var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.setBaseValue(state.originalMoveSpeed);
            }
            state.speedModified = false;
            state.originalMoveSpeed = 0;
        }
    }

    private static void cleanupIdleState(UUID id, PlayerPoppyState state, Map<UUID, PlayerPoppyState> statesMap) {
        long currentTime = System.currentTimeMillis();
        long idleTime = currentTime - state.lastLookTime;

        if (idleTime > IDLE_CLEANUP_TIME &&
            state.addictionLevel == 0 &&
            !state.hasAddiction &&
            !state.shouldApplySad &&
            !state.isFrozen) {
            statesMap.remove(id);
        }
    }

    private static DamageSource getBrainDamageSource(Level level) {
        ResourceKey<DamageType> brainDamageKey = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "braindamage")
        );
        return level.damageSources().source(brainDamageKey);
    }

    private static void forceKillPlayer(Player player, DamageSource damageSource) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getAbilities().invulnerable = false;
            serverPlayer.getAbilities().flying = false;
            serverPlayer.onUpdateAbilities();
            
            net.minecraft.server.MinecraftServer server = serverPlayer.getServer();
            if (server != null) {
                net.minecraft.advancements.AdvancementHolder advancementHolder = 
                    server.getAdvancements().get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            "mcanomalyarchives", "watchdeath"
                        )
                    );
                if (advancementHolder != null && 
                    !serverPlayer.getAdvancements().getOrStartProgress(advancementHolder).isDone()) {
                    serverPlayer.getAdvancements().award(advancementHolder, "watchdeath_0");
                }
            }
            
            serverPlayer.hurt(damageSource, Float.MAX_VALUE);
            
            if (serverPlayer.getHealth() > 0) {
                serverPlayer.die(damageSource);
            }
        } else {
            player.hurt(damageSource, Float.MAX_VALUE);
            if (player.getHealth() > 0) {
                player.die(damageSource);
            }
        }
    }

    private static void forceKillNPC(LivingEntity entity, DamageSource damageSource) {
        entity.hurt(damageSource, Float.MAX_VALUE);
        if (entity.getHealth() > 0) {
            entity.die(damageSource);
        }
    }
}
