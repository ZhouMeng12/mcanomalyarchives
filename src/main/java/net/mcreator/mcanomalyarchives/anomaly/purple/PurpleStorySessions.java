package net.mcreator.mcanomalyarchives.anomaly.purple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 一个服务器的紫怪会话。只保存身份与状态，不持有玩家/实体/世界对象。
 * 由服务器线程调用；生产与测试使用同一状态转换和一次性结算规则。
 */
public final class PurpleStorySessions {
	public enum Stage { QUESTION, REVENGE, TRANSITION, ANIMAL, PETTING, FINAL, CLOSED }
	public enum TransitionStep { NONE, REMOVE_OLD, SPAWN, OPEN }
	public enum Outcome { REJECTED, CANCELLED, KILL_PLAYER, ASSIMILATED, REWARDED }

	public record Actor(int id, UUID uuid) {
		public Actor {
			if (id <= 0) throw new IllegalArgumentException("Story actor must have a positive entity id");
			Objects.requireNonNull(uuid);
		}
	}

	private final Map<UUID, Session> sessions = new HashMap<>();

	public Session open(UUID player, String dimension, Actor purple, Actor dog, Stage stage) {
		Objects.requireNonNull(player);
		Objects.requireNonNull(dimension);
		Objects.requireNonNull(purple);
		if (stage != Stage.QUESTION && stage != Stage.REVENGE && stage != Stage.ANIMAL) {
			throw new IllegalArgumentException("Cannot open a normal story in this stage");
		}
		if (stage == Stage.ANIMAL && (dog == null || dog.equals(purple) || dog.id() == purple.id()
				|| dog.uuid().equals(purple.uuid()))) {
			throw new IllegalArgumentException("Phase 4 needs two distinct actors");
		}
		if (stage != Stage.ANIMAL && dog != null) {
			throw new IllegalArgumentException("Early phases do not have a dog");
		}
		if (sessions.containsKey(player)) {
			throw new IllegalStateException("Player already has a story session");
		}
		Session session = new Session(player, dimension, purple, dog, stage);
		sessions.put(player, session);
		return session;
	}

	public Session get(UUID player) {
		return sessions.get(player);
	}

	public List<Session> active() {
		return List.copyOf(sessions.values());
	}

	public Session close(UUID player) {
		Session session = sessions.remove(player);
		if (session != null) session.cancel();
		return session;
	}

	public List<Session> closeAll() {
		List<Session> previous = active();
		sessions.clear();
		previous.forEach(Session::cancel);
		return previous;
	}

	public static final class Session {
		/** 原客户端至少等待 15 tick，再挥臂 45 tick；移动所需时间不计入下限。 */
		public static final int MIN_PETTING_TICKS = 60;
		private final UUID player;
		private final String dimension;
		private Actor purple;
		private Actor dog;
		private Stage stage;
		private int transitionTicks;
		private boolean replacementBound;
		private long pettingStarted;

		private Session(UUID player, String dimension, Actor purple, Actor dog, Stage stage) {
			this.player = player;
			this.dimension = dimension;
			this.purple = purple;
			this.dog = dog;
			this.stage = stage;
		}

		public UUID player() { return player; }
		public String dimension() { return dimension; }
		public Actor purple() { return purple; }
		public Actor dog() { return dog; }
		public int dogId() { return dog == null ? 0 : dog.id(); }
		public Stage stage() { return stage; }

		public boolean advanceToPhase2(int purpleId) {
			if (stage != Stage.QUESTION || purpleId != purple.id()) return false;
			stage = Stage.REVENGE;
			return true;
		}

		public boolean beginPhase4(int purpleId) {
			if (stage != Stage.REVENGE || purpleId != purple.id()) return false;
			stage = Stage.TRANSITION;
			transitionTicks = 0;
			replacementBound = false;
			return true;
		}

		public TransitionStep tickTransition() {
			if (stage != Stage.TRANSITION) return TransitionStep.NONE;
			transitionTicks++;
			if (transitionTicks == 5) return TransitionStep.REMOVE_OLD;
			if (transitionTicks == 20) return TransitionStep.SPAWN;
			if (transitionTicks == 60 && replacementBound) {
				stage = Stage.ANIMAL;
				return TransitionStep.OPEN;
			}
			return TransitionStep.NONE;
		}

		public boolean bindReplacement(Actor purple, Actor dog) {
			if (stage != Stage.TRANSITION || transitionTicks != 20 || replacementBound
					|| purple == null || dog == null || purple.id() == dog.id()
					|| purple.uuid().equals(dog.uuid())) return false;
			this.purple = purple;
			this.dog = dog;
			replacementBound = true;
			return true;
		}

		/** 转场 5~19 tick，旧紫怪已移除，新实体还没生成；只有这段允许实体暂缺。 */
		public boolean actorsTemporarilyAbsent() {
			return stage == Stage.TRANSITION && transitionTicks >= 5 && transitionTicks < 20;
		}

		public boolean startPetting(int purpleId, int dogId, long tick) {
			if (stage != Stage.ANIMAL || !matches(purpleId, dogId)) return false;
			stage = Stage.PETTING;
			pettingStarted = tick;
			return true;
		}

		public boolean completePetting(int purpleId, int dogId, long tick) {
			if (stage != Stage.PETTING || !matches(purpleId, dogId)
					|| tick - pettingStarted < MIN_PETTING_TICKS) return false;
			stage = Stage.FINAL;
			return true;
		}

		public Outcome claimReward() {
			if (stage != Stage.FINAL) return Outcome.REJECTED;
			stage = Stage.CLOSED;
			return Outcome.REWARDED;
		}

		public Outcome finish(int purpleId, int dogId, boolean kill, boolean killPlayer) {
			if (!matches(purpleId, dogId)) return Outcome.REJECTED;
			if (kill || killPlayer) {
				// 只有第四阶段“丑陋”选项同时杀死玩家与紫怪；不接受任意客户端布尔组合。
				if (stage != Stage.ANIMAL || !kill || !killPlayer) return Outcome.REJECTED;
				stage = Stage.CLOSED;
				return Outcome.KILL_PLAYER;
			}
			if (stage == Stage.FINAL) {
				stage = Stage.CLOSED;
				return Outcome.ASSIMILATED;
			}
			if (stage == Stage.QUESTION || stage == Stage.REVENGE || stage == Stage.ANIMAL) {
				stage = Stage.CLOSED;
				return Outcome.CANCELLED;
			}
			return Outcome.REJECTED;
		}

		private boolean matches(int purpleId, int dogId) {
			return purpleId == purple.id() && dogId == dogId();
		}

		private void cancel() {
			stage = Stage.CLOSED;
		}
	}
}
