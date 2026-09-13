package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.List;

/**
 * 服务端 → 客户端：对话中弹出一个选择（屏幕中间的按钮）。
 *
 * 选项用 {@code \n} 拼成一个字符串传输（选项文字里不会有换行），省掉一个 List 编解码器。
 * 玩家点完由 {@link DialogueChoiceAnswerPacket} 回传。
 */
@EventBusSubscriber
public record DialogueChoicePacket(int choiceId, String prompt, String optionsJoined) implements CustomPacketPayload {

	public static final Type<DialogueChoicePacket> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "dialogue_choice"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueChoicePacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialogueChoicePacket::choiceId,
			ByteBufCodecs.STRING_UTF8, DialogueChoicePacket::prompt,
			ByteBufCodecs.STRING_UTF8, DialogueChoicePacket::optionsJoined,
			DialogueChoicePacket::new);

	public static DialogueChoicePacket of(int choiceId, String prompt, List<String> options) {
		return new DialogueChoicePacket(choiceId, prompt, String.join("\n", options));
	}

	/** 选项列表 */
	public List<String> options() {
		return Arrays.asList(this.optionsJoined.split("\n"));
	}

	@Override
	public Type<DialogueChoicePacket> type() {
		return TYPE;
	}

	public static void handleData(final DialogueChoicePacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> net.mcreator.mcanomalyarchives.client.dialogue.DialogueChoiceScreen
					.open(message.choiceId(), message.prompt(), message.options()));
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, DialogueChoicePacket::handleData);
	}
}
