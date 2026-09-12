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
import net.mcreator.mcanomalyarchives.event.PurpleDogPettingClientHandler;

@EventBusSubscriber
public record StartPettingClientPacket(int purpleEntityId, int dogEntityId, double startX, double startY, double startZ) implements CustomPacketPayload {
	public static final Type<StartPettingClientPacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "start_petting_client"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StartPettingClientPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			StartPettingClientPacket::purpleEntityId,
			ByteBufCodecs.INT,
			StartPettingClientPacket::dogEntityId,
			ByteBufCodecs.DOUBLE,
			StartPettingClientPacket::startX,
			ByteBufCodecs.DOUBLE,
			StartPettingClientPacket::startY,
			ByteBufCodecs.DOUBLE,
			StartPettingClientPacket::startZ,
			StartPettingClientPacket::new
		);

	@Override
	public Type<StartPettingClientPacket> type() {
		return TYPE;
	}

	public static void handleData(final StartPettingClientPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> {
				PurpleDogPettingClientHandler.startPetting(
					message.purpleEntityId, message.dogEntityId,
					message.startX, message.startY, message.startZ);
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, StartPettingClientPacket::handleData);
	}
}
