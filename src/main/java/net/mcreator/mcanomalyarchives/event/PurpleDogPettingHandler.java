package net.mcreator.mcanomalyarchives.event;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.server.level.ServerPlayer;

import net.mcreator.mcanomalyarchives.network.StartPettingClientPacket;

public class PurpleDogPettingHandler {

	public static void init() {
		// 服务端不再需要tick处理，改为客户端驱动
	}

	public static void startPetting(ServerPlayer player, int purpleEntityId, int dogEntityId) {
		// 发送客户端包，由客户端执行抚摸动画序列
		PacketDistributor.sendToPlayer(player,
			new StartPettingClientPacket(purpleEntityId, dogEntityId,
				player.getX(), player.getY(), player.getZ()));
	}
}
