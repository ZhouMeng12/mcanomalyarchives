package net.mcreator.mcanomalyarchives.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.screen.PurpleMonsterScreen;
import net.mcreator.mcanomalyarchives.event.Phase5CameraHandler;
import net.mcreator.mcanomalyarchives.event.PurpleTransitionClientHandler;

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

	public static void handleData(final OpenPurpleGuiPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> {
				Minecraft mc = Minecraft.getInstance();
				if (message.phase == 5 && mc.player != null) {
					// Phase 5: 先开始视角过渡，延迟显示UI
					Phase5CameraHandler.startLockWithDelayedScreen(
						mc.player.getUUID(), message.entityId(), message.phase(), message.purpleDogEntityId());
				} else {
					// 其他阶段：直接显示UI
					mc.setScreen(new PurpleMonsterScreen(message.entityId(), message.phase(), message.purpleDogEntityId()));
					if (mc.player != null) {
						PurpleTransitionClientHandler.unlockPlayer(mc.player.getUUID());
					}
				}
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, OpenPurpleGuiPacket::handleData);
	}
}
