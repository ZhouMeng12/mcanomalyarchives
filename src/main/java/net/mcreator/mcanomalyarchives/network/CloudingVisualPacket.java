package net.mcreator.mcanomalyarchives.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.clouding.CloudingVisualState;

import java.util.UUID;

/** 服务端 → 客户端：同步生物"云化灰白"视觉标记（不依赖效果在客户端的可见性） */
@EventBusSubscriber
public record CloudingVisualPacket(int entityId, UUID entityUUID, boolean gray) implements CustomPacketPayload {

	public static final Type<CloudingVisualPacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "clouding_visual"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CloudingVisualPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, CloudingVisualPacket::entityId,
			ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), CloudingVisualPacket::entityUUID,
			ByteBufCodecs.BOOL, CloudingVisualPacket::gray,
			CloudingVisualPacket::new);

	@Override
	public Type<CloudingVisualPacket> type() {
		return TYPE;
	}

	public static void handleData(final CloudingVisualPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> CloudingVisualState.set(message.entityId(), message.entityUUID(), message.gray()));
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, CloudingVisualPacket::handleData);
	}
}
