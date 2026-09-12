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

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.SanityClientHandler;

@EventBusSubscriber
public record SyncPlayerSanityPacket(int sanity) implements CustomPacketPayload {
	public static final Type<SyncPlayerSanityPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "sync_player_sanity"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerSanityPacket> STREAM_CODEC = StreamCodec.composite(
		net.minecraft.network.codec.ByteBufCodecs.INT,
		SyncPlayerSanityPacket::sanity,
		SyncPlayerSanityPacket::new
	);

	@Override
	public Type<SyncPlayerSanityPacket> type() {
		return TYPE;
	}

	public static void handleData(final SyncPlayerSanityPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> {
				SanityClientHandler.updateCachedSanity(message.sanity);
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(SyncPlayerSanityPacket.TYPE, SyncPlayerSanityPacket.STREAM_CODEC, SyncPlayerSanityPacket::handleData);
	}
}
