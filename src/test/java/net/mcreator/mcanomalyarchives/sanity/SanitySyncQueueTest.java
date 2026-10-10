package net.mcreator.mcanomalyarchives.sanity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SanitySyncQueueTest {
	private final SanitySyncQueue queue = new SanitySyncQueue();
	private final Map<UUID, Integer> current = new HashMap<>();
	private final List<String> sent = new ArrayList<>();
	private final UUID player = UUID.randomUUID();

	private void flush(long tick) {
		queue.flush(tick, current::containsKey, current::get, (id, value) -> sent.add(id + ":" + value));
	}

	@Test
	void coalescesChangesAndOnlyFlushesEveryFiveTicks() {
		current.put(player, 80);
		queue.markDirty(player);
		queue.markDirty(player);
		flush(4);
		assertTrue(sent.isEmpty());
		current.put(player, 20); // 不保存数值快照：真正发送时读最新服务器值。
		flush(5);
		assertEquals(List.of(player + ":20"), sent);
		flush(10);
		assertEquals(1, sent.size());
	}

	@Test
	void immediateJoinOrRespawnSyncRemovesPreviouslyDirtyValue() {
		current.put(player, 10);
		queue.markDirty(player);
		current.put(player, 100);
		queue.syncNow(player, current::get, (id, value) -> sent.add(id + ":" + value));
		flush(5);
		assertEquals(List.of(player + ":100"), sent);
	}

	@Test
	void logoutDiscardsWorkEvenIfSameUuidReconnectsBeforeFlush() {
		current.put(player, 50);
		queue.markDirty(player);
		queue.forget(player);
		current.put(player, 75);
		flush(5);
		assertTrue(sent.isEmpty());
		queue.syncNow(player, current::get, (id, value) -> sent.add(id + ":" + value));
		assertEquals(List.of(player + ":75"), sent);
	}

	@Test
	void offlinePlayersAreDiscardedWithoutReadingOrSendingTheirValue() {
		queue.markDirty(player);
		flush(5);
		current.put(player, 90);
		flush(10);
		assertTrue(sent.isEmpty());
	}

	@Test
	void stopAndNewWorldDoNotReuseDirtyPlayers() {
		current.put(player, 10);
		queue.markDirty(player);
		queue.clear();
		flush(5);
		new SanitySyncQueue().flush(5, current::containsKey, current::get,
				(id, value) -> sent.add(id + ":" + value));
		assertTrue(sent.isEmpty());
	}

	@Test
	void newChangesDuringSendRemainPendingForNextBatch() {
		current.put(player, 50);
		queue.markDirty(player);
		queue.flush(5, current::containsKey, current::get, (id, value) -> {
			sent.add(id + ":" + value);
			current.put(id, 25);
			queue.markDirty(id);
		});
		flush(10);
		assertEquals(List.of(player + ":50", player + ":25"), sent);
	}

	@Test
	void eachDirtyPlayerGetsTheirOwnAuthoritativeValue() {
		UUID other = UUID.randomUUID();
		current.put(player, 30);
		current.put(other, 70);
		queue.markDirty(player);
		queue.markDirty(other);
		flush(5);
		assertEquals(List.of(player + ":30", other + ":70"), sent);
	}
}
