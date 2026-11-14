package net.mcreator.mcanomalyarchives.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.rewind.RewindClient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public record RewindVisualPacket(BlockPos center, int age, List<Pose> poses, int radius) implements CustomPacketPayload {
    public RewindVisualPacket(BlockPos center, int age, List<Pose> poses) { this(center, age, poses, 50); }
    public record Pose(int id, Vec3 position) { }
    public static final Type<RewindVisualPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "rewind_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RewindVisualPacket> CODEC = StreamCodec.of((buf, packet) -> {
        buf.writeBlockPos(packet.center); buf.writeVarInt(packet.age); buf.writeVarInt(packet.radius); buf.writeVarInt(packet.poses.size());
        for (Pose pose : packet.poses) { buf.writeVarInt(pose.id); buf.writeDouble(pose.position.x); buf.writeDouble(pose.position.y); buf.writeDouble(pose.position.z); }
    }, buf -> {
        BlockPos center = buf.readBlockPos(); int age = buf.readVarInt(), radius = buf.readVarInt(), count = buf.readVarInt();
        if (radius != 50 && radius != 256) throw new IllegalArgumentException("Invalid rewind radius");
        if (count < 0 || count > 512 || age < 0 || age > 60) throw new IllegalArgumentException("Invalid rewind frame");
        List<Pose> poses = new ArrayList<>();
        for (int i = 0; i < count; i++) poses.add(new Pose(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
        return new RewindVisualPacket(center, age, List.copyOf(poses), radius);
    });
    @Override public Type<RewindVisualPacket> type() { return TYPE; }
    public static void handle(RewindVisualPacket packet, IPayloadContext context) {
        if (context.flow() == net.minecraft.network.protocol.PacketFlow.CLIENTBOUND) context.enqueueWork(() -> RewindClient.accept(packet));
    }
    @SubscribeEvent public static void register(FMLCommonSetupEvent event) {
        McanomalyarchivesMod.addNetworkMessage(TYPE, CODEC, RewindVisualPacket::handle);
    }
}
