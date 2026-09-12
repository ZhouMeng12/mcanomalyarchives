package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.StrangeTreeEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.function.Predicate;

/**
 * 怪树实体懒加载：玩家靠近 str_tree 结构且树基座附近无实体时，
 * 自动补一只（世界生成的 jigsaw 结构不放置 nbt 实体，需要这里补；
 * 刷怪蛋已自带实体，检测到已有实体即跳过）。
 */
public class StrangeTreeHandler {

    private static final ResourceLocation STR_TREE_ID = ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "str_tree");
    private static final Predicate<Holder<Structure>> STR_TREE_PRED = h -> h.unwrapKey()
            .map(k -> k.location()).map(STR_TREE_ID::equals).orElse(false);
    private static final int ENTITY_RADIUS = 64;

    public static void init() {
        NeoForge.EVENT_BUS.register(new StrangeTreeHandler());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            processPlayer(player);
        }
    }

    private void processPlayer(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Map<Structure, LongSet> structures = level.structureManager().getAllStructuresAt(player.blockPosition());
        for (Map.Entry<Structure, LongSet> entry : structures.entrySet()) {
            ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
            if (!STR_TREE_ID.equals(id)) continue;
            for (long chunkLong : entry.getValue()) {
                int probeX = ChunkPos.getX(chunkLong) * 16 + 2;
                int probeZ = ChunkPos.getZ(chunkLong) * 16 + 2;
                int probeY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(probeX, 0, probeZ)).getY();
                StructureStart s = level.structureManager().getStructureWithPieceAt(new BlockPos(probeX, probeY, probeZ), STR_TREE_PRED);
                BlockPos base;
                if (s.isValid()) {
                    BoundingBox box = s.getBoundingBox();
                    base = new BlockPos(box.minX() + 2, box.minY(), box.minZ() + 2);
                } else {
                    int baseY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(probeX + 8, 0, probeZ + 8)).getY();
                    base = new BlockPos(probeX, baseY, probeZ);
                }
                if (base.distSqr(player.blockPosition()) <= (double) ENTITY_RADIUS * ENTITY_RADIUS) {
                    ensureEntity(level, base);
                }
            }
        }
    }

    private void ensureEntity(ServerLevel level, BlockPos base) {
        // 检测范围必须覆盖所有可能位置，否则会误判"没有树"而重复补一只新树：
        // - 钻地后下移 20 格的实体
        // - 埋伏追踪移动到玩家附近的实体（玩家距 base ≤64 + 埋伏点距玩家 ≤32 = 最远约 96 格）
        if (!level.getEntitiesOfClass(StrangeTreeEntity.class, new net.minecraft.world.phys.AABB(base).inflate(128.0)).isEmpty()) {
            return;
        }
        // 实体丢失时：只有树干/树叶方块还在（树处于站立态）才补；
        // 钻地/潜伏/偷袭流程中树干树叶已被清除（方块表由 BurrowHandler 按 UUID 记录），绝不补，
        // 否则会补出一只没有方块、没有方块表的新树（脚模型孤零零站在空地上）
        if (!hasTreeBlocks(level, base)) {
            return;
        }
        StrangeTreeEntity tree = new StrangeTreeEntity(McanomalyarchivesModEntities.STRANGE_TREE.get(), level);
        tree.setPos(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        level.addFreshEntity(tree);
    }

    /** 基座附近（±4 格、高 8 格）是否还有怪树树干/树叶方块 */
    private static boolean hasTreeBlocks(ServerLevel level, BlockPos base) {
        for (int dy = 0; dy <= 8; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    net.minecraft.world.level.block.state.BlockState st = level.getBlockState(base.offset(dx, dy, dz));
                    if (st.getBlock() == net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks.STRANGETREELOG.get()
                            || st.getBlock() == net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks.STRANGETREELEAVES.get()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
