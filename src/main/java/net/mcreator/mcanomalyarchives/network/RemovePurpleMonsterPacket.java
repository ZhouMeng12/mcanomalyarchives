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

public record RemovePurpleMonsterPacket(int entityId, boolean kill, int purpleDogEntityId, boolean killPlayer) implements CustomPacketPayload {
	public static final Type<RemovePurpleMonsterPacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "remove_purple_monster"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RemovePurpleMonsterPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			RemovePurpleMonsterPacket::entityId,
			ByteBufCodecs.BOOL,
			RemovePurpleMonsterPacket::kill,
			ByteBufCodecs.INT,
			RemovePurpleMonsterPacket::purpleDogEntityId,
			ByteBufCodecs.BOOL,
			RemovePurpleMonsterPacket::killPlayer,
			RemovePurpleMonsterPacket::new
		);

	@Override
	public Type<RemovePurpleMonsterPacket> type() {
		return TYPE;
	}

	public static void handleData(final RemovePurpleMonsterPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player) {
					PurpleStoryModule.finish(player, message.entityId, message.purpleDogEntityId,
							message.kill, message.killPlayer);
				}
			});
		}
	}

}
