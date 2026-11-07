package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.nametag.GarbledInventoryInfection;
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

/** Client reports whether an item GUI is visible; all infection decisions remain server-side. */
@EventBusSubscriber
public record GarbledGuiStatePacket(boolean open) implements CustomPacketPayload {
	public static final Type<GarbledGuiStatePacket> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "garbled_gui_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GarbledGuiStatePacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, GarbledGuiStatePacket::open, GarbledGuiStatePacket::new);

	@Override
	public Type<GarbledGuiStatePacket> type() {
		return TYPE;
	}

	public static void handleData(GarbledGuiStatePacket message, IPayloadContext context) {
		if (context.flow() != PacketFlow.SERVERBOUND) return;
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player) {
				GarbledInventoryInfection.setGuiOpen(player, message.open());
			}
		});
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, GarbledGuiStatePacket::handleData);
	}
}
