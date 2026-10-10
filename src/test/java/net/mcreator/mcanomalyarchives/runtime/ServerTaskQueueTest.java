package net.mcreator.mcanomalyarchives.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ServerTaskQueueTest {
	private final List<RuntimeException> failures = new ArrayList<>();
	private final ServerTaskQueue queue = new ServerTaskQueue(failures::add);

	@Test
	void preservesDelayFromFirstPostTickAndRunsOnce() {
		AtomicInteger calls = new AtomicInteger();
		queue.schedule(5, calls::incrementAndGet);
		queue.tick(100);
		queue.tick(104);
		assertEquals(0, calls.get());
		queue.tick(105);
		queue.tick(106);
		assertEquals(1, calls.get());
	}

	@Test
	void preservesSubmissionOrderForEqualDeadlines() {
		List<Integer> calls = new ArrayList<>();
		queue.schedule(1, () -> calls.add(1));
		queue.schedule(1, () -> calls.add(2));
		queue.tick(0);
		queue.tick(1);
		assertEquals(List.of(1, 2), calls);
	}

	@Test
	void callbacksCanScheduleButNewWorkWaitsForNextTick() {
		List<Integer> calls = new ArrayList<>();
		queue.schedule(0, () -> {
			calls.add(1);
			queue.schedule(0, () -> calls.add(2));
		});
		queue.tick(10);
		assertEquals(List.of(1), calls);
		queue.tick(11);
		assertEquals(List.of(1, 2), calls);
	}

	@Test
	void canCancelBeforeOrAfterTransferToScheduledQueue() {
		AtomicInteger calls = new AtomicInteger();
		ServerTaskQueue.TaskHandle incoming = queue.schedule(1, calls::incrementAndGet);
		ServerTaskQueue.TaskHandle scheduled = queue.schedule(1, calls::incrementAndGet);
		assertTrue(incoming.cancel());
		assertFalse(incoming.cancel());
		queue.tick(10);
		assertTrue(scheduled.cancel());
		queue.tick(11);
		assertEquals(0, calls.get());
	}

	@Test
	void logoutCancelsBothPendingAndScheduledOwnedWorkOnly() {
		UUID owner = UUID.randomUUID();
		List<Integer> calls = new ArrayList<>();
		queue.schedule(owner, 2, () -> calls.add(1));
		queue.schedule(UUID.randomUUID(), 2, () -> calls.add(2));
		queue.schedule(2, () -> calls.add(3));
		queue.tick(10);
		queue.schedule(owner, 0, () -> calls.add(4));
		queue.cancelOwner(owner);
		queue.tick(12);
		assertEquals(List.of(2, 3), calls);
	}

	@Test
	void stopReleasesAllWorkAndNewWorldStartsWithEmptyQueue() {
		AtomicInteger oldCalls = new AtomicInteger();
		ServerTaskQueue.TaskHandle scheduled = queue.schedule(1, oldCalls::incrementAndGet);
		queue.tick(100);
		ServerTaskQueue.TaskHandle incoming = queue.schedule(0, oldCalls::incrementAndGet);
		queue.close();
		queue.close();
		assertFalse(scheduled.cancel());
		assertFalse(incoming.cancel());
		assertFalse(queue.schedule(0, oldCalls::incrementAndGet).cancel());
		queue.tick(101);
		ServerTaskQueue nextWorld = new ServerTaskQueue(failures::add);
		nextWorld.tick(101);
		assertEquals(0, oldCalls.get());
		AtomicInteger newCalls = new AtomicInteger();
		nextWorld.schedule(0, newCalls::incrementAndGet);
		nextWorld.tick(102);
		assertEquals(1, newCalls.get());
	}

	@Test
	void aFailedTaskIsReportedWithoutDroppingOtherTasks() {
		AtomicInteger calls = new AtomicInteger();
		queue.schedule(0, () -> { throw new IllegalStateException("test"); });
		queue.schedule(0, calls::incrementAndGet);
		queue.tick(0);
		assertEquals(1, failures.size());
		assertEquals("test", failures.getFirst().getMessage());
		assertEquals(1, calls.get());
	}

	@Test
	void callbackCanStopQueueWithoutExecutingRemainingTasks() {
		AtomicInteger calls = new AtomicInteger();
		queue.schedule(0, queue::close);
		queue.schedule(0, calls::incrementAndGet);
		queue.tick(0);
		assertEquals(0, calls.get());
	}

	@Test
	void validatesArgumentsAndCompletedHandlesCannotCancel() {
		assertThrows(IllegalArgumentException.class, () -> queue.schedule(-1, () -> {}));
		assertThrows(NullPointerException.class, () -> queue.schedule(0, null));
		ServerTaskQueue.TaskHandle task = queue.schedule(0, () -> {});
		queue.tick(0);
		assertFalse(task.cancel());
	}

	@Test
	void deadlineUsesLongArithmeticNearIntegerTickOverflow() {
		AtomicInteger calls = new AtomicInteger();
		queue.schedule(20, calls::incrementAndGet);
		queue.tick(Integer.MAX_VALUE - 10L);
		queue.tick(Integer.MAX_VALUE + 10L);
		assertEquals(1, calls.get());
	}
}
