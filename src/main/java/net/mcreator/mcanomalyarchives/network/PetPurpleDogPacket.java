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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.mcreator.mcanomalyarchives.event.PurpleDogPettingHandler;

@EventBusSubscriber
public record PetPurpleDogPacket(int purpleEntityId, int dogEntityId) implements CustomPacketPayload {
	public static final Type<PetPurpleDogPacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "pet_purple_dog"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PetPurpleDogPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			PetPurpleDogPacket::purpleEntityId,
			ByteBufCodecs.INT,
			PetPurpleDogPacket::dogEntityId,
			PetPurpleDogPacket::new
		);

	@Override
	public Type<PetPurpleDogPacket> type() {
		return TYPE;
	}

	public static void handleData(final PetPurpleDogPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
					// 安全校验：验证 purpleEntityId 对应的实体归属于该玩家
					Entity purpleEntity = serverLevel.getEntity(message.purpleEntityId);
					if (!isOwnedByPlayer(purpleEntity, player)) {
						return;
					}
					Entity dogEntity = serverLevel.getEntity(message.dogEntityId);
					if (!isOwnedByPlayer(dogEntity, player)) {
						return;
					}
					PurpleDogPettingHandler.startPetting(player, message.purpleEntityId, message.dogEntityId);
				}
			});
		}
	}

	private static boolean isOwnedByPlayer(Entity entity, ServerPlayer player) {
		if (entity instanceof PurpleMonsterEntity purple) {
			return player.getUUID().equals(purple.getPerformTargetUUID());
		}
		if (entity instanceof PurpleDogEntity dog) {
			return player.getUUID().equals(dog.getPerformTargetUUID());
		}
		return false;
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, PetPurpleDogPacket::handleData);
	}
}