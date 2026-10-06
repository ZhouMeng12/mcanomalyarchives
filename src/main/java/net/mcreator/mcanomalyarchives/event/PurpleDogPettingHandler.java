package net.mcreator.mcanomalyarchives.event;


import net.minecraft.server.level.ServerPlayer;

import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStoryModule;

public class PurpleDogPettingHandler {

	public static void init() {
		// 兼容旧初始化入口；会话模块自动订阅事件。
	}

	public static void startPetting(ServerPlayer player, int purpleEntityId, int dogEntityId) {
		PurpleStoryModule.startPetting(player, purpleEntityId, dogEntityId);
	}
}
