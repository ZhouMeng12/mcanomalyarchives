package net.mcreator.mcanomalyarchives.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.event.PurplePhaseTransitionHandler;

@EventBusSubscriber
public record AdvanceToPhase2Packet(int purpleEntityId) implements CustomPacketPayload {
	public static final Type<AdvanceToPhase2Packet> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "advance_to_phase2"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AdvanceToPhase2Packet> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			AdvanceToPhase2Packet::purpleEntityId,
			AdvanceToPhase2Packet::new
		);

	@Override
	public Type<AdvanceToPhase2Packet> type() {
		return TYPE;
	}

	public static void handleData(final AdvanceToPhase2Packet message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
					PurplePhaseTransitionHandler.startTransitionToPhase2(player, message.purpleEntityId);
				}
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, AdvanceToPhase2Packet::handleData);
	}
}