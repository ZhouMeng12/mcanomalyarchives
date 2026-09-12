package net.mcreator.mcanomalyarchives.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

@EventBusSubscriber
public record GivePurplehandPacket() implements CustomPacketPayload {
	public static final Type<GivePurplehandPacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "give_purplehand"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GivePurplehandPacket> STREAM_CODEC =
		StreamCodec.of(
			(value, buf) -> {},
			buf -> new GivePurplehandPacket()
		);

	@Override
	public Type<GivePurplehandPacket> type() {
		return TYPE;
	}

	public static void handleData(final GivePurplehandPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player) {
					ItemStack purplehand = new ItemStack(McanomalyarchivesModItems.PURPLEHAND.get());
					if (!player.getInventory().add(purplehand)) {
						player.drop(purplehand, false);
					}
				}
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, GivePurplehandPacket::handleData);
	}
}
