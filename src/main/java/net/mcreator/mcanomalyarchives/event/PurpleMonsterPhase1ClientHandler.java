package net.mcreator.mcanomalyarchives.event;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import net.neoforged.neoforge.network.PacketDistributor;

import net.mcreator.mcanomalyarchives.McanomalyarchivesModPlayerAnimationAPI;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.network.TransitionToPhase2Packet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(value = Dist.CLIENT)
public class PurpleMonsterPhase1ClientHandler {
	private static final Map<UUID, Phase1Transition> TRANSITIONS = new ConcurrentHashMap<>();
	private static final int ANIM_TICKS = 10; // model.hand 动画 0.5s = 10 ticks
	private static final int DELAY_TICKS = 20; // 纹理切换后延迟 1s 再开 GUI

	@SubscribeEvent
	public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		// 清理所有按玩家缓存的客户端状态，避免跨世界/跨登录后内存泄漏
		TRANSITIONS.clear();
		McanomalyarchivesModPlayerAnimationAPI.active_animations.clear();
	}

	public static void startTransition(UUID playerUUID, int purpleEntityId) {
		TRANSITIONS.put(playerUUID, new Phase1Transition(purpleEntityId));
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;

		Phase1Transition transition = TRANSITIONS.get(player.getUUID());
		if (transition == null) return;

		if (mc.level == null) {
			TRANSITIONS.remove(player.getUUID());
			return;
		}

		Entity entity = mc.level.getEntity(transition.purpleEntityId);
		if (entity instanceof PurpleMonsterEntity monster) {
			transition.tick++;

			switch (transition.phase) {
				case Phase1Transition.Phase.PLAY_ANIM:
					// 第一帧触发动画
					if (transition.tick == 1) {
						monster.triggerModelHand();
					}
					if (transition.tick >= ANIM_TICKS) {
						transition.phase = Phase1Transition.Phase.SWITCH_TEXTURE;
						transition.tick = 0;
					}
					break;

				case Phase1Transition.Phase.SWITCH_TEXTURE:
					monster.setTextureVariant(1); // purple
					transition.phase = Phase1Transition.Phase.WAIT_DELAY;
					transition.tick = 0;
					break;

				case Phase1Transition.Phase.WAIT_DELAY:
					if (transition.tick >= DELAY_TICKS) {
						transition.phase = Phase1Transition.Phase.OPEN_PHASE2;
						transition.tick = 0;
					}
					break;

				case Phase1Transition.Phase.OPEN_PHASE2:
					PacketDistributor.sendToServer(
						new TransitionToPhase2Packet(transition.purpleEntityId));
					TRANSITIONS.remove(player.getUUID());
					break;
			}
		} else {
			// 实体已经不存在，直接清理
			TRANSITIONS.remove(player.getUUID());
		}
	}

	private static class Phase1Transition {
		final int purpleEntityId;
		int phase = Phase.PLAY_ANIM;
		int tick = 0;

		Phase1Transition(int purpleEntityId) {
			this.purpleEntityId = purpleEntityId;
		}

		static class Phase {
			static final int PLAY_ANIM = 0;
			static final int SWITCH_TEXTURE = 1;
			static final int WAIT_DELAY = 2;
			static final int OPEN_PHASE2 = 3;
		}
	}
}
