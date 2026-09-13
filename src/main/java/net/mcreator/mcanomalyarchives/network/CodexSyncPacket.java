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

/**
 * 服务端 → 客户端：见闻录解锁状态（位掩码）。
 *
 * 解锁状态是服务端权威的（存在玩家 persistentData 里），客户端要显示就得同步过来；
 * {@code openScreen} 为真时顺带把界面打开的请求一起带过去（省一个包）。
 */
@EventBusSubscriber
public record CodexSyncPacket(int mask, boolean openScreen) implements CustomPacketPayload {

	public static final Type<CodexSyncPacket> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "codex_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CodexSyncPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CodexSyncPacket::mask,
			ByteBufCodecs.BOOL, CodexSyncPacket::openScreen,
			CodexSyncPacket::new);

	@Override
	public Type<CodexSyncPacket> type() {
		return TYPE;
	}

	public static void handleData(final CodexSyncPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> net.mcreator.mcanomalyarchives.client.codex.CodexClientHandler
					.onSync(message.mask(), message.openScreen()));
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, CodexSyncPacket::handleData);
	}
}
