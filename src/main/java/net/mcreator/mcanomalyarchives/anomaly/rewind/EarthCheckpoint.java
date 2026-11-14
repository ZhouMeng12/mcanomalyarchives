package net.mcreator.mcanomalyarchives.anomaly.rewind;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.*;
import net.minecraft.resources.ResourceLocation;
import net.mcreator.mcanomalyarchives.anomaly.earth.*;
import net.mcreator.mcanomalyarchives.item.NegativeMusicDiscItem;
import net.mcreator.mcanomalyarchives.network.*;
import net.mcreator.mcanomalyarchives.codex.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** One persistent pre-naming checkpoint per dimension. First-write journal has no ten-second expiry. */
@net.neoforged.fml.common.EventBusSubscriber(modid="mcanomalyarchives")
public final class EarthCheckpoint extends SavedData {
    private BlockPos center, jukebox;
    private UUID disc;
    private CompoundTag activationDisc = new CompoundTag();
    private RewindSnapshot snapshot;
    private final Map<Long, BlockState> blocks = new LinkedHashMap<>();
    private boolean working, charged;
    private int age, initialBlocks, previewEarthAge;
    private final Map<UUID,Vec3> previewStarts = new HashMap<>();
    private boolean paused;

    private static final Factory<EarthCheckpoint> FACTORY = new Factory<>(EarthCheckpoint::new, EarthCheckpoint::load, null);
    public static EarthCheckpoint get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY,"mcanomalyarchives_earth_checkpoint"); }
    public static void capture(ServerLevel level, BlockPos center) {
        var data=get(level);
        if(data.working) throw new IllegalStateException("Earth recall is still restoring");
        var snapshot=RewindSnapshot.capture(level,center,level.getGameTime(),256);
        data.center=center.immutable(); data.snapshot=snapshot; data.blocks.clear(); data.age=0; data.charged=false; data.disc=null; data.jukebox=null;
        data.setDirty();
    }
    public static boolean busy(ServerLevel level) { return get(level).working; }
    public static boolean frozen(ServerLevel level, BlockPos pos) {
        var d=get(level); return d.working && d.center!=null && horizontal(d.center,pos)<=256L*256;
    }
    private static long horizontal(BlockPos a, BlockPos b) { long x=(long)a.getX()-b.getX(),z=(long)a.getZ()-b.getZ(); return x*x+z*z; }
    public static void record(ServerLevel level, BlockPos pos, BlockState state) {
        var d=get(level);
        if(d.snapshot==null || d.working || horizontal(d.center,pos)>256L*256) return;
        if(d.blocks.putIfAbsent(pos.asLong(),state)==null) d.setDirty();
    }
    public static boolean activate(ServerLevel level, JukeboxBlockEntity box) {
        var d=get(level);
        if(d.snapshot==null || d.center.distSqr(box.getBlockPos())>256L*256) return false;
        if(d.working) return true;
        if(box.getTheItem().isEmpty()) return true;
        UUID disc=NegativeMusicDiscItem.identity(box.getTheItem());
        if(DiscUsageData.get(level).used(disc)>=DiscUsageData.MAX_USES) return true;
        d.activationDisc=(CompoundTag)box.getTheItem().save(level.registryAccess());
        d.disc=disc; d.jukebox=box.getBlockPos().immutable(); d.working=true; d.paused=false; d.age=0; d.initialBlocks=d.blocks.size(); d.previewStarts.clear();
        var earth=EarthService.snapshot(level); d.previewEarthAge=earth==null?799:Math.min(799,earth.age()); d.setDirty();
        box.getSongPlayer().stop(level,box.getBlockState());
        for(ServerPlayer p:level.players()) if(p.distanceToSqr(Vec3.atCenterOf(d.center))<=256*256) { p.closeContainer(); p.stopUsingItem(); }
        EarthService.hideForRecall(level);
        return true;
    }
    public static boolean tick(ServerLevel level) {
        var d=get(level); if(!d.working) return false;
        if(d.paused) return true;
        try { return advance(level,d); }
        catch(RuntimeException error) {
            d.paused=true; d.setDirty();
            net.mcreator.mcanomalyarchives.McanomalyarchivesMod.LOGGER.error("Earth recall paused; checkpoint and unfinished cells retained",error);
            for(ServerPlayer p:level.players()) {
                PacketDistributor.sendToPlayer(p,new RewindVisualPacket(d.center,60,List.of(),256));
                PacketDistributor.sendToPlayer(p,new EarthVisualPacket(level.dimension().location(),d.center,-1,true,false));
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("地球回溯已暂停，记录已保留。修复后可使用 /earthrecall resume 继续。"),false);
            }
            return true;
        }
    }
    private static boolean advance(ServerLevel level, EarthCheckpoint d) {
        d.age++; d.setDirty();
        float progress=EarthRecallVisual.progress(d.age,d.initialBlocks,d.blocks.size());
        List<RewindVisualPacket.Pose> poses=d.preview(level,progress);
        for(ServerPlayer p:level.players()) if(p.distanceToSqr(Vec3.atCenterOf(d.center))<=320*320)
            PacketDistributor.sendToPlayer(p,new RewindVisualPacket(d.center,EarthRecallVisual.packetAge(d.age,progress),poses,256));
        EarthService.previewRecall(level,d.center,d.previewEarthAge,progress);
        if(d.age<20) return true;
        long deadline=System.nanoTime()+8_000_000L;
        var iterator=((LinkedHashMap<Long,BlockState>)d.blocks).reversed().entrySet().iterator(); int done=0;
        while(iterator.hasNext() && done<4096) {
            var e=iterator.next(); BlockPos pos=BlockPos.of(e.getKey());
            // These are recorded, existing chunks, loaded only when their restoration batch is reached.
            level.getChunkAt(pos);
            RewindService.restoreEarthBlock(level,pos,e.getValue()); iterator.remove(); done++;
            if((done&63)==0 && System.nanoTime()>=deadline) break;
        }
        if(!d.blocks.isEmpty() || d.age<120) return true;
        // Stop the anchor-load guard before recreating the original, unmodified animal.
        EarthService.finishRecall(level);
        RewindService.restoreEarthActors(level,d.center,d.snapshot);
        if (!d.charged) { DiscUsageData.get(level).consume(d.disc); d.charged=true; d.setDirty(); }
        for(ServerPlayer p:level.players()) {
            for(int i=0;i<p.getInventory().getContainerSize();i++) NegativeMusicDiscItem.synchronize(p.getInventory().getItem(i),level);
            for(int i=0;i<p.getEnderChestInventory().getContainerSize();i++) NegativeMusicDiscItem.synchronize(p.getEnderChestInventory().getItem(i),level);
            if(p.distanceToSqr(Vec3.atCenterOf(d.center))<=320*320) {
                CodexObservations.record(p,"earth_rewind"); CodexObservations.record(p,"negative_disc_rewind");
                if(DiscUsageData.get(level).used(d.disc)>=DiscUsageData.MAX_USES) ArchiveAdvancements.award(p,"disc_spent");
                PacketDistributor.sendToPlayer(p,new RewindVisualPacket(d.center,60,List.of(),256));
                PacketDistributor.sendToPlayer(p,new EarthVisualPacket(level.dimension().location(),d.center,-1,true,false));
            }
        }
        for(var chunk:RewindSnapshot.loadedChunks(level,d.center,256)) for(var tile:chunk.getBlockEntities().values())
            if(tile instanceof JukeboxBlockEntity box) { NegativeMusicDiscItem.synchronize(box.getTheItem(),level); box.setChanged(); }
        d.working=false;
        // A jukebox placed after the checkpoint disappears on recall. Preserve its inserted disc
        // unless that same identity has already returned to a recorded inventory.
        if(!discExists(level,d.center,d.disc)) {
            var stack=net.minecraft.world.item.ItemStack.parse(level.registryAccess(),d.activationDisc).orElse(net.minecraft.world.item.ItemStack.EMPTY);
            NegativeMusicDiscItem.synchronize(stack,level);
            if(!stack.isEmpty()) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,d.jukebox.getX()+.5,d.jukebox.getY()+1,d.jukebox.getZ()+.5,stack));
        }
        d.snapshot=null; d.disc=null; d.activationDisc=new CompoundTag(); d.setDirty(); return true;
    }
    private List<RewindVisualPacket.Pose> preview(ServerLevel level,float progress) {
        List<RewindVisualPacket.Pose> result=new ArrayList<>();
        for(var e:snapshot.actors().entrySet()) {
            if(result.size()>=480)break;
            if(e.getValue().position().distanceToSqr(Vec3.atCenterOf(center))>256*256)continue;
            var entity=level.getEntity(e.getKey()); if(entity==null)continue;
            Vec3 from=previewStarts.computeIfAbsent(e.getKey(),id->entity.position());
            result.add(new RewindVisualPacket.Pose(entity.getId(),from.lerp(e.getValue().position(),progress)));
        }
        for(var e:snapshot.players().entrySet()) {
            if(result.size()>=512)break;
            if(!e.getValue().dimension().equals(level.dimension().location().toString()) || e.getValue().position().distanceToSqr(Vec3.atCenterOf(center))>256*256)continue;
            var player=level.getServer().getPlayerList().getPlayer(e.getKey());if(player==null || player.serverLevel()!=level)continue;
            Vec3 from=previewStarts.computeIfAbsent(e.getKey(),id->player.position());
            result.add(new RewindVisualPacket.Pose(player.getId(),from.lerp(e.getValue().position(),progress)));
        }
        return List.copyOf(result);
    }
    public static boolean resume(ServerLevel level) {
        var d=get(level);if(!d.working)return false;d.paused=false;d.setDirty();return true;
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("earthrecall").requires(s->s.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("resume").executes(c->resume(c.getSource().getLevel())?1:0)));
    }
    private static boolean sameDisc(net.minecraft.world.item.ItemStack stack,UUID id) {
        return stack.getItem() instanceof NegativeMusicDiscItem && NegativeMusicDiscItem.identity(stack).equals(id);
    }
    private static boolean discExists(ServerLevel level,BlockPos center,UUID id) {
        for(ServerPlayer p:level.getServer().getPlayerList().getPlayers()) {
            for(int i=0;i<p.getInventory().getContainerSize();i++) if(sameDisc(p.getInventory().getItem(i),id))return true;
            for(int i=0;i<p.getEnderChestInventory().getContainerSize();i++) if(sameDisc(p.getEnderChestInventory().getItem(i),id))return true;
        }
        for(var chunk:RewindSnapshot.loadedChunks(level,center,256)) for(var tile:chunk.getBlockEntities().values()) {
            if(tile instanceof JukeboxBlockEntity j && sameDisc(j.getTheItem(),id))return true;
            if(tile instanceof net.minecraft.world.Container c) for(int i=0;i<c.getContainerSize();i++) if(sameDisc(c.getItem(i),id))return true;
        }
        for(var drop:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(center).inflate(272)))
            if(sameDisc(drop.getItem(),id))return true;
        return false;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        if(snapshot==null) return tag;
        tag.putLong("Center",center.asLong());tag.putBoolean("Working",working);tag.putBoolean("Charged",charged);tag.putInt("Age",age);tag.putInt("InitialBlocks",initialBlocks);tag.putInt("PreviewEarthAge",previewEarthAge);tag.putBoolean("Paused",paused);
        if(disc!=null) tag.putUUID("Disc",disc);tag.put("ActivationDisc",activationDisc.copy());
        if(jukebox!=null) tag.putLong("Jukebox",jukebox.asLong());
        ListTag palette=new ListTag(); Map<BlockState,Integer> indices=new HashMap<>();
        long[] positions=new long[blocks.size()];int[] states=new int[blocks.size()];int i=0;
        for(var e:blocks.entrySet()) { positions[i]=e.getKey();Integer id=indices.get(e.getValue());
            if(id==null){id=indices.size();indices.put(e.getValue(),id);palette.add(NbtUtils.writeBlockState(e.getValue()));} states[i++]=id; }
        tag.put("Palette",palette);tag.putLongArray("Positions",positions);tag.putIntArray("States",states);
        tag.put("Snapshot",encode(snapshot));return tag;
    }
    private static EarthCheckpoint load(CompoundTag tag, HolderLookup.Provider lookup) {
        var d=new EarthCheckpoint();if(!tag.contains("Snapshot"))return d;
        d.center=BlockPos.of(tag.getLong("Center"));d.working=tag.getBoolean("Working");d.charged=tag.getBoolean("Charged");d.age=tag.getInt("Age");d.initialBlocks=tag.getInt("InitialBlocks");d.previewEarthAge=tag.contains("PreviewEarthAge")?tag.getInt("PreviewEarthAge"):799;d.paused=tag.getBoolean("Paused");
        d.activationDisc=tag.getCompound("ActivationDisc");if(tag.hasUUID("Disc"))d.disc=tag.getUUID("Disc");d.jukebox=BlockPos.of(tag.getLong("Jukebox"));
        var palette=tag.getList("Palette",10);long[] positions=tag.getLongArray("Positions");int[] states=tag.getIntArray("States");
        List<BlockState> decoded=new ArrayList<>();for(var e:palette)decoded.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),(CompoundTag)e));
        for(int i=0;i<positions.length;i++)d.blocks.put(positions[i],decoded.get(states[i]));
        d.initialBlocks=Math.max(d.initialBlocks,d.blocks.size());d.snapshot=decode(tag.getCompound("Snapshot"));return d;
    }
    static CompoundTag encode(RewindSnapshot s) {
        CompoundTag out=new CompoundTag();out.putLong("Tick",s.tick());ListTag tiles=new ListTag(),actors=new ListTag(),players=new ListTag(),bt=new ListTag(),ft=new ListTag();
        s.tiles().forEach((pos,t)->{var n=new CompoundTag();n.putLong("Pos",pos);n.put("State",NbtUtils.writeBlockState(t.state()));n.put("Data",t.data().copy());n.put("Inventory",t.inventory().copy());tiles.add(n);});
        s.actors().forEach((id,a)->{var n=new CompoundTag();n.putUUID("UUID",id);n.putInt("Runtime",a.runtimeId());n.putDouble("X",a.position().x);n.putDouble("Y",a.position().y);n.putDouble("Z",a.position().z);n.put("Data",a.data().copy());if(a.vehicle()!=null)n.putUUID("Vehicle",a.vehicle());actors.add(n);});
        s.players().forEach((id,p)->{var n=new CompoundTag();n.putUUID("UUID",id);n.putString("Dimension",p.dimension());n.putDouble("X",p.position().x);n.putDouble("Y",p.position().y);n.putDouble("Z",p.position().z);n.put("Data",p.data().copy());n.put("Carried",p.carried().copy());players.add(n);});
        for(var t:s.blockTicks())bt.add(tickTag(BuiltInRegistries.BLOCK.getKey(t.type()).toString(),t.pos(),t.triggerTick(),t.priority().getValue(),t.subTickOrder()));
        for(var t:s.fluidTicks())ft.add(tickTag(BuiltInRegistries.FLUID.getKey(t.type()).toString(),t.pos(),t.triggerTick(),t.priority().getValue(),t.subTickOrder()));
        out.put("Tiles",tiles);out.put("Actors",actors);out.put("Players",players);out.put("BlockTicks",bt);out.put("FluidTicks",ft);return out;
    }
    private static CompoundTag tickTag(String id,BlockPos pos,long time,int priority,long order){var n=new CompoundTag();n.putString("Id",id);n.putLong("Pos",pos.asLong());n.putLong("Time",time);n.putInt("Priority",priority);n.putLong("Order",order);return n;}
    static RewindSnapshot decode(CompoundTag tag) {
        Map<Long,RewindSnapshot.Tile> tiles=new HashMap<>();Map<UUID,RewindSnapshot.Actor> actors=new HashMap<>();Map<UUID,RewindSnapshot.Person> players=new HashMap<>();
        for(var e:tag.getList("Tiles",10)){var n=(CompoundTag)e;tiles.put(n.getLong("Pos"),new RewindSnapshot.Tile(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),n.getCompound("State")),n.getCompound("Data"),n.getCompound("Inventory")));}
        for(var e:tag.getList("Actors",10)){var n=(CompoundTag)e;actors.put(n.getUUID("UUID"),new RewindSnapshot.Actor(n.getInt("Runtime"),new Vec3(n.getDouble("X"),n.getDouble("Y"),n.getDouble("Z")),n.getCompound("Data"),n.hasUUID("Vehicle")?n.getUUID("Vehicle"):null));}
        for(var e:tag.getList("Players",10)){var n=(CompoundTag)e;players.put(n.getUUID("UUID"),new RewindSnapshot.Person(n.getString("Dimension"),new Vec3(n.getDouble("X"),n.getDouble("Y"),n.getDouble("Z")),n.getCompound("Data"),n.getCompound("Carried")));}
        List<ScheduledTick<net.minecraft.world.level.block.Block>> bt=new ArrayList<>();List<ScheduledTick<net.minecraft.world.level.material.Fluid>> ft=new ArrayList<>();
        for(var e:tag.getList("BlockTicks",10)){var n=(CompoundTag)e;bt.add(new ScheduledTick<>(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(n.getString("Id"))),BlockPos.of(n.getLong("Pos")),n.getLong("Time"),TickPriority.byValue(n.getInt("Priority")),n.getLong("Order")));}
        for(var e:tag.getList("FluidTicks",10)){var n=(CompoundTag)e;ft.add(new ScheduledTick<>(BuiltInRegistries.FLUID.get(ResourceLocation.parse(n.getString("Id"))),BlockPos.of(n.getLong("Pos")),n.getLong("Time"),TickPriority.byValue(n.getInt("Priority")),n.getLong("Order")));}
        return new RewindSnapshot(tag.getLong("Tick"),Map.copyOf(tiles),Map.copyOf(actors),Map.copyOf(players),List.copyOf(bt),List.copyOf(ft));
    }
}
