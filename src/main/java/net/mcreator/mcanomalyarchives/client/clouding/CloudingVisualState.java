package net.mcreator.mcanomalyarchives.client.clouding;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端"云化灰白"标记表：由服务端 CloudingVisualPacket 下发，按实体 UUID 匹配防 id 复用 */
public final class CloudingVisualState {

	private static final Map<Integer, UUID> GRAY = new ConcurrentHashMap<>();

	private CloudingVisualState() {
	}

	public static void set(int entityId, UUID uuid, boolean gray) {
		if (gray) {
			GRAY.put(entityId, uuid);
		} else {
			GRAY.remove(entityId);
		}
	}

	public static void clearAll() {
		GRAY.clear();
	}

	public static boolean isGray(Entity entity) {
		UUID uuid = GRAY.get(entity.getId());
		return uuid != null && uuid.equals(entity.getUUID());
	}
}
