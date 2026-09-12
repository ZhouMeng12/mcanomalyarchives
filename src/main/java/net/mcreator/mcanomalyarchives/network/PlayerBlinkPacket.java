package net.mcreator.mcanomalyarchives.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.BlinkClientHandler;

/**
 * 服务端 → 客户端：通知玩家“眨眼”（羊借此消失）。客户端播放黑屏眨眼演出。
 */
@EventBusSubscriber
public record PlayerBlinkPacket() implements CustomPacketPayload {

    public static final Type<PlayerBlinkPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "player_blink"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerBlinkPacket> STREAM_CODEC =
            StreamCodec.unit(new PlayerBlinkPacket());

    @Override
    public Type<PlayerBlinkPacket> type() {
        return TYPE;
    }

    public static void handleData(final PlayerBlinkPacket message, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(BlinkClientHandler::playBlink);
        }
    }

    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, PlayerBlinkPacket::handleData);
    }
}
