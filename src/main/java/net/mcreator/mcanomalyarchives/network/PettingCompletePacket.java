package net.mcreator.mcanomalyarchives.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber
public record PettingCompletePacket(int purpleEntityId, int dogEntityId) implements CustomPacketPayload {
	public static final Type<PettingCompletePacket> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "petting_complete"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PettingCompletePacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			PettingCompletePacket::purpleEntityId,
			ByteBufCodecs.INT,
			PettingCompletePacket::dogEntityId,
			PettingCompletePacket::new
		);

	@Override
	public Type<PettingCompletePacket> type() {
		return TYPE;
	}

	public static void handleData(final PettingCompletePacket message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
					// 安全校验：验证实体归属于该玩家
					Entity purpleEntity = serverLevel.getEntity(message.purpleEntityId);
					if (!isOwnedByPlayer(purpleEntity, player)) {
						return;
					}
					Entity dogEntity = serverLevel.getEntity(message.dogEntityId);
					if (!isOwnedByPlayer(dogEntity, player)) {
						return;
					}
					// 服务端执行物品丢弃
					ItemStack held = player.getMainHandItem().copy();
					if (!held.isEmpty()) {
						player.getInventory().removeItem(held);
						player.drop(held, false);
					}
					// 设置紫怪模型变体为 Phase 5 模型
					if (purpleEntity instanceof PurpleMonsterEntity purpleMonster) {
						purpleMonster.setModelVariant(1);
					}
					// 发送 Phase 5 GUI 打开指令给客户端
					PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(message.purpleEntityId, 5, message.dogEntityId));
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
		McanomalyarchivesMod.addNetworkMessage(TYPE, STREAM_CODEC, PettingCompletePacket::handleData);
	}
}
