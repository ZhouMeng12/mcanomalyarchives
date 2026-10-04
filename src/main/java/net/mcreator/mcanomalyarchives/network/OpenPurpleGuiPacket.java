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
import net.mcreator.mcanomalyarchives.client.screen.PurpleGuiClientHandler;

@EventBusSubscriber
public record OpenPurpleGuiPacket(int entityId, int phase, int purpleDogEntityId) implements CustomPacketPayload {
	public static final Type<OpenPurpleGuiPacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "open_purple_gui"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenPurpleGuiPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			OpenPurpleGuiPacket::entityId,
			ByteBufCodecs.INT,
			OpenPurpleGuiPacket::phase,
			ByteBufCodecs.INT,
			OpenPurpleGuiPacket::purpleDogEntityId,
			OpenPurpleGuiPacket::new
		);

	@Override
	public Type<OpenPurpleGuiPacket> type() {
		return TYPE;
	}

	/**
	 * 客户端落地逻辑一律放 {@link PurpleGuiClientHandler}，这里只留一句静态调用。
	 *
	 * 【别把客户端代码搬回来】本类带 @EventBusSubscriber，专用服务器也会扫描并加载它。
	 * 方法体里只要出现 Minecraft / new PurpleMonsterScreen(...)，字节码校验就会去解析
	 * PurpleMonsterScreen 的父类 Screen，RuntimeDistCleaner 立刻判定 invalid dist
	 * DEDICATED_SERVER，整个模组加载失败——运行时的 flow() 判断救不了（2026-10-05 实测）。
	 */
	public static void handleData(final OpenPurpleGuiPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> PurpleGuiClientHandler.open(
					message.entityId(), message.phase(), message.purpleDogEntityId()));
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, OpenPurpleGuiPacket::handleData);
	}
}