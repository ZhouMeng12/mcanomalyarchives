package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cancel an identified encounter without selecting a dialogue outcome. */
public record CancelPurpleStoryPacket(int purpleId, int dogId) implements CustomPacketPayload {
    public static final Type<CancelPurpleStoryPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "cancel_purple_story"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CancelPurpleStoryPacket> STREAM_CODEC =
            StreamCodec.of((b, p) -> { b.writeInt(p.purpleId); b.writeInt(p.dogId); },
                    b -> new CancelPurpleStoryPacket(b.readInt(), b.readInt()));
    @Override public Type<CancelPurpleStoryPacket> type() { return TYPE; }
    public static void handle(CancelPurpleStoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                PurpleStoryModule.cancel(player, packet.purpleId, packet.dogId);
        });
    }
}
