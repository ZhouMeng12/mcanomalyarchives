package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.event.PurplehandAOEHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 挥空时原版只发送挥手包，单独报告左键；目标和伤害均由服务端判定。 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public record PurplehandSweepPacket() implements CustomPacketPayload {
    public static final Type<PurplehandSweepPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "purplehand_sweep"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PurplehandSweepPacket> STREAM_CODEC =
            StreamCodec.unit(new PurplehandSweepPacket());

    @Override
    public Type<PurplehandSweepPacket> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("purplehand-1").playToServer(TYPE, STREAM_CODEC, (message, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PurplehandAOEHandler.swingAtAir(player);
                    }
                }));
    }
}
