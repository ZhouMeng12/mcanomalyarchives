package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.client.FortunePulseClient;
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

@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public record FortunePulsePacket(int severity) implements CustomPacketPayload {
	public static final Type<FortunePulsePacket> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "fortune_pulse"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FortunePulsePacket> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, FortunePulsePacket::severity, FortunePulsePacket::new);
	@Override public Type<FortunePulsePacket> type() { return TYPE; }
	public static void handle(FortunePulsePacket packet, IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND)
			context.enqueueWork(() -> FortunePulseClient.play(packet.severity()));
	}
	@SubscribeEvent
	public static void register(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, FortunePulsePacket::handle);
	}
}
