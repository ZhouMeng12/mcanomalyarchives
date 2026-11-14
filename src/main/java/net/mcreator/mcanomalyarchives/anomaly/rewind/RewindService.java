package net.mcreator.mcanomalyarchives.anomaly.rewind;

import java.util.*;
import net.mcreator.mcanomalyarchives.anomaly.earth.EarthService;
import net.mcreator.mcanomalyarchives.anomaly.earth.EarthData;
import net.mcreator.mcanomalyarchives.anomaly.earth.EarthRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.codex.CodexObservations;
import net.mcreator.mcanomalyarchives.item.NegativeMusicDiscItem;
import net.mcreator.mcanomalyarchives.network.RewindVisualPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Records loaded jukebox neighbourhoods. Preview is client-only; inventories commit together at the end. */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class RewindService {
    public static final int DURATION = 60;
    private static final int MAX_CHANGES = 8192;
    private static final Map<ServerLevel, World> WORLDS = new IdentityHashMap<>();
    private static boolean restoring;
    private static boolean suppressSpawns;
    private static int restoreRadius = 50;
    private static boolean restoreInside(BlockPos c, BlockPos p) { return c.distSqr(p) <= restoreRadius * restoreRadius; }
    private static boolean restoreInside(BlockPos c, Vec3 p) { return Vec3.atCenterOf(c).distanceToSqr(p) <= restoreRadius * restoreRadius; }

    private record Frame(RewindSnapshot snapshot, Map<Long, BlockState> before, EarthData.State earth) { }
    private static final class Watch {
        final BlockPos center;
        final RewindWindow<Frame> history = new RewindWindow<>();
        final Map<Long, BlockState> pending = new LinkedHashMap<>();
        String failure = "warming";
        long lastSample = Long.MIN_VALUE;
        Watch(BlockPos center) { this.center = center.immutable(); }
        void sample(ServerLevel level, long tick) {
            if (tick == lastSample) return;
            lastSample = tick;
            try {
                RewindSnapshot snapshot = RewindSnapshot.capture(level, center, tick);
                history.add(tick, new Frame(snapshot, Map.copyOf(pending), EarthService.snapshot(level)));
                failure = "warming";
            } catch (RewindSnapshot.Unavailable unavailable) {
                history.clear(); failure = unavailable.reason;
            } catch (RuntimeException error) {
                history.clear(); failure = "machine";
                McanomalyarchivesMod.LOGGER.warn("Unable to record rewind neighbourhood at {}", center, error);
            }
            pending.clear();
        }
    }
    private static final class World {
        final Map<BlockPos, Watch> watches = new LinkedHashMap<>();
        final Set<BlockPos> requests = new LinkedHashSet<>();
        Active active;
    }
    private static final class Active {
        final BlockPos center;
        final List<RewindWindow.Sample<Frame>> frames;
        final RewindSnapshot start;
        final Map<Long, BlockState> originalBlocks;
        final Map<Long, BlockState> targetBlocks;
        final Set<UUID> participants;
        final UUID disc;
        final Set<UUID> trackedActors = new HashSet<>();
        int age, previewIndex;
        Iterator<Map.Entry<Long, BlockState>> commitBlocks;
        boolean partialCommit;
        Active(BlockPos center, List<RewindWindow.Sample<Frame>> frames, RewindSnapshot start,
                Map<Long, BlockState> originalBlocks, Map<Long, BlockState> targetBlocks, Set<UUID> participants, UUID disc) {
            this.center = center; this.frames = frames; this.start = start;
            this.originalBlocks = originalBlocks; this.targetBlocks = targetBlocks; this.participants = participants; this.disc = disc;
            previewIndex = frames.size() - 1;
            frames.getFirst().state().snapshot.actors().forEach((id, actor) -> {
                if (RewindSnapshot.inside(center, actor.position())) trackedActors.add(id);
            });
        }
    }
    private RewindService() { }

    public static void inserted(JukeboxBlockEntity jukebox) {
        if (restoring || !(jukebox.getLevel() instanceof ServerLevel level)
                || !(jukebox.getTheItem().getItem() instanceof NegativeMusicDiscItem)) return;
        NegativeMusicDiscItem.synchronize(jukebox.getTheItem(), level);
        var existing = WORLDS.get(level);
        if (existing != null && existing.active != null) return;
        if (EarthCheckpoint.activate(level, jukebox)) return;
        World world = WORLDS.computeIfAbsent(level, ignored -> new World());
        BlockPos pos = jukebox.getBlockPos().immutable();
        if (!world.watches.containsKey(pos)) {
            // Explicitly used jukeboxes take priority over the four discovered automatically.
            if (world.watches.size() >= 4) world.watches.remove(world.watches.keySet().iterator().next());
            world.watches.put(pos, new Watch(pos));
        }
        world.requests.add(pos);
    }

    public static void beforeBlockChange(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (restoring || before == after) return;
        EarthCheckpoint.record(level, pos, before);
        World world = WORLDS.get(level);
        if (world == null) return;
        for (Watch watch : world.watches.values()) {
            if (watch.center.distSqr(pos) > 54 * 54 && !EarthService.affected(level, watch.center, pos)) continue;
            if (watch.pending.size() >= (EarthService.associated(level, watch.center) ? EarthRules.REWIND_PENDING_LIMIT : MAX_CHANGES)) {
                watch.history.clear(); watch.pending.clear(); watch.failure = "busy";
            }
            watch.pending.putIfAbsent(pos.asLong(), before);
        }
    }

    public static boolean frozen(net.minecraft.world.level.Level level, BlockPos pos) {
        if (restoring || !(level instanceof ServerLevel server)) return false;
        if (EarthCheckpoint.frozen(server, pos)) return true;
        World world = WORLDS.get(server);
        return world != null && world.active != null && (world.active.center.distSqr(pos) <= 54 * 54 || EarthService.affected(server, world.active.center, pos));
    }
    public static boolean frozenCore(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!frozen(level, pos)) return false;
        if (EarthCheckpoint.frozen((ServerLevel)level, pos)) return true;
        var world = WORLDS.get((ServerLevel)level);
        return world != null && world.active != null && RewindSnapshot.inside(world.active.center, pos);
    }
    public static boolean restoring() { return restoring; }
    private static boolean trackedEntity(Entity entity) {
        if (restoring || !(entity.level() instanceof ServerLevel level)) return false;
        World world = WORLDS.get(level);
        return world != null && world.active != null && world.active.trackedActors.contains(entity.getUUID());
    }
    public static boolean locked(Player player) {
        if (restoring || !(player.level() instanceof ServerLevel level)) return false;
        if (EarthCheckpoint.frozen(level, player.blockPosition())) return true;
        World world = WORLDS.get(level);
        return world != null && world.active != null && (world.active.participants.contains(player.getUUID())
                || player.distanceToSqr(Vec3.atCenterOf(world.active.center)) <= 54 * 54);
    }

    @SubscribeEvent public static void entityTick(EntityTickEvent.Pre event) {
        if (trackedEntity(event.getEntity()) || frozen(event.getEntity().level(), event.getEntity().blockPosition())
                || (event.getEntity() instanceof Player player && locked(player))) event.setCanceled(true);
    }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event) {
        if (trackedEntity(event.getEntity()) || frozen(event.getEntity().level(), event.getEntity().blockPosition())
                || (event.getEntity() instanceof Player player && locked(player))) event.setCanceled(true);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (suppressSpawns || frozen(event.getLevel(), event.getEntity().blockPosition())) event.setCanceled(true);
    }
    @SubscribeEvent public static void droppedDiscTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                && item.level() instanceof ServerLevel level && item.tickCount % 20 == 0) synchronizeDroppedDisc(item, level);
    }
    static void synchronizeDroppedDisc(net.minecraft.world.entity.item.ItemEntity item, ServerLevel level) {
        if (!(item.getItem().getItem() instanceof NegativeMusicDiscItem)) return;
        ItemStack updated = item.getItem().copy();
        NegativeMusicDiscItem.synchronize(updated, level);
        if (updated.isEmpty()) item.discard();
        else if (!ItemStack.matches(item.getItem(), updated)) item.setItem(updated);
    }
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (var entry : WORLDS.entrySet()) if (entry.getValue().active != null) cancel(entry.getKey(), entry.getValue());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        WORLDS.clear(); restoring = false; suppressSpawns = false;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (EarthCheckpoint.tick(level)) continue;
            World world = WORLDS.computeIfAbsent(level, ignored -> new World());
            if (world.active != null) { animate(level, world); continue; }
            if (tick % 20 == 0) discover(level, world);
            for (Watch watch : world.watches.values()) {
                if (tick % RewindWindow.SAMPLE_TICKS == 0 || world.requests.contains(watch.center)) watch.sample(level, tick);
            }
            for (BlockPos pos : List.copyOf(world.requests)) {
                if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) begin(level, world, jukebox, tick);
            }
            world.requests.clear();
        }
    }

    private static void discover(ServerLevel level, World world) {
        world.watches.entrySet().removeIf(e -> !level.hasChunkAt(e.getKey()) || !(level.getBlockEntity(e.getKey()) instanceof JukeboxBlockEntity)
                || level.players().stream().noneMatch(p -> p.distanceToSqr(Vec3.atCenterOf(e.getKey())) < 96 * 96));
        Set<Long> scanned = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            for (int x = player.chunkPosition().x - 4; x <= player.chunkPosition().x + 4; x++) {
                for (int z = player.chunkPosition().z - 4; z <= player.chunkPosition().z + 4; z++) {
                    if (!scanned.add(net.minecraft.world.level.ChunkPos.asLong(x, z))) continue;
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    for (BlockEntity tile : chunk.getBlockEntities().values()) {
                        if (tile instanceof JukeboxBlockEntity && world.watches.size() < 4) {
                            world.watches.computeIfAbsent(tile.getBlockPos().immutable(), Watch::new);
                        }
                    }
                }
            }
        }
    }

    private static void begin(ServerLevel level, World world, JukeboxBlockEntity jukebox, long tick) {
        if (!(jukebox.getTheItem().getItem() instanceof NegativeMusicDiscItem)) return;
        try {
            if (world.active != null) throw new RewindSnapshot.Unavailable("busy");
            Watch watch = world.watches.get(jukebox.getBlockPos());
            if (watch == null) throw new RewindSnapshot.Unavailable("warming");
            var frames = watch.history.availableHistory(tick);
            if (frames.isEmpty()) throw new RewindSnapshot.Unavailable(watch.failure);
            UUID disc = NegativeMusicDiscItem.identity(jukebox.getTheItem());
            if (DiscUsageData.get(level).used(disc) >= DiscUsageData.MAX_USES) throw new RewindSnapshot.Unavailable("worn");
            Set<UUID> participants = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                if (frames.stream().anyMatch(f -> { var person = f.state().snapshot.players().get(player.getUUID()); return person != null && person.dimension().equals(level.dimension().location().toString()) && RewindSnapshot.inside(watch.center, person.position()); })) {
                    participants.add(player.getUUID());
                    for (int slot = 1; slot <= 4; slot++) if (player.inventoryMenu.getSlot(slot).hasItem())
                        throw new RewindSnapshot.Unavailable("menu");
                    player.closeContainer();
                    player.stopUsingItem();
                }
            }
            // Closing menus can return cursor/crafting items; take the rollback snapshot afterwards.
            RewindSnapshot start = RewindSnapshot.capture(level, watch.center, tick);
            Map<Long, BlockState> originals = new LinkedHashMap<>(), target = new LinkedHashMap<>();
            for (int i = frames.size() - 1; i > 0; i--) {
                for (var change : frames.get(i).state().before.entrySet()) {
                    BlockPos pos = BlockPos.of(change.getKey());
                    if (!RewindSnapshot.inside(watch.center, pos) && !EarthService.affected(level, watch.center, pos)) continue;
                    if (!level.hasChunkAt(pos)) throw new RewindSnapshot.Unavailable("chunks");
                    originals.putIfAbsent(change.getKey(), level.getBlockState(pos));
                    target.put(change.getKey(), change.getValue());
                }
            }
            if (target.size() > (EarthService.associated(level, watch.center) ? EarthRules.REWIND_LIMIT : MAX_CHANGES)) throw new RewindSnapshot.Unavailable("busy");
            world.active = new Active(watch.center, frames, start, originals, target, Set.copyOf(participants), disc);
            jukebox.getSongPlayer().stop(level, jukebox.getBlockState());
            sendVisual(level, world.active, 0, start);
            for (UUID id : participants) CodexObservations.record(level.getServer().getPlayerList().getPlayer(id), "negative_disc_sound");
        } catch (RewindSnapshot.Unavailable failure) {
            jukebox.getSongPlayer().stop(level, jukebox.getBlockState());
            for (ServerPlayer player : level.players()) if (player.distanceToSqr(Vec3.atCenterOf(jukebox.getBlockPos())) < 50 * 50)
                player.displayClientMessage(Component.translatable("message.mcanomalyarchives.rewind." + failure.reason), true);
        } catch (RuntimeException error) {
            jukebox.getSongPlayer().stop(level, jukebox.getBlockState());
            McanomalyarchivesMod.LOGGER.error("Negative disc preflight failed", error);
        }
    }

    private static void animate(ServerLevel level, World world) {
        Active active = world.active;
        try {
            RewindSnapshot.chunks(level, active.center);
            for (UUID id : active.participants) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player == null || (restoreRadius == 50 && player.serverLevel() != level)) continue;
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0;
            }
            active.age++;
            int index = active.age <= 10 ? active.frames.size() - 1
                    : Math.max(0, (int)Math.ceil((active.frames.size() - 1) * (1 - Math.min(1, (active.age - 10) / 40.0))));
            while (active.previewIndex > index) {
                for (var change : active.frames.get(active.previewIndex).state().before.entrySet()) {
                    if (RewindSnapshot.inside(active.center, BlockPos.of(change.getKey()))) previewBlock(level, active, change.getKey(), change.getValue());
                }
                active.previewIndex--;
            }
            sendVisual(level, active, Math.min(DURATION - 1, active.age), active.frames.get(index).state().snapshot);
            EarthService.preview(level, active.center, active.frames.get(index).state().earth);
            if (active.age < DURATION) return;
            restoring = true;
            try {
                if (active.commitBlocks == null) active.commitBlocks = active.targetBlocks.entrySet().iterator();
                boolean capturing = level.captureBlockSnapshots;
                level.captureBlockSnapshots = true; suppressSpawns = true;
                try {
                    int budget = 2048;
                    while (budget-- > 0 && active.commitBlocks.hasNext()) {
                        var change = active.commitBlocks.next(); BlockPos pos = BlockPos.of(change.getKey());
                        if (!level.hasChunkAt(pos)) throw new IllegalStateException("Rewind chunk unloaded during commit");
                        active.partialCommit = true; restoreBlock(level, pos, change.getValue());
                    }
                } finally { level.captureBlockSnapshots = capturing; suppressSpawns = false; }
                if (active.commitBlocks.hasNext()) return;
                restore(level, active.center, Map.of(), active.frames.getFirst().state().snapshot, active.participants);
                EarthService.restored(level, active.center, active.frames.getFirst().state().earth);
                active.partialCommit = false;
            } catch (RuntimeException error) {
                McanomalyarchivesMod.LOGGER.error("Negative disc commit failed; restoring its start snapshot", error);
                restore(level, active.center, active.originalBlocks, active.start, active.participants);
                active.partialCommit = false;
                throw error;
            } finally { restoring = false; suppressSpawns = false; }
            // Wear is outside the transaction. Once committed, a notification error must not roll the world back.
            DiscUsageData.get(level).consume(active.disc);
            try {
                restoring = true;
                refreshDiscWear(level, active.center);
                for (UUID id : active.participants) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                    if (player != null) { CodexObservations.record(player, "negative_disc_rewind"); CodexObservations.record(player, "negative_disc_wear"); if (DiscUsageData.get(level).used(active.disc) >= DiscUsageData.MAX_USES) net.mcreator.mcanomalyarchives.codex.ArchiveAdvancements.award(player, "disc_spent"); }
                }
            } catch (RuntimeException error) {
                McanomalyarchivesMod.LOGGER.warn("Rewind committed; unable to refresh all notifications", error);
            } finally { restoring = false; finish(level, world); }
        } catch (RuntimeException error) {
            restoring = false; suppressSpawns = false;
            McanomalyarchivesMod.LOGGER.warn("Negative disc playback cancelled", error);
            cancel(level, world);
        }
    }

    private static void restore(ServerLevel level, BlockPos center, Map<Long, BlockState> states, RewindSnapshot snapshot, Set<UUID> participants) {
        // State replacement can run vanilla onRemove (container drops). Discard generated drops and restore exact saved contents afterwards.
        suppressSpawns = true;
        boolean capturing = level.captureBlockSnapshots;
        level.captureBlockSnapshots = true;
        try {
            for (var entry : states.entrySet()) restoreBlock(level, BlockPos.of(entry.getKey()), entry.getValue());
            for (var entry : snapshot.tiles().entrySet()) {
                BlockPos pos = BlockPos.of(entry.getKey());
                if (!restoreInside(center, pos)) continue;
                var tile = entry.getValue();
                if (restoreRadius > 50) level.getChunkAt(pos);
                if (level.getBlockState(pos) != tile.state()) restoreBlock(level, pos, tile.state());
                BlockEntity current = level.getBlockEntity(pos);
                if (current == null) {
                    current = BlockEntity.loadStatic(pos, tile.state(), tile.data().copy(), level.registryAccess());
                    if (current == null) throw new IllegalStateException("Cannot restore block entity at " + pos);
                    level.setBlockEntity(current);
                } else current.loadWithComponents(tile.data().copy(), level.registryAccess());
                if (current instanceof JukeboxBlockEntity jukebox) jukebox.getSongPlayer().stop(level, jukebox.getBlockState());
                current.setChanged();
                level.sendBlockUpdated(pos, tile.state(), tile.state(), Block.UPDATE_CLIENTS);
            }
        } finally { level.captureBlockSnapshots = capturing; suppressSpawns = false; }
        Map<UUID, RewindSnapshot.Actor> target = new HashMap<>();
        World world = WORLDS.get(level);
        snapshot.actors().forEach((id, actor) -> {
            if (restoreInside(center, actor.position()) || (world != null && world.active != null && world.active.trackedActors.contains(id))) target.put(id, actor);
        });
        for (Entity entity : List.copyOf(level.getEntities((Entity)null, new net.minecraft.world.phys.AABB(center).inflate(restoreRadius + 1),
                e -> !(e instanceof Player) && restoreInside(center, e.position())))) {
            entity.stopRiding();
            if (!target.containsKey(entity.getUUID())) entity.discard();
        }
        for (var entry : target.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            CompoundTag actorData = RewindLifeState.forRestore(entry.getValue().data());
            // LivingEntity.load does not clear every server/client death flag. A fresh instance
            // with the original UUID also sends a fresh spawn to clients, clearing their death pose.
            if (entity instanceof net.minecraft.world.entity.LivingEntity && RewindLifeState.wasAlive(actorData)) {
                entity.stopRiding(); entity.discard(); entity = null;
            }
            if (entity == null || entity.isRemoved()) {
                boolean elsewhere = false;
                for (ServerLevel other : level.getServer().getAllLevels()) {
                    if (other != level && other.getEntity(entry.getKey()) != null) {
                        if (restoreRadius > 50) other.getEntity(entry.getKey()).discard();
                        else elsewhere = true;
                        break;
                    }
                }
                if (elsewhere) continue;
                if (restoreRadius > 50) level.getChunkAt(BlockPos.containing(entry.getValue().position()));
                entity = EntityType.loadEntityRecursive(actorData, level, e -> e);
                if (entity == null || !level.addFreshEntity(entity)) throw new IllegalStateException("Cannot restore entity " + entry.getKey());
            } else entity.load(actorData);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living && RewindLifeState.wasAlive(actorData)) {
                living.deathTime = 0; living.hurtTime = 0;
                if (living.getPose() == net.minecraft.world.entity.Pose.DYING) living.setPose(net.minecraft.world.entity.Pose.STANDING);
            }
            entity.teleportTo(entry.getValue().position().x, entry.getValue().position().y, entry.getValue().position().z);
            entity.hasImpulse = true;
        }
        for (var entry : snapshot.players().entrySet()) {
            var person = entry.getValue();
            if (!person.dimension().equals(level.dimension().location().toString()) || (!restoreInside(center, person.position()) && !participants.contains(entry.getKey()))) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || (restoreRadius == 50 && player.serverLevel() != level)) continue;
            if (player.isDeadOrDying() || player.deathTime > 0 || player.getPose() == net.minecraft.world.entity.Pose.DYING) player = level.getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            CompoundTag tag = person.data();
            player.closeContainer();
            player.stopRiding();
            player.getInventory().load(tag.getList("Inventory", 10));
            player.getInventory().selected = Math.max(0, Math.min(8, tag.getInt("SelectedItemSlot")));
            player.getEnderChestInventory().fromTag(tag.getList("EnderItems", 10), level.registryAccess());
            player.getFoodData().readAdditionalSaveData(tag);
            player.removeAllEffects();
            for (var effectTag : tag.getList("active_effects", 10)) {
                MobEffectInstance effect = MobEffectInstance.load((CompoundTag)effectTag);
                if (effect != null) player.addEffect(effect);
            }
            player.setHealth(Math.max(1f, tag.getFloat("Health")));
            player.setAbsorptionAmount(tag.getFloat("AbsorptionAmount"));
            player.deathTime = 0; player.hurtTime = 0; player.fallDistance = 0;
            player.experienceLevel = tag.getInt("XpLevel"); player.experienceProgress = tag.getFloat("XpP"); player.totalExperience = tag.getInt("XpTotal");
            player.setRemainingFireTicks(tag.getShort("Fire")); player.setAirSupply(tag.getShort("Air"));
            player.teleportTo(level, person.position().x, person.position().y, person.position().z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.inventoryMenu.setCarried(person.carried().isEmpty() ? ItemStack.EMPTY : ItemStack.parse(level.registryAccess(), person.carried()).orElse(ItemStack.EMPTY));
            player.getInventory().setChanged(); player.inventoryMenu.broadcastFullState();
            player.connection.send(new ClientboundSetHealthPacket(player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
            player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
        }
        for (var entry : target.entrySet()) {
            if (entry.getValue().vehicle() == null) continue;
            Entity entity = level.getEntity(entry.getKey()), vehicle = level.getEntity(entry.getValue().vehicle());
            if (entity != null && vehicle != null) entity.startRiding(vehicle, true);
        }
        for (var entry : snapshot.players().entrySet()) {
            var person = entry.getValue();
            if (!person.dimension().equals(level.dimension().location().toString()) || !restoreInside(center, person.position())
                    || !person.data().hasUUID("RewindVehicle")) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            Entity vehicle = level.getEntity(person.data().getUUID("RewindVehicle"));
            if (player != null && vehicle != null) player.startRiding(vehicle, true);
        }
        restoreSchedules(level, center, snapshot);
    }

    static void restoreEarthActors(ServerLevel level, BlockPos center, RewindSnapshot snapshot) {
        restoring = true; restoreRadius = 256;
        try { restore(level, center, Map.of(), snapshot, snapshot.players().keySet()); }
        finally { restoring = false; restoreRadius = 50; }
    }
    static void restoreEarthBlock(ServerLevel level, BlockPos pos, BlockState state) {
        boolean previousRestoring = restoring, previousSuppression = suppressSpawns;
        boolean previousCapture = level.captureBlockSnapshots;
        restoring = true; suppressSpawns = true; level.captureBlockSnapshots = true;
        try { restoreBlock(level, pos, state); }
        finally { restoring = previousRestoring; suppressSpawns = previousSuppression; level.captureBlockSnapshots = previousCapture; }
    }
    private static void restoreBlock(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before == state) return;
        // Call the chunk directly: keep vanilla light/heightmap updates without leaking captureBlockSnapshots entries.
        // setBlockState may return null for an already-air section. Verify the resulting state,
        // not the return value; cave_air/void_air are equivalent for an empty restored cell.
        level.getChunkAt(pos).setBlockState(pos, state, false);
        BlockState actual = level.getBlockState(pos);
        if (actual != state && !(actual.isAir() && state.isAir()))
            throw new IllegalStateException("Cannot restore block at " + pos + ": expected=" + state + ", actual=" + actual);
        level.sendBlockUpdated(pos, before, state, Block.UPDATE_CLIENTS);
    }

    private static void restoreSchedules(ServerLevel level, BlockPos center, RewindSnapshot snapshot) {
        for (var chunk : restoreRadius == 50 ? RewindSnapshot.chunks(level, center) : RewindSnapshot.loadedChunks(level, center, restoreRadius)) {
            ((net.minecraft.world.ticks.LevelChunkTicks<Block>)chunk.getBlockTicks()).removeIf(t -> restoreInside(center, t.pos()));
            ((net.minecraft.world.ticks.LevelChunkTicks<net.minecraft.world.level.material.Fluid>)chunk.getFluidTicks()).removeIf(t -> restoreInside(center, t.pos()));
        }
        for (var tick : snapshot.blockTicks()) level.getBlockTicks().schedule(new ScheduledTick<>(tick.type(), tick.pos(),
                level.getGameTime() + Math.max(1, tick.triggerTick() - snapshot.tick()), tick.priority(), tick.subTickOrder()));
        for (var tick : snapshot.fluidTicks()) level.getFluidTicks().schedule(new ScheduledTick<>(tick.type(), tick.pos(),
                level.getGameTime() + Math.max(1, tick.triggerTick() - snapshot.tick()), tick.priority(), tick.subTickOrder()));
    }

    private static void refreshDiscWear(ServerLevel level, BlockPos center) {
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) NegativeMusicDiscItem.synchronize(player.getInventory().getItem(i), level);
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) NegativeMusicDiscItem.synchronize(player.getEnderChestInventory().getItem(i), level);
            NegativeMusicDiscItem.synchronize(player.inventoryMenu.getCarried(), level);
            player.inventoryMenu.broadcastFullState();
        }
        for (var chunk : RewindSnapshot.chunks(level, center)) for (BlockEntity tile : chunk.getBlockEntities().values()) {
            if (tile instanceof Container container) for (int i = 0; i < container.getContainerSize(); i++) NegativeMusicDiscItem.synchronize(container.getItem(i), level);
            if (tile instanceof JukeboxBlockEntity jukebox && jukebox.getTheItem().isEmpty()
                    && jukebox.getBlockState().getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD))
                jukebox.setTheItem(ItemStack.EMPTY);
            tile.setChanged();
        }
        for (Entity entity : level.getEntities((Entity)null, new net.minecraft.world.phys.AABB(center).inflate(51)))
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) synchronizeDroppedDisc(item, level);
    }
    private static void previewBlock(ServerLevel level, Active active, long pos, BlockState state) {
        for (ServerPlayer player : viewers(level, active)) player.connection.send(new ClientboundBlockUpdatePacket(BlockPos.of(pos), state));
    }
    private static List<ServerPlayer> viewers(ServerLevel level, Active active) {
        return level.players().stream().filter(p -> active.participants.contains(p.getUUID()) || p.distanceToSqr(Vec3.atCenterOf(active.center)) < 72 * 72).toList();
    }
    private static void sendVisual(ServerLevel level, Active active, int age, RewindSnapshot snapshot) {
        List<RewindVisualPacket.Pose> poses = new ArrayList<>();
        snapshot.actors().forEach((id, actor) -> {
            Entity current = level.getEntity(id);
            if (current != null && RewindSnapshot.inside(active.center, actor.position())) poses.add(new RewindVisualPacket.Pose(current.getId(), actor.position()));
        });
        for (UUID id : active.participants) {
            var person = snapshot.players().get(id);
            ServerPlayer current = level.getServer().getPlayerList().getPlayer(id);
            if (person != null && current != null) poses.add(new RewindVisualPacket.Pose(current.getId(), person.position()));
        }
        RewindVisualPacket packet = new RewindVisualPacket(active.center, age, poses);
        for (ServerPlayer player : viewers(level, active)) PacketDistributor.sendToPlayer(player, packet);
    }
    private static void cancel(ServerLevel level, World world) {
        if (world.active == null) return;
        if (world.active.partialCommit) {
            restoring = true;
            try { restore(level, world.active.center, world.active.originalBlocks, world.active.start, world.active.participants); world.active.partialCommit = false; }
            finally { restoring = false; suppressSpawns = false; }
        }
        for (var entry : world.active.originalBlocks.entrySet()) previewBlock(level, world.active, entry.getKey(), level.getBlockState(BlockPos.of(entry.getKey())));
        try { restoreSchedules(level, world.active.center, world.active.start); }
        catch (RuntimeException error) { McanomalyarchivesMod.LOGGER.warn("Unable to resume scheduled ticks after rewind cancellation", error); }
        sendVisual(level, world.active, DURATION, world.active.start);
        finish(level, world);
    }
    private static void finish(ServerLevel level, World world) {
        world.active = null;
        world.requests.clear();
        for (Watch watch : world.watches.values()) { watch.history.clear(); watch.pending.clear(); watch.lastSample = Long.MIN_VALUE; }
    }
}
