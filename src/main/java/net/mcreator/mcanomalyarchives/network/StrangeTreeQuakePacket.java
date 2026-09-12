package net.mcreator.mcanomalyarchives.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.TreeQuakeClientHandler;

/** 服务端 → 客户端：怪树地震/震颤（振幅、时长可配：埋伏小幅、钻地/钻出大幅） */
@EventBusSubscriber
public record StrangeTreeQuakePacket(float amplitude, int durationTicks) implements CustomPacketPayload {

    public static final Type<StrangeTreeQuakePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "strange_tree_quake"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StrangeTreeQuakePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, StrangeTreeQuakePacket::amplitude,
                    ByteBufCodecs.VAR_INT, StrangeTreeQuakePacket::durationTicks,
                    StrangeTreeQuakePacket::new);

    @Override
    public Type<StrangeTreeQuakePacket> type() {
        return TYPE;
    }

    public static void handleData(final StrangeTreeQuakePacket message, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> TreeQuakeClientHandler.startShake(message.amplitude(), message.durationTicks()));
        }
    }

    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, StrangeTreeQuakePacket::handleData);
    }
}
