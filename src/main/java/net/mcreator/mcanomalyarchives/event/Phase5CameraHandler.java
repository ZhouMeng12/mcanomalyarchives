package net.mcreator.mcanomalyarchives.event;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.mcreator.mcanomalyarchives.client.screen.PurpleMonsterScreen;

@EventBusSubscriber(value = Dist.CLIENT)
public class Phase5CameraHandler {
	private static final Map<UUID, Integer> LOCKED_PLAYERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> START_YAW = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> START_PITCH = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> TRANSITION_TICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingScreenData> PENDING_SCREENS = new ConcurrentHashMap<>();
	private static final int TRANSITION_DURATION = 20; // 20 tick = 1秒过渡
	private static final int DELAY_AFTER_TRANSITION = 10; // 过渡完成后再等10 tick才显示UI

	public record PendingScreenData(int entityId, int phase, int purpleDogEntityId, int totalWaitTicks) {}

	public static void startLock(UUID playerUUID, int purpleEntityId) {
		LOCKED_PLAYERS.put(playerUUID, purpleEntityId);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			START_YAW.put(playerUUID, mc.player.getYRot());
			START_PITCH.put(playerUUID, mc.player.getXRot());
			TRANSITION_TICKS.put(playerUUID, 0);
		}
	}

	public static void startLockWithDelayedScreen(UUID playerUUID, int entityId, int phase, int purpleDogEntityId) {
		startLock(playerUUID, entityId);
		PENDING_SCREENS.put(playerUUID, new PendingScreenData(entityId, phase, purpleDogEntityId, TRANSITION_DURATION + DELAY_AFTER_TRANSITION));
	}

	public static void stopLock(UUID playerUUID) {
		LOCKED_PLAYERS.remove(playerUUID);
		START_YAW.remove(playerUUID);
		START_PITCH.remove(playerUUID);
		TRANSITION_TICKS.remove(playerUUID);
		PENDING_SCREENS.remove(playerUUID);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;

		UUID uuid = player.getUUID();
		Integer entityId = LOCKED_PLAYERS.get(uuid);
		if (entityId == null) return;

		Entity target = mc.level.getEntity(entityId);
		if (target == null) {
			stopLock(uuid);
			return;
		}

		// 锁定移动输入
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(false);
		mc.options.keyLeft.setDown(false);
		mc.options.keyRight.setDown(false);
		player.xxa = 0;
		player.zza = 0;
		player.setSprinting(false);

		// 计算目标视角（往上半格）
		double dx = target.getX() - player.getX();
		double dy = target.getEyeY() + 0.5 - player.getEyeY();
		double dz = target.getZ() - player.getZ();
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
		float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));

		// 平滑过渡动画
		Integer ticks = TRANSITION_TICKS.get(uuid);
		if (ticks == null || ticks >= TRANSITION_DURATION) {
			// 过渡完成，直接设置目标视角
			player.setYRot(targetYaw);
			player.yHeadRot = targetYaw;
			player.setXRot(targetPitch);
		} else {
			float progress = (float) ticks / TRANSITION_DURATION;
			Float startYaw = START_YAW.get(uuid);
			Float startPitch = START_PITCH.get(uuid);
			if (startYaw != null && startPitch != null) {
				float currentYaw = lerp(startYaw, targetYaw, progress);
				float currentPitch = lerp(startPitch, targetPitch, progress);
				player.setYRot(currentYaw);
				player.yHeadRot = currentYaw;
				player.setXRot(currentPitch);
			}
			TRANSITION_TICKS.put(uuid, ticks + 1);
		}

		// 处理延迟显示UI
		PendingScreenData pending = PENDING_SCREENS.get(uuid);
		if (pending != null) {
			int remaining = pending.totalWaitTicks() - 1;
			if (remaining <= 0) {
				PENDING_SCREENS.remove(uuid);
				mc.setScreen(new PurpleMonsterScreen(pending.entityId(), pending.phase(), pending.purpleDogEntityId()));
				PurpleTransitionClientHandler.unlockPlayer(uuid);
			} else {
				PENDING_SCREENS.put(uuid, new PendingScreenData(pending.entityId(), pending.phase(), pending.purpleDogEntityId(), remaining));
			}
		}
	}

	private static float lerp(float start, float end, float progress) {
		return start + (end - start) * progress;
	}
}
