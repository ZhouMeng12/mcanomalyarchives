package net.mcreator.mcanomalyarchives.anomaly.rewind;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;
import net.mcreator.mcanomalyarchives.item.NegativeMusicDiscItem;

/** Immutable NBT snapshots; player data is restored selectively, never achievements or codex memory. */
public record RewindSnapshot(long tick, Map<Long, Tile> tiles, Map<UUID, Actor> actors,
        Map<UUID, Person> players, List<ScheduledTick<Block>> blockTicks, List<ScheduledTick<Fluid>> fluidTicks) {
    public record Tile(BlockState state, CompoundTag data, CompoundTag inventory) { }
    public record Actor(int runtimeId, Vec3 position, CompoundTag data, UUID vehicle) { }
    public record Person(String dimension, Vec3 position, CompoundTag data, CompoundTag carried) { }

    public static boolean inside(BlockPos center, Vec3 point) {
        return Vec3.atCenterOf(center).distanceToSqr(point) <= 50 * 50;
    }
    public static boolean inside(BlockPos center, BlockPos point) { return center.distSqr(point) <= 50 * 50; }

    public static List<LevelChunk> chunks(ServerLevel level, BlockPos center) {
        List<LevelChunk> result = new ArrayList<>();
        for (int x = (center.getX() - 54) >> 4; x <= (center.getX() + 54) >> 4; x++) {
            for (int z = (center.getZ() - 54) >> 4; z <= (center.getZ() + 54) >> 4; z++) {
                int nearestX = Math.max(x << 4, Math.min(center.getX(), (x << 4) + 15));
                int nearestZ = Math.max(z << 4, Math.min(center.getZ(), (z << 4) + 15));
                long dx = nearestX - center.getX(), dz = nearestZ - center.getZ();
                // Square bounding-box corners lie outside the sphere and need not be loaded.
                if (dx * dx + dz * dz > 54 * 54) continue;
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) throw new Unavailable("chunks");
                result.add(chunk);
            }
        }
        return result;
    }

    public static List<LevelChunk> loadedChunks(ServerLevel level, BlockPos center, int radius) {
        List<LevelChunk> result = new ArrayList<>();
        for (int x=(center.getX()-radius)>>4; x<=(center.getX()+radius)>>4; x++)
            for (int z=(center.getZ()-radius)>>4; z<=(center.getZ()+radius)>>4; z++) {
                var chunk=level.getChunkSource().getChunkNow(x,z);
                if (chunk!=null) result.add(chunk);
            }
        return result;
    }
    public static RewindSnapshot capture(ServerLevel level, BlockPos center, long tick) { return capture(level, center, tick, 50); }
    public static RewindSnapshot capture(ServerLevel level, BlockPos center, long tick, int radius) {
        if (radius > 50) for (ServerPlayer p : level.players()) if (p.distanceToSqr(Vec3.atCenterOf(center)) <= radius * radius) {
            p.closeContainer();
            for (int slot = 1; slot <= 4; slot++) {
                var stack = p.inventoryMenu.getSlot(slot).remove(Integer.MAX_VALUE);
                if (!stack.isEmpty() && !p.getInventory().add(stack)) p.drop(stack, false);
            }
        }
        Map<Long, Tile> tiles = new HashMap<>();
        Map<UUID, Actor> actors = new HashMap<>();
        Map<UUID, Person> players = new HashMap<>();
        List<ScheduledTick<Block>> blocks = new ArrayList<>();
        List<ScheduledTick<Fluid>> fluids = new ArrayList<>();
        long bytes = 0;
        for (LevelChunk chunk : radius == 50 ? chunks(level, center) : loadedChunks(level, center, radius)) {
            for (BlockEntity tile : List.copyOf(chunk.getBlockEntities().values())) {
                if (center.distSqr(tile.getBlockPos()) > (radius + 4) * (radius + 4)) continue;
                if (radius == 50 && inside(center, tile.getBlockPos()) && !BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(tile.getType()).getNamespace().equals("minecraft")) {
                    throw new Unavailable("machine");
                }
                CompoundTag inventory = new CompoundTag();
                if (tile instanceof Container container) {
                    ListTag items = new ListTag();
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        var stack = container.getItem(slot);
                        NegativeMusicDiscItem.synchronize(stack, level);
                        if (!stack.isEmpty()) {
                            CompoundTag entry = new CompoundTag();
                            entry.putInt("slot", slot);
                            entry.put("item", stack.save(level.registryAccess()));
                            items.add(entry);
                        }
                    }
                    inventory.put("items", items);
                }
                CompoundTag data = tile.saveWithFullMetadata(level.registryAccess());
                bytes += data.sizeInBytes();
                tiles.put(tile.getBlockPos().asLong(), new Tile(tile.getBlockState(), data, inventory));
                if (radius == 50 && tiles.size() > 192) throw new Unavailable("busy");
            }
            ((net.minecraft.world.ticks.LevelChunkTicks<Block>)chunk.getBlockTicks()).getAll().filter(t -> center.distSqr(t.pos()) <= radius * radius).forEach(blocks::add);
            ((net.minecraft.world.ticks.LevelChunkTicks<Fluid>)chunk.getFluidTicks()).getAll().filter(t -> center.distSqr(t.pos()) <= radius * radius).forEach(fluids::add);
        }
        for (Entity entity : level.getEntities((Entity)null, new AABB(center).inflate(radius + 16), e -> !(e instanceof ServerPlayer))) {
            if (entity instanceof ItemEntity item) RewindService.synchronizeDroppedDisc(item, level);
            if (entity.isRemoved()) continue;
            CompoundTag data = entity.saveWithoutId(new CompoundTag());
            data.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            data.remove("Passengers");
            bytes += data.sizeInBytes();
            actors.put(entity.getUUID(), new Actor(entity.getId(), entity.position(), data, entity.getVehicle() == null ? null : entity.getVehicle().getUUID()));
            if (radius == 50 && actors.size() > 384) throw new Unavailable("busy");
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) NegativeMusicDiscItem.synchronize(player.getInventory().getItem(i), level);
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) NegativeMusicDiscItem.synchronize(player.getEnderChestInventory().getItem(i), level);
            NegativeMusicDiscItem.synchronize(player.containerMenu.getCarried(), level);
            CompoundTag carried = player.containerMenu.getCarried().isEmpty() ? new CompoundTag()
                    : (CompoundTag)player.containerMenu.getCarried().save(player.registryAccess());
            // Transient crafting inputs are not part of player NBT. Refuse an incomplete timeline.
            if (player.serverLevel() == level && Vec3.atCenterOf(center).distanceToSqr(player.position()) <= radius * radius) {
                var menu = player.containerMenu;
                boolean backed = menu == player.inventoryMenu || menu instanceof net.minecraft.world.inventory.ChestMenu
                    || menu instanceof net.minecraft.world.inventory.HopperMenu || menu instanceof net.minecraft.world.inventory.DispenserMenu
                    || menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu || menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu
                    || menu instanceof net.minecraft.world.inventory.BrewingStandMenu;
                if (!backed) throw new Unavailable("menu");
                for (int slot = 1; slot <= 4; slot++) if (player.inventoryMenu.getSlot(slot).hasItem()) throw new Unavailable("menu");
            }
            CompoundTag data = player.saveWithoutId(new CompoundTag());
            if (player.getVehicle() != null) data.putUUID("RewindVehicle", player.getVehicle().getUUID());
            bytes += data.sizeInBytes();
            players.put(player.getUUID(), new Person(player.level().dimension().location().toString(), player.position(), data, carried));
        }
        if (radius == 50 && (bytes > 2_000_000 || blocks.size() + fluids.size() > 8192)) throw new Unavailable("busy");
        return new RewindSnapshot(level.getGameTime(), Map.copyOf(tiles), Map.copyOf(actors), Map.copyOf(players), List.copyOf(blocks), List.copyOf(fluids));
    }

    public static final class Unavailable extends RuntimeException {
        public final String reason;
        public Unavailable(String reason) { super(reason); this.reason = reason; }
    }
}
