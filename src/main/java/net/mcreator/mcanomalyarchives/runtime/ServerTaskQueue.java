package net.mcreator.mcanomalyarchives.runtime;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 一个服务器会话的延迟任务。仅由服务器线程访问，不持有全局服务器或世界。
 * 先接收、再于 Post tick 计算到期时间，保留旧 queueServerWork 的计时方式。
 */
public final class ServerTaskQueue {
	public interface TaskHandle {
		/** 取消尚未执行的任务，同时释放其捕获的实体/世界引用。 */
		boolean cancel();
	}

	private static final TaskHandle REJECTED = () -> false;
	private final Queue<Task> incoming = new ArrayDeque<>();
	private final PriorityQueue<Task> scheduled = new PriorityQueue<>(
			Comparator.comparingLong((Task task) -> task.dueTick).thenComparingLong(task -> task.sequence));
	private final Consumer<RuntimeException> onFailure;
	private long sequence;
	private boolean closed;

	public ServerTaskQueue(Consumer<RuntimeException> onFailure) {
		this.onFailure = Objects.requireNonNull(onFailure);
	}

	public static TaskHandle rejected() {
		return REJECTED;
	}

	public TaskHandle schedule(int delay, Runnable action) {
		return schedule(null, delay, action);
	}

	public TaskHandle schedule(UUID owner, int delay, Runnable action) {
		if (delay < 0) {
			throw new IllegalArgumentException("Task delay must be non-negative");
		}
		Objects.requireNonNull(action);
		if (closed) {
			return REJECTED;
		}
		Task task = new Task(owner, delay, sequence++, action);
		incoming.add(task);
		return task;
	}

	public void tick(long currentTick) {
		if (closed) {
			return;
		}
		Task task;
		while ((task = incoming.poll()) != null) {
			if (task.action != null) {
				task.dueTick = currentTick + task.delay;
				scheduled.add(task);
			}
		}
		// 回调中新排的任务留到下一次 tick，零延迟也不能形成同 tick 的递归死循环。
		while (!closed && !scheduled.isEmpty() && currentTick >= scheduled.peek().dueTick) {
			Task ready = scheduled.poll();
			Runnable action = ready.action;
			ready.action = null;
			if (action != null) {
				try {
					action.run();
				} catch (RuntimeException failure) {
					onFailure.accept(failure);
				}
			}
		}
	}

	public void cancelOwner(UUID owner) {
		Objects.requireNonNull(owner);
		incoming.removeIf(task -> cancelIfOwned(task, owner));
		scheduled.removeIf(task -> cancelIfOwned(task, owner));
	}

	private static boolean cancelIfOwned(Task task, UUID owner) {
		if (!owner.equals(task.owner)) {
			return false;
		}
		task.cancel();
		return true;
	}

	public void close() {
		closed = true;
		incoming.forEach(Task::cancel);
		scheduled.forEach(Task::cancel);
		incoming.clear();
		scheduled.clear();
	}

	private static final class Task implements TaskHandle {
		private final UUID owner;
		private final int delay;
		private final long sequence;
		private long dueTick;
		private Runnable action;

		private Task(UUID owner, int delay, long sequence, Runnable action) {
			this.owner = owner;
			this.delay = delay;
			this.sequence = sequence;
			this.action = action;
		}

		@Override
		public boolean cancel() {
			if (action == null) {
				return false;
			}
			action = null;
			return true;
		}
	}
}
