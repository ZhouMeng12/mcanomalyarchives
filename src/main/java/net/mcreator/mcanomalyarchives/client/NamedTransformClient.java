package net.mcreator.mcanomalyarchives.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端侧的"谁在转化中"缓存。
 *
 * 服务端只在命名时（以及玩家开始追踪时）发一次
 * {@link net.mcreator.mcanomalyarchives.network.NamedTransformPacket}，
 * 之后进度由客户端按本地游戏刻插值 —— 渲染每帧都要问，走缓存不碰网络。
 *
 * 【工程层归属】client 包（非 MCreator 生成区）。
 */
public final class NamedTransformClient {

	/** 实体 id → {开始刻, 总时长}。 */
	private static final Map<Integer, long[]> ACTIVE = new ConcurrentHashMap<>();
	/** 实体 id → 目标注册名（形如 entity:minecraft:chicken）。 */
	private static final Map<Integer, String> TARGETS = new ConcurrentHashMap<>();

	private NamedTransformClient() {
	}

	public static void accept(int entityId, long startTick, int duration, String targetId) {
		ACTIVE.put(entityId, new long[]{startTick, Math.max(1, duration)});
		if (targetId != null && !targetId.isEmpty()) {
			TARGETS.put(entityId, targetId);
		}
	}

	public static String targetOf(Entity entity) {
		return entity == null ? null : TARGETS.get(entity.getId());
	}

	/** 离开世界/实体没了就清掉，避免这张表越攒越大。 */
	public static void clear() {
		ACTIVE.clear();
		TARGETS.clear();
	}

	public static void forget(int entityId) {
		ACTIVE.remove(entityId);
		TARGETS.remove(entityId);
	}

	/**
	 * 当前进度（0..1）。没有在转化就返回 0。
	 *
	 * 时间基准用客户端自己的游戏刻：包里的 startTick 是服务端的游戏刻，
	 * 双端同一世界时两者一致；即使有偏差，最坏情况也只是进度整体偏移一点点。
	 */
	public static float progressOf(Entity entity) {
		if (entity == null) {
			return 0.0f;
		}
		long[] entry = ACTIVE.get(entity.getId());
		if (entry == null) {
			return 0.0f;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 0.0f;
		}
		long elapsed = mc.level.getGameTime() - entry[0];
		float p = (float) elapsed / (float) entry[1];
		return Math.max(0.0f, Math.min(1.0f, p));
	}

	public static boolean isTransforming(Entity entity) {
		return entity != null && ACTIVE.containsKey(entity.getId());
	}
}
