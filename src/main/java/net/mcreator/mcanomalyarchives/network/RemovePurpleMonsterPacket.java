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
import net.minecraft.world.entity.LivingEntity;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;

@EventBusSubscriber
public record RemovePurpleMonsterPacket(int entityId, boolean kill, int purpleDogEntityId, boolean killPlayer) implements CustomPacketPayload {
	public static final Type<RemovePurpleMonsterPacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "remove_purple_monster"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RemovePurpleMonsterPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			RemovePurpleMonsterPacket::entityId,
			ByteBufCodecs.BOOL,
			RemovePurpleMonsterPacket::kill,
			ByteBufCodecs.INT,
			RemovePurpleMonsterPacket::purpleDogEntityId,
			ByteBufCodecs.BOOL,
			RemovePurpleMonsterPacket::killPlayer,
			RemovePurpleMonsterPacket::new
		);

	@Override
	public Type<RemovePurpleMonsterPacket> type() {
		return TYPE;
	}

	public static void handleData(final RemovePurpleMonsterPacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
					Entity entity = serverLevel.getEntity(message.entityId);
					// 安全校验：验证实体存在且归属于该玩家
				if (!isOwnedByPlayer(entity, player)) {
					return;
				}
				// Phase 5 结束时标记同化完成（modelVariant==1 是 Phase 5 标志）
				if (entity instanceof PurpleMonsterEntity pm && pm.getModelVariant() == 1) {
					player.setData(McanomalyarchivesModAttachments.PURPLE_ASSIMILATION_COMPLETED, true);
				}
				// 服务端根据客户端语义决定是否杀死玩家（killPlayer=true 对应坏结局，false 对应好结局/中途退出）
				if (message.killPlayer() && entity instanceof LivingEntity living) {
					player.hurt(serverLevel.damageSources().mobAttack(living), Float.MAX_VALUE);
				}
					if (message.kill) {
						entity.kill();
					} else {
						entity.remove(Entity.RemovalReason.DISCARDED);
					}
					if (message.purpleDogEntityId != 0) {
						Entity dog = serverLevel.getEntity(message.purpleDogEntityId);
						if (isOwnedByPlayer(dog, player)) {
							dog.remove(Entity.RemovalReason.DISCARDED);
						}
					}
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
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, RemovePurpleMonsterPacket::handleData);
	}
}
