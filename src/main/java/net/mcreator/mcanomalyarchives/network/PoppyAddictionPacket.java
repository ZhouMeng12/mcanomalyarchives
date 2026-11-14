package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.events.AddictionClientHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public record PoppyAddictionPacket(int ticks, int route, int sadTicks, double x, double y, double z,
        int entityId, long seed) implements CustomPacketPayload {
    public static final Type<PoppyAddictionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            McanomalyarchivesMod.MODID, "poppy_addiction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PoppyAddictionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.ticks); buf.writeVarInt(p.route); buf.writeVarInt(p.sadTicks);
                buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
                buf.writeInt(p.entityId); buf.writeLong(p.seed); },
            buf -> new PoppyAddictionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readLong()));
    @Override public Type<PoppyAddictionPacket> type() { return TYPE; }
    public static void handle(PoppyAddictionPacket packet, IPayloadContext context) {
        if (context.flow() == net.minecraft.network.protocol.PacketFlow.CLIENTBOUND)
            context.enqueueWork(() -> AddictionClientHandler.accept(packet));
    }
    @SubscribeEvent public static void register(FMLCommonSetupEvent event) {
        McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, PoppyAddictionPacket::handle);
    }
}
