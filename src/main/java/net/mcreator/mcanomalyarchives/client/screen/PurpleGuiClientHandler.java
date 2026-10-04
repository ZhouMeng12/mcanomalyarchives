package net.mcreator.mcanomalyarchives.client.screen;

import net.minecraft.client.Minecraft;

import net.mcreator.mcanomalyarchives.event.Phase5CameraHandler;
import net.mcreator.mcanomalyarchives.event.PurpleTransitionClientHandler;

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
public final class PurpleGuiClientHandler {

	private PurpleGuiClientHandler() {
	}

	/** phase 5 先走视角过渡再延迟开界面，其余阶段直接开界面。 */
	public static void open(int entityId, int phase, int purpleDogEntityId) {
		Minecraft mc = Minecraft.getInstance();
		if (phase == 5 && mc.player != null) {
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
}
