package net.mcreator.mcanomalyarchives.sanity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * 一个服务器会话内的理智同步策略。只保存 UUID，发送时读取权威值。
 * 由服务器线程使用；同批多次变化合并，立即同步会淘汰此前的待发记录。
 */
public final class SanitySyncQueue {
	public static final int INTERVAL_TICKS = 5;
	private final Set<UUID> dirty = new LinkedHashSet<>();

	public void markDirty(UUID player) {
		dirty.add(player);
	}

	public void flush(long tick, Predicate<UUID> online, ToIntFunction<UUID> currentValue,
			BiConsumer<UUID, Integer> send) {
		if (tick % INTERVAL_TICKS != 0) {
			return;
		}
		Set<UUID> batch = new LinkedHashSet<>(dirty);
		dirty.clear();
		for (UUID player : batch) {
			if (online.test(player)) {
				send.accept(player, currentValue.applyAsInt(player));
			}
		}
	}

	public void syncNow(UUID player, ToIntFunction<UUID> currentValue, BiConsumer<UUID, Integer> send) {
		dirty.remove(player);
		send.accept(player, currentValue.applyAsInt(player));
	}

	public void forget(UUID player) {
		dirty.remove(player);
	}

	public void clear() {
		dirty.clear();
	}
}
