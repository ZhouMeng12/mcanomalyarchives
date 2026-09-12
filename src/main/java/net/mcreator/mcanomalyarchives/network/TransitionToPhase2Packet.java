package net.mcreator.mcanomalyarchives.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

@EventBusSubscriber
public record TransitionToPhase2Packet(int purpleEntityId) implements CustomPacketPayload {
	public static final Type<TransitionToPhase2Packet> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "transition_to_phase2"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TransitionToPhase2Packet> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			TransitionToPhase2Packet::purpleEntityId,
			TransitionToPhase2Packet::new
		);

	@Override
	public Type<TransitionToPhase2Packet> type() {
		return TYPE;
	}

	public static void handleData(final TransitionToPhase2Packet message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player) {
					// 归属校验：只有 performTargetUUID 匹配的玩家才能推进到 Phase 2，
					// 防止修改版客户端伪造包任意开启紫怪 GUI
					if (!isOwnedByPlayer(player.serverLevel().getEntity(message.purpleEntityId), player)) {
						return;
					}
					PacketDistributor.sendToPlayer(player,
						new OpenPurpleGuiPacket(message.purpleEntityId, 2, 0));
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
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, TransitionToPhase2Packet::handleData);
	}
}
