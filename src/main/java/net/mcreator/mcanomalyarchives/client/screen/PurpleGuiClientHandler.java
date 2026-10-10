package net.mcreator.mcanomalyarchives.client.screen;

import net.minecraft.client.Minecraft;

import net.mcreator.mcanomalyarchives.event.Phase5CameraHandler;
import net.mcreator.mcanomalyarchives.event.PurpleTransitionClientHandler;
import net.mcreator.mcanomalyarchives.event.PurpleDogPettingClientHandler;
import net.mcreator.mcanomalyarchives.event.PurpleMonsterPhase1ClientHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * OpenPurpleGuiPacket 的客户端落地。
 *
 * 【为什么单独放一个类】来源包体里一旦出现 new PurpleMonsterScreen(...) / Minecraft.getInstance()，
 * 专用服务器在校验 OpenPurpleGuiPacket 字节码时就会去解析 PurpleMonsterScreen 的父类 Screen，
 * RuntimeDistCleaner 直接判定 invalid dist DEDICATED_SERVER，整个模组加载失败——
 * 不是运行到那一行才炸，是类一被加载就炸，所以 {flow() == CLIENTBOUND} 这种运行时判断挡不住。
 *
 * 挪到这里之后，包体只剩一句对客户端类的静态调用；服务端既不会执行它，
 * 校验时也不需要解析方法体内的引用类型，于是能正常启动。
 */
@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class PurpleGuiClientHandler {

	private PurpleGuiClientHandler() {
	}

	/** phase 5 先走视角过渡再延迟开界面，其余阶段直接开界面。 */
	public static void open(int entityId, int phase, int purpleDogEntityId) {
		Minecraft mc = Minecraft.getInstance();
		if (phase == 5) PurpleDogPettingClientHandler.clear(); // 服务端确认抚摸完成，停止重试请求。
		if (phase == 5 && entityId > 0 && mc.player != null) {
			// Phase 5: 先开始视角过渡，延迟显示UI
			Phase5CameraHandler.startLockWithDelayedScreen(
					mc.player.getUUID(), entityId, phase, purpleDogEntityId);
		} else {
			// 其他阶段：直接显示UI
			mc.setScreen(new PurpleMonsterScreen(entityId, phase, purpleDogEntityId));
			if (mc.player != null) {
				PurpleTransitionClientHandler.unlockPlayer(mc.player.getUUID());
			}
		}
	}

	/** 服务端取消与本地断线共用同一清理入口，不回发“删除实体”或“领奖”请求。 */
	public static void close() {
		PurpleMonsterPhase1ClientHandler.clear();
		PurpleDogPettingClientHandler.clear();
		PurpleTransitionClientHandler.clear();
		Phase5CameraHandler.clear();
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof PurpleMonsterScreen) mc.setScreen(null);
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		close();
	}
}
