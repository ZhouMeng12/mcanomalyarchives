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

/** 服务端 → 客户端：在实体头顶显示一条对话气泡 */
@EventBusSubscriber
public record DialogueBubblePacket(int entityId, String speakerName, String text, String imagePath, int durationTicks, int color)
		implements CustomPacketPayload {

	public static final Type<DialogueBubblePacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "dialogue_bubble"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueBubblePacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, DialogueBubblePacket::entityId,
			ByteBufCodecs.STRING_UTF8, DialogueBubblePacket::speakerName,
			ByteBufCodecs.STRING_UTF8, DialogueBubblePacket::text,
			ByteBufCodecs.STRING_UTF8, DialogueBubblePacket::imagePath,
			ByteBufCodecs.INT, DialogueBubblePacket::durationTicks,
			ByteBufCodecs.INT, DialogueBubblePacket::color,
			DialogueBubblePacket::new);

	@Override
	public Type<DialogueBubblePacket> type() {
		return TYPE;
	}

	public static void handleData(final DialogueBubblePacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> DialogueClientState.showBubble(message));
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, DialogueBubblePacket::handleData);
	}
}
