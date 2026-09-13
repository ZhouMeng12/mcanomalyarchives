package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.entity.StangeCloudEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 伪云生成：在玩家附近的高空（y 100~150）周期性生成，
 * 让"天上飘的云"在主世界普通地形也能看到，不再依赖高山地形。
 */
public class StangeCloudSpawnHandler {

    /** 检查间隔：1 秒一次 */
    private static final int CHECK_INTERVAL = 20;
    /** 每个玩家的生成间隔：3~6 分钟 */
    private static final int MIN_INTERVAL = 3600;
    private static final int MAX_INTERVAL = 7200;
    /** 生成在玩家水平 16~40 格、垂直 100~150 格 */
    private static final int MIN_OFFSET = 16;
    private static final int MAX_OFFSET = 40;
    private static final int MIN_Y = 100;
    private static final int MAX_Y = 150;
    /** 玩家 80 格内已有伪云则不再生成 */
    private static final double NEARBY_RADIUS_SQ = 80.0 * 80.0;

    private static final ResourceKey<Level> CLOUD_DEM_KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "clouddem"));

    /** 每玩家下次生成时间（gameTime） */
    private static final Map<UUID, Long> nextSpawnTime = new HashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.register(new StangeCloudSpawnHandler());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            nextSpawnTime.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            trySpawn(player);
        }
    }

    private void trySpawn(ServerPlayer player) {
        if (player.gameMode.isCreative() || player.isSpectator()) return;
        ServerLevel level = (ServerLevel) player.level();
        ResourceKey<Level> dim = level.dimension();
        // 只做主世界与云之维度；地下/洞穴（y<60）不生成（生成也看不到）
        if (!(dim.equals(Level.OVERWORLD) || dim.equals(CLOUD_DEM_KEY)) || player.getY() < 60) return;

        long now = level.getGameTime();
        UUID uuid = player.getUUID();
        long next = nextSpawnTime.getOrDefault(uuid, 0L);
        if (now < next) return;

        // 附近已有伪云：顺延一个间隔
        if (hasCloudNearby(level, player)) {
            nextSpawnTime.put(uuid, now + MIN_INTERVAL);
            return;
        }

        if (spawnCloud(level, player)) {
            long interval = MIN_INTERVAL + player.getRandom().nextInt(MAX_INTERVAL - MIN_INTERVAL);
            nextSpawnTime.put(uuid, now + interval);
        } else {
            // 没找到合适的空域：10 秒后重试
            nextSpawnTime.put(uuid, now + 200);
        }
    }

    private boolean hasCloudNearby(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(StangeCloudEntity.class, player.getBoundingBox().inflate(80.0))
                .stream().anyMatch(cloud -> cloud.distanceToSqr(player) < NEARBY_RADIUS_SQ);
    }

    private boolean spawnCloud(ServerLevel level, ServerPlayer player) {
        RandomSource rng = player.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = MIN_OFFSET + rng.nextDouble() * (MAX_OFFSET - MIN_OFFSET);
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            int y = MIN_Y + rng.nextInt(MAX_Y - MIN_Y + 1);
            if (isAirSpace(level, x, y, z)) {
                StangeCloudEntity cloud = new StangeCloudEntity(McanomalyarchivesModEntities.STANGE_CLOUD.get(), level);
                cloud.setPos(x, y, z);
                level.addFreshEntity(cloud);
                return true;
            }
        }
        return false;
    }

    /** 生成点及其上方 3 格必须是可通行的空域（避免生成在山上/建筑里） */
    private boolean isAirSpace(ServerLevel level, double x, int y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            BlockState state = level.getBlockState(pos.set((int) Math.floor(x), y + i, (int) Math.floor(z)));
            if (state.isSolid() || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }
    /**
     * 禁止伪云以"生物自然刷怪"方式出现（它由本类按玩家距离自行投放）。
     *
     * <p><b>注意这道拦截单独并不足以避免世界生成崩溃</b>：
     * {@code NaturalSpawner.spawnMobsForChunkGeneration} 里先算 spawn AABB 做碰撞检测
     * （{@code noCollision}），{@code checkSpawnRules}（本事件）排在它后面，短路求值也救不了。
     * 伪云宽 30 格，生成 AABB 会横跨邻接区块 → {@code Requested chunk unavailable during world generation}。
     * 真正让它不进区块生成刷怪表的是【注册为 MobCategory.AMBIENT】
     * （该方法只取 {@code MobCategory.CREATURE}）。这里只作为双保险。
     */
    @SubscribeEvent
    public void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() != McanomalyarchivesModEntities.STANGE_CLOUD.get()) return;
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }
}