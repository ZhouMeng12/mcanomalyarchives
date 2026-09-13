package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.codex.CodexOriginStory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：玩家在对话选项里点了第几个（{@code index = -1} 表示取消）。
 */
@EventBusSubscriber
public record DialogueChoiceAnswerPacket(int choiceId, int index) implements CustomPacketPayload {

	public static final Type<DialogueChoiceAnswerPacket> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "dialogue_choice_answer"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueChoiceAnswerPacket> STREAM_CODEC = StreamCodec
			.composite(
					ByteBufCodecs.VAR_INT, DialogueChoiceAnswerPacket::choiceId,
					ByteBufCodecs.VAR_INT, DialogueChoiceAnswerPacket::index,
					DialogueChoiceAnswerPacket::new);

	public static DialogueChoiceAnswerPacket answer(int choiceId, int index) {
		return new DialogueChoiceAnswerPacket(choiceId, index);
	}

	@Override
	public Type<DialogueChoiceAnswerPacket> type() {
		return TYPE;
	}

	public static void handleData(final DialogueChoiceAnswerPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND && context.player() instanceof ServerPlayer player) {
			context.enqueueWork(() -> CodexOriginStory.onAnswer(player, message.choiceId(), message.index()));
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, DialogueChoiceAnswerPacket::handleData);
	}
}
