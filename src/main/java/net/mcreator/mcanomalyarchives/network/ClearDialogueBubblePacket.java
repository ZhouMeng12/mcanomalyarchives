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
import net.mcreator.mcanomalyarchives.client.dialogue.DialogueClientState;

/** 服务端 → 客户端：清除实体头顶的对话气泡 */
@EventBusSubscriber
public record ClearDialogueBubblePacket(int entityId) implements CustomPacketPayload {

	public static final Type<ClearDialogueBubblePacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "clear_dialogue_bubble"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClearDialogueBubblePacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, ClearDialogueBubblePacket::entityId,
			ClearDialogueBubblePacket::new);

	@Override
	public Type<ClearDialogueBubblePacket> type() {
		return TYPE;
	}

	public static void handleData(final ClearDialogueBubblePacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> DialogueClientState.clearBubble(message.entityId()));
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, ClearDialogueBubblePacket::handleData);
	}
}
