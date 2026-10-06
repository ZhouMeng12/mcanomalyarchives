package net.mcreator.mcanomalyarchives.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;

public record PettingCompletePacket(int purpleEntityId, int dogEntityId) implements CustomPacketPayload {
	public static final Type<PettingCompletePacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "petting_complete"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PettingCompletePacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			PettingCompletePacket::purpleEntityId,
			ByteBufCodecs.INT,
			PettingCompletePacket::dogEntityId,
			PettingCompletePacket::new
		);

	@Override
	public Type<PettingCompletePacket> type() {
		return TYPE;
	}

	public static void handleData(final PettingCompletePacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player) {
					PurpleStoryModule.completePetting(player, message.purpleEntityId, message.dogEntityId);
				}
			});
		}
	}

}
