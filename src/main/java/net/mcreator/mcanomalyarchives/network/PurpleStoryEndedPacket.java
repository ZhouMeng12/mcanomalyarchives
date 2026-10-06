package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.screen.PurpleGuiClientHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端会话中止/结束：解除客户端演出状态，不在网络类里加载客户端专用类型。 */
public record PurpleStoryEndedPacket() implements CustomPacketPayload {
	public static final Type<PurpleStoryEndedPacket> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "purple_story_ended"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PurpleStoryEndedPacket> STREAM_CODEC =
			StreamCodec.of((buf, packet) -> { }, buf -> new PurpleStoryEndedPacket());

	@Override
	public Type<PurpleStoryEndedPacket> type() {
		return TYPE;
	}

	public static void handleData(PurpleStoryEndedPacket packet, IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(PurpleGuiClientHandler::close);
		}
	}

}
