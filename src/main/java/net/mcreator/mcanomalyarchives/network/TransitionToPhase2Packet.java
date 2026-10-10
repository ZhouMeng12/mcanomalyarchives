package net.mcreator.mcanomalyarchives.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;
import net.minecraft.server.level.ServerPlayer;

public record TransitionToPhase2Packet(int purpleEntityId) implements CustomPacketPayload {
	public static final Type<TransitionToPhase2Packet> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "transition_to_phase2"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TransitionToPhase2Packet> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			TransitionToPhase2Packet::purpleEntityId,
			TransitionToPhase2Packet::new
		);

	@Override
	public Type<TransitionToPhase2Packet> type() {
		return TYPE;
	}

	public static void handleData(final TransitionToPhase2Packet message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player) {
					PurpleStoryModule.advanceToPhase2(player, message.purpleEntityId);
				}
			});
		}
	}

}
