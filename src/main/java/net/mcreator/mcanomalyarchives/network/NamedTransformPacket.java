package net.mcreator.mcanomalyarchives.network;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagTransform;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState;
import net.mcreator.mcanomalyarchives.client.NamedTransformClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：告诉客户端"这个生物正在转化中，从第几刻开始、要多久、要变成什么"。
 *
 * 【为什么需要它】体型缩放与材质渐变都是**渲染**的事，而转化进度只存在服务端。
 * 只在命名那一刻发一次，以及玩家开始追踪该实体时补发一次；
 * 客户端自己按游戏刻插值 —— 不需要每帧发包。
 */
@EventBusSubscriber
public record NamedTransformPacket(int entityId, long startTick, int duration, String targetId)
		implements CustomPacketPayload {

	public static final Type<NamedTransformPacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "named_transform"));

	public static final StreamCodec<RegistryFriendlyByteBuf, NamedTransformPacket> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, NamedTransformPacket::entityId,
					ByteBufCodecs.VAR_LONG, NamedTransformPacket::startTick,
					ByteBufCodecs.VAR_INT, NamedTransformPacket::duration,
					ByteBufCodecs.STRING_UTF8, NamedTransformPacket::targetId,
					NamedTransformPacket::new);

	@Override
	public Type<NamedTransformPacket> type() {
		return TYPE;
	}

	/** 刚命名完：发给正在追踪它的玩家（含自己）。 */
	public static void sendToWatchers(Entity entity) {
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packetFor(entity));
	}

	/** 玩家开始追踪这个实体时补发（否则中途进来的玩家看不到转化过程）。 */
	public static void sendTo(ServerPlayer player, Entity entity) {
		if (NamedState.isNamed(entity)) {
			PacketDistributor.sendToPlayer(player, packetFor(entity));
		}
	}

	private static NamedTransformPacket packetFor(Entity entity) {
		return new NamedTransformPacket(entity.getId(), NameTagTransform.startTick(entity),
				NameTagTransform.duration(entity), NamedState.targetTokenOf(entity));
	}

	public static void handleData(final NamedTransformPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.CLIENTBOUND) {
			context.enqueueWork(() -> NamedTransformClient.accept(message.entityId(), message.startTick(),
					message.duration(), message.targetId()));
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, NamedTransformPacket::handleData);
	}
}
