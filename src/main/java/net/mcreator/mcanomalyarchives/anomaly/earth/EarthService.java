package net.mcreator.mcanomalyarchives.anomaly.earth;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.mcreator.mcanomalyarchives.anomaly.rewind.RewindService;
import net.mcreator.mcanomalyarchives.codex.CodexObservations;
import net.mcreator.mcanomalyarchives.network.EarthVisualPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "mcanomalyarchives")
public final class EarthService {
    private static final List<BlockPos> BLAST_COLUMNS = blastColumns();
    private EarthService() { }
    private static List<BlockPos> blastColumns() {
        List<BlockPos> positions = new ArrayList<>();
        int r = EarthRules.FINAL_RADIUS;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
            if (x*x + z*z <= r*r) positions.add(new BlockPos(x,0,z));
        // Clear adjacent 16x16 regions together; never scatter individual columns across the crater.
        positions.sort(Comparator.comparingInt((BlockPos p) -> Math.floorDiv(p.getX(),16))
            .thenComparingInt(p -> Math.floorDiv(p.getZ(),16))
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(positions);
    }
    public static boolean start(ServerPlayer player, LivingEntity anchor, String name) {
        ServerLevel level = player.serverLevel();
        EarthData data = EarthData.get(level);
        if (data.state != null || net.mcreator.mcanomalyarchives.anomaly.rewind.EarthCheckpoint.busy(level)) {
            player.displayClientMessage(Component.translatable("message.mcanomalyarchives.earth.active"), true); return false;
        }
        net.mcreator.mcanomalyarchives.anomaly.rewind.EarthCheckpoint.capture(level, anchor.blockPosition());
        data.set(new EarthData.State(UUID.randomUUID(), anchor.getUUID(), anchor.blockPosition(), 0, 0, 0, EarthRules.COLUMN_START,
            anchor instanceof Mob mob && mob.isNoAi(), anchor.isNoGravity(), new int[0]));
        anchor.getPersistentData().putUUID("McanomalyEarthAnchor", data.state.id());
        anchor.setCustomName(Component.literal(name)); anchor.setCustomNameVisible(true);
        if (anchor instanceof Mob mob) { mob.setNoAi(true); mob.setPersistenceRequired(); }
        anchor.setNoGravity(true);
        CodexObservations.record(player, "name_earth"); send(level, data.state, false); return true;
    }
    public static void hideForRecall(ServerLevel level) {
        var s=snapshot(level); if(s==null)return;
        for(ServerPlayer p:level.players()) PacketDistributor.sendToPlayer(p,new EarthVisualPacket(level.dimension().location(),s.center(),-1,true,false));
    }
    public static void previewRecall(ServerLevel level, BlockPos center, int startAge, float progress) {
        int age=Math.max(0,(int)(startAge*(1-progress)));
        for(ServerPlayer p:level.players()) PacketDistributor.sendToPlayer(p,new EarthVisualPacket(level.dimension().location(),center,age,true,false));
    }
    public static void finishRecall(ServerLevel level) { hideForRecall(level); EarthData.get(level).set(null); }
    public static EarthData.State snapshot(ServerLevel level) { return EarthData.get(level).state; }
    public static boolean associated(ServerLevel level, BlockPos watch) {
        var s = snapshot(level); return s != null && watch.distSqr(s.center()) <= 50 * 50;
    }
    public static boolean affected(ServerLevel level, BlockPos watch, BlockPos pos) {
        var s = snapshot(level);
        if (s == null || watch.distSqr(s.center()) > 50 * 50) return false;
        long dx = (long)pos.getX() - s.center().getX(), dz = (long)pos.getZ() - s.center().getZ();
        return dx * dx + dz * dz <= EarthRules.FINAL_RADIUS * EarthRules.FINAL_RADIUS
            && pos.getY() >= s.center().getY() - EarthRules.DEPTH && pos.getY() < level.getMaxBuildHeight();
    }
    /** Called only after a successful world transaction, using the same historical frame. */
    public static void restored(ServerLevel level, BlockPos watch, EarthData.State historical) {
        var current = snapshot(level);
        if (current == null || watch.distSqr(current.center()) > 50 * 50) return;
        if (historical != null && !historical.id().equals(current.id())) historical = null;
        EarthData.get(level).set(historical);
        send(level, historical == null ? current : historical, historical == null, false);
        if (historical == null) for (ServerPlayer p : level.players())
            if (p.distanceToSqr(Vec3.atCenterOf(current.center())) < 128 * 128) CodexObservations.record(p, "earth_rewind");
    }
    public static void preview(ServerLevel level, BlockPos watch, EarthData.State historical) {
        var current = snapshot(level);
        if (current == null || watch.distSqr(current.center()) > 50 * 50) return;
        send(level, historical == null ? new EarthData.State(current.id(), current.anchor(), current.center(), 0, 0, 0, EarthRules.COLUMN_START,
            current.noAi(), current.noGravity(), new int[0]) : historical, true);
    }
    private static void send(ServerLevel level, EarthData.State s, boolean rewind) { send(level, s, false, rewind); }
    private static void send(ServerLevel level, EarthData.State s, boolean stop, boolean rewind) {
        boolean clearing = s.phase() == 1 && s.column() < BLAST_COLUMNS.size();
        var packet = new EarthVisualPacket(level.dimension().location(), s.center(), stop && !clearing ? -1 : Math.min(799, s.age()), rewind, clearing);
        for (ServerPlayer p : level.players()) PacketDistributor.sendToPlayer(p, packet);
    }
    @SubscribeEvent public static void anchorLoaded(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || RewindService.restoring()) return;
        var tag = event.getEntity().getPersistentData();
        if (!tag.hasUUID("McanomalyEarthAnchor")) return;
        var state = snapshot(level);
        if (state == null || !state.id().equals(tag.getUUID("McanomalyEarthAnchor")) || state.age() >= EarthRules.END_AGE) {
            event.setCanceled(true); event.getEntity().discard();
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            EarthData data = EarthData.get(level); var s = data.state;
            if (s == null || RewindService.frozen(level, s.center())) continue;
            // No ticking ticket is added. With nobody nearby, retain the saved cursor for later.
            if (!level.hasChunkAt(s.center())) continue;
            int age = s.age(), phase = s.phase(), column = s.column(), y = s.y();
            // Terrain removal begins only after detonation; cursor is persisted and rewindable.
            ArrayDeque<Integer> deferred = new ArrayDeque<>();
            boolean complete = true;
            if (phase == 0 && age >= EarthRules.BLAST_AGE - 1 && complete) {
                phase = 2; y = 0; // Visual charge transitions directly into the blast.
            }
            if (phase == 2 && y <= 0) {
                stopInwardMotion(level, s);
                phase = 1; column = 0; y = EarthRules.COLUMN_START; deferred.clear(); age = EarthRules.BLAST_AGE;
                net.mcreator.mcanomalyarchives.codex.ArchiveAdvancements.earthBlast(level, s.id(), s.center());
                Entity anchor = level.getEntity(s.anchor());
                level.explode(anchor, s.center().getX() + .5, s.center().getY() + .5, s.center().getZ() + .5,
                    EarthRules.EXPLOSION_POWER, false, Level.ExplosionInteraction.BLOCK);
                for (int i = 0; i < 8; i++) {
                    var bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        double angle = Math.PI * 2 * i / 8;
                        bolt.moveTo(s.center().getX() + Math.cos(angle) * 24, s.center().getY(), s.center().getZ() + Math.sin(angle) * 24);
                        bolt.setVisualOnly(true); level.addFreshEntity(bolt);
                    }
                }
                level.playSound(null, s.center(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 24, .6f);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, s.center().getX(), s.center().getY(), s.center().getZ(), 12, 16, 8, 16, .1);
                for (ServerPlayer p : level.players()) if (p.distanceToSqr(Vec3.atCenterOf(s.center())) < 256 * 256)
                    CodexObservations.record(p, "earth_blast");
            } else if (phase == 2) { y--; age = EarthRules.BLAST_AGE - 1; }
            else if (phase == 0) age = Math.min(EarthRules.BLAST_AGE - 1, age + 1);
            else age++;
            if (phase == 1 && age >= EarthRules.BLAST_AGE) {
                int scans = 0, changed = 0;
                long deadline = System.nanoTime() + 8_000_000L;
                while (column < BLAST_COLUMNS.size() && scans < EarthRules.SCAN_BUDGET && changed < EarthRules.BLOCK_BUDGET) {
                    if (scans > 0 && (scans & 63) == 0 && System.nanoTime() >= deadline) break;
                    BlockPos offset = BLAST_COLUMNS.get(column);
                    BlockPos top = s.center().offset(offset);
                    if (!level.hasChunkAt(top)) { column++; y=EarthRules.COLUMN_START; scans++; continue; }
                    if (y == EarthRules.COLUMN_START)
                        y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,top.getX(),top.getZ())-1-s.center().getY();
                    int bottom=Math.max(level.getMinBuildHeight()-s.center().getY(),EarthRules.bottom(offset.getX(),offset.getZ(),1));
                    if (y < bottom) { column++; y=EarthRules.COLUMN_START; scans++; continue; }
                    BlockPos pos=s.center().offset(offset.getX(),y,offset.getZ());
                    var block=level.getBlockState(pos);
                    if (!block.isAir() && !block.hasBlockEntity() && block.getDestroySpeed(level,pos)>=0
                        && level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE)) changed++;
                    scans++; y--;
                }
                if (age == 610) level.playSound(null,s.center(),SoundEvents.LIGHTNING_BOLT_THUNDER,SoundSource.WEATHER,24,.55f);
                if (age == 635 || age == 670 || age == 715)
                    level.playSound(null,s.center(),SoundEvents.GENERIC_EXPLODE.value(),SoundSource.BLOCKS,
                        age == 635 ? 12 : age == 670 ? 7 : 3,.5f);
            }
            var next = new EarthData.State(s.id(), s.anchor(), s.center(), age, phase, column, y, s.noAi(), s.noGravity(), deferred.stream().mapToInt(Integer::intValue).toArray());
            data.set(next);
            if (age < EarthRules.BLAST_AGE) {
                pin(level, next); pull(level, next);
                if (age % 40 == 0) {
                    level.playSound(null, s.center(), SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 5, .5f);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, s.center().getX(), s.center().getY(), s.center().getZ(), 32, 12, 8, 12, .08);
                }
            } else if (age >= EarthRules.END_AGE) {
                if (s.age() < EarthRules.END_AGE) net.mcreator.mcanomalyarchives.codex.ArchiveAdvancements.earthEnd(level, s.id());
                Entity anchor = level.getEntity(next.anchor());
                if (anchor != null) anchor.discard();
                if (s.age() < EarthRules.END_AGE || age % 20 == 0) send(level, next, true, false);
            }
            if (age < EarthRules.END_AGE && (age % 5 == 0 || age == EarthRules.BLAST_AGE)) send(level, next, false);
            if (age > EarthRules.END_AGE + 220 && column >= BLAST_COLUMNS.size()) { send(level, next, true, false); data.set(null); }
        }
    }
    private static void pin(ServerLevel level, EarthData.State s) {
        Entity anchor = level.getEntity(s.anchor()); if (anchor == null) return;
        anchor.getPersistentData().putUUID("McanomalyEarthAnchor", s.id());
        if (anchor instanceof Mob mob) mob.setNoAi(true);
        anchor.setNoGravity(true); anchor.setDeltaMovement(Vec3.ZERO);
        double drop = Math.min(36, s.age() / 600.0 * 36);
        anchor.teleportTo(s.center().getX() + .5, Math.max(level.getMinBuildHeight() + 1, s.center().getY() - drop), s.center().getZ() + .5);
        anchor.fallDistance = 0;
    }
    private static void stopInwardMotion(ServerLevel level, EarthData.State s) {
        Vec3 center = Vec3.atCenterOf(s.center()).add(0, -32, 0);
        for (Entity entity : level.getEntities((Entity)null, new AABB(s.center()).inflate(EarthRules.FIELD_RADIUS))) {
            if (entity.isSpectator() || entity instanceof Player p && p.isCreative()) continue;
            if (!(entity instanceof LivingEntity) && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)) continue;
            Vec3 toCenter = center.subtract(entity.position());
            if (toCenter.lengthSqr() > EarthRules.FIELD_RADIUS * EarthRules.FIELD_RADIUS) continue;
            entity.setDeltaMovement(EarthRules.releaseVelocity(entity.getDeltaMovement(), toCenter)); entity.hasImpulse = true;
            if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }
    @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("earthstatus").requires(source -> source.hasPermission(2)).executes(context -> {
            var state = snapshot(context.getSource().getLevel());
            String text = state == null ? "地球 v12：当前维度无活动事件。" : "地球 v12：阶段=" + state.phase()
                + "（0引力 / 2引爆 / 1爆炸后清除），年龄=" + state.age()
                + "，清除扫描=" + (state.phase() == 1 && state.column() < BLAST_COLUMNS.size())
                + "，吸引=" + (EarthRules.pull(4, state.age()) > 0) + "，延迟列=" + state.deferred().length;
            context.getSource().sendSuccess(() -> Component.literal(text), false); return 1;
        }));
    }
    private static void pull(ServerLevel level, EarthData.State s) {
        Vec3 center = Vec3.atCenterOf(s.center()).add(0, -Math.min(32, s.age() / 600.0 * 32), 0);
        int processed = 0;
        for (Entity entity : level.getEntities((Entity)null, new AABB(s.center()).inflate(EarthRules.FIELD_RADIUS))) {
            if (processed++ >= 2048) break;
            if (entity.getUUID().equals(s.anchor()) || entity.isSpectator() || entity instanceof Player p && p.isCreative()) continue;
            if (!(entity instanceof LivingEntity) && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)) continue;
            if (RewindService.frozen(level, entity.blockPosition())) continue;
            Vec3 delta = center.subtract(entity.position()); double force = EarthRules.pull(delta.length(), s.age());
            if (force <= 0) continue;
            Vec3 velocity = entity.getDeltaMovement().add(delta.normalize().scale(force));
            if (velocity.lengthSqr() > 6.25) velocity = velocity.normalize().scale(2.5);
            entity.setDeltaMovement(velocity); entity.hasImpulse = true;
            if (entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                if (s.age() % 40 == 0) CodexObservations.record(player, "earth_pull");
            }
        }
    }
}
