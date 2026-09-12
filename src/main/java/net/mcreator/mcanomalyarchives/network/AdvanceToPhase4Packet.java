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
public record AdvanceToPhase4Packet(int purpleEntityId) implements CustomPacketPayload {
	public static final Type<AdvanceToPhase4Packet> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "advance_to_phase4"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AdvanceToPhase4Packet> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			AdvanceToPhase4Packet::purpleEntityId,
			AdvanceToPhase4Packet::new
		);

	@Override
	public Type<AdvanceToPhase4Packet> type() {
		return TYPE;
	}

	public static void handleData(final AdvanceToPhase4Packet message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
					PurplePhaseTransitionHandler.startTransition(player, message.purpleEntityId);
				}
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, AdvanceToPhase4Packet::handleData);
	}
}
