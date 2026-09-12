package net.mcreator.mcanomalyarchives.event;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;

import net.mcreator.mcanomalyarchives.network.PettingCompletePacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(value = Dist.CLIENT)
public class PurpleDogPettingClientHandler {
	private static final Map<UUID, PettingState> PETTING_STATES = new ConcurrentHashMap<>();
	private static final float WALK_SPEED = 0.1f;
	private static final int ANIMATION_TICKS = 16; // 0.8s @ 20tps

	public static void startPetting(int purpleEntityId, int dogEntityId, double startX, double startY, double startZ) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		PETTING_STATES.put(mc.player.getUUID(),
			new PettingState(purpleEntityId, dogEntityId, startX, startY, startZ));
	}

	public static boolean isPetting(UUID playerUUID) {
		return PETTING_STATES.containsKey(playerUUID);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;

		PettingState state = PETTING_STATES.get(player.getUUID());
		if (state == null) return;

		if (mc.level == null) {
			PETTING_STATES.remove(player.getUUID());
			return;
		}

		Entity dogEntity = mc.level.getEntity(state.dogEntityId);
		if (dogEntity == null) {
			PacketDistributor.sendToServer(
				new PettingCompletePacket(state.purpleEntityId, state.dogEntityId));
			PETTING_STATES.remove(player.getUUID());
			return;
		}

		// 锁定输入
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(false);
		mc.options.keyLeft.setDown(false);
		mc.options.keyRight.setDown(false);
		player.xxa = 0;
		player.zza = 0;
		player.setSprinting(false);

		// 锁定视角朝向紫狗
		double dx = dogEntity.getX() - player.getX();
		double dz = dogEntity.getZ() - player.getZ();
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
		player.setYRot(yaw);
		player.yHeadRot = yaw;

		state.tick++;

		switch (state.phase) {
			case 1:
				// Phase 1: 等待
				if (state.tick >= 15) {
					state.phase = 2;
					state.tick = 0;
				}
				break;

			case 2:
				// Phase 2: 走向紫狗
				if (horizontalDist < 1.5) {
					// 不再在客户端丢弃物品，由服务端在收到 PettingCompletePacket 后执行
					state.phase = 3;
					state.tick = 0;
				} else {
					double moveX = (dx / horizontalDist) * WALK_SPEED;
					double moveZ = (dz / horizontalDist) * WALK_SPEED;
					player.move(MoverType.SELF, new Vec3(moveX, 0, moveZ));
				}
				break;

			case 3:
				// Phase 3: 慢速挥臂两次（增大间隔使动作更慢）
				if (state.tick == 5) {
					player.swing(InteractionHand.MAIN_HAND);
				}
				if (state.tick == 25) { // 增大挥臂间隔到20tick（1秒）
					player.swing(InteractionHand.MAIN_HAND);
				}
				if (state.tick >= 45) { // 延长阶段时间到45tick（2.25秒）
					state.phase = 4;
					state.tick = 0;
				}
				break;

			case 4:
				// Phase 4: 退回原始位置
				double backDx = state.startX - player.getX();
				double backDz = state.startZ - player.getZ();
				double backDist = Math.sqrt(backDx * backDx + backDz * backDz);
				if (backDist < 0.3) {
					state.phase = 5;
					state.tick = 0;
				} else {
					double moveX = (backDx / backDist) * WALK_SPEED;
					double moveZ = (backDz / backDist) * WALK_SPEED;
					player.move(MoverType.SELF, new Vec3(moveX, 0, moveZ));
				}
				break;

			case 5:
				// Phase 5: 通知服务端清理实体
				PacketDistributor.sendToServer(
					new PettingCompletePacket(state.purpleEntityId, state.dogEntityId));
				PETTING_STATES.remove(player.getUUID());
				break;
		}
	}

	private static class PettingState {
		final int purpleEntityId;
		final int dogEntityId;
		final double startX;
		final double startY;
		final double startZ;
		int phase = 1;
		int tick = 0;

		PettingState(int purpleEntityId, int dogEntityId, double startX, double startY, double startZ) {
			this.purpleEntityId = purpleEntityId;
			this.dogEntityId = dogEntityId;
			this.startX = startX;
			this.startY = startY;
			this.startZ = startZ;
		}
	}
}
