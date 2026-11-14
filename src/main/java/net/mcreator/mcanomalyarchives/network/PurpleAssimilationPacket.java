package net.mcreator.mcanomalyarchives.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PurpleAssimilationPacket(UUID player, int stage) implements CustomPacketPayload {
    public static final Type<PurpleAssimilationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            "mcanomalyarchives", "purple_assimilation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PurpleAssimilationPacket> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.player); b.writeVarInt(p.stage); },
            b -> new PurpleAssimilationPacket(b.readUUID(), b.readVarInt()));
    @Override public Type<PurpleAssimilationPacket> type() { return TYPE; }
    public static void handle(PurpleAssimilationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> net.mcreator.mcanomalyarchives.client.purple.PurpleAssimilationClient.accept(packet));
    }
}
