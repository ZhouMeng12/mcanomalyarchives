package net.mcreator.mcanomalyarchives.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;

public record PetPurpleDogPacket(int purpleEntityId, int dogEntityId) implements CustomPacketPayload {
	public static final Type<PetPurpleDogPacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "pet_purple_dog"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PetPurpleDogPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			PetPurpleDogPacket::purpleEntityId,
			ByteBufCodecs.INT,
			PetPurpleDogPacket::dogEntityId,
			PetPurpleDogPacket::new
		);

	@Override
	public Type<PetPurpleDogPacket> type() {
		return TYPE;
	}

	public static void handleData(final PetPurpleDogPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player) {
					PurpleStoryModule.startPetting(player, message.purpleEntityId, message.dogEntityId);
				}
			});
		}
	}

}
