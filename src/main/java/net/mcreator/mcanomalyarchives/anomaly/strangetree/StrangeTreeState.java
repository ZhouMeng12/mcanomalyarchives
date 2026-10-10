package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.HashSet;

/** 怪树的唯一阶段/目标/恢复快照。无 Minecraft 对象、世界引用或延迟回调。 */
public final class StrangeTreeState {
	public static final int BURROW_TICKS = 60;
	public static final int EMERGE_TICKS = 30;
	public static final int SWING_TICKS = 40;
	public static final int KILL_TICK = 21;
	public static final int CHASE_INTERVAL = 30;
	public static final int IDLE_TICKS = 90 * 20;
	public static final int MAX_JOURNAL_BLOCKS = 7 * 9 * 9;

	public enum Phase {
		STANDING, BURROWING, BURIED, EMERGING, NORMAL_STRIKE, CHASE_SWING, CHASE_WAIT,
		FINAL_STRIKE, RELOCATING, RECOVERING
	}
	public enum Effect { NONE, BURY, BEGIN_SWING, CHECK_CHASE, KILL, RESTORE, RELOCATE }
	public record Point(int x, int y, int z) {
		public boolean withinCube(Point other, int range) {
			return Math.abs((long) x - other.x) <= range && Math.abs((long) y - other.y) <= range
					&& Math.abs((long) z - other.z) <= range;
		}
	}
	public record BlockBackup(Point point, String state) {
		public BlockBackup {
			Objects.requireNonNull(point);
			if (state == null || state.isBlank() || state.length() > 1024) throw new IllegalArgumentException("Invalid block state");
		}
	}

	/** 迁居每次写入前持久化索引；期望树块由原快照推导，不复制第二份树身数据。 */
	public record PlacementUndo(Point offset, List<Integer> entries) {
		public PlacementUndo {
			Objects.requireNonNull(offset);
			entries = List.copyOf(entries);
			if (entries.isEmpty() || entries.size() > MAX_JOURNAL_BLOCKS
					|| new HashSet<>(entries).size() != entries.size()
					|| Math.abs((long) offset.x()) > 64 || Math.abs((long) offset.z()) > 64
					|| Math.abs((long) offset.y()) > 4096) {
				throw new IllegalArgumentException("Invalid relocation undo plan");
			}
		}
	}

	public record Snapshot(String dimension, Phase phase, int ticks, Point origin, Point ground,
			UUID target, Point ambush, boolean ambushed, boolean chasing, boolean killDelivered,
			List<BlockBackup> blocks, PlacementUndo undo) {
		/** 兼容旧版本1快照调用；没有部分迁居写入时无需撤回记录。 */
		public Snapshot(String dimension, Phase phase, int ticks, Point origin, Point ground,
				UUID target, Point ambush, boolean ambushed, boolean chasing, boolean killDelivered,
				List<BlockBackup> blocks) {
			this(dimension, phase, ticks, origin, ground, target, ambush, ambushed, chasing, killDelivered, blocks, null);
		}

		public Snapshot {
			Objects.requireNonNull(dimension);
			Objects.requireNonNull(phase);
			Objects.requireNonNull(origin);
			if (ticks < 0) throw new IllegalArgumentException("Negative phase clock");
			blocks = List.copyOf(blocks);
			if (blocks.size() > MAX_JOURNAL_BLOCKS) throw new IllegalArgumentException("Oversized tree journal");
			for (BlockBackup backup : blocks) {
				Point point = backup.point;
				if (Math.abs((long) point.x - origin.x) > 4 || Math.abs((long) point.z - origin.z) > 4
						|| (long) point.y < origin.y || (long) point.y > (long) origin.y + 6) {
					throw new IllegalArgumentException("Tree journal escapes its original capture volume");
				}
			}
			if (new HashSet<>(blocks.stream().map(BlockBackup::point).toList()).size() != blocks.size()) {
				throw new IllegalArgumentException("Duplicate tree backup position");
			}
			if (undo != null) {
				if (phase != Phase.RELOCATING && phase != Phase.RECOVERING) {
					throw new IllegalArgumentException("Undo plan outside relocation/recovery");
				}
				for (int index : undo.entries()) {
					if (index < 0 || index >= blocks.size()) throw new IllegalArgumentException("Undo index outside journal");
				}
			}
		}
	}

	private String dimension;
	private Phase phase = Phase.STANDING;
	private int ticks;
	private Point origin;
	private Point ground;
	private UUID target;
	private Point ambush;
	private boolean ambushed;
	private boolean chasing;
	private boolean killDelivered;
	private List<BlockBackup> blocks = List.of();
	private PlacementUndo undo;
	private long revision;

	public StrangeTreeState(String dimension, Point origin) {
		this.dimension = Objects.requireNonNull(dimension);
		this.origin = Objects.requireNonNull(origin);
	}

	public static StrangeTreeState fromSnapshot(Snapshot snapshot) {
		StrangeTreeState state = new StrangeTreeState(snapshot.dimension, snapshot.origin);
		state.phase = snapshot.phase;
		state.ticks = snapshot.ticks;
		state.ground = snapshot.ground;
		state.target = snapshot.target;
		state.ambush = snapshot.ambush;
		state.ambushed = snapshot.ambushed;
		state.chasing = snapshot.chasing;
		state.killDelivered = snapshot.killDelivered;
		state.blocks = snapshot.blocks;
		state.undo = snapshot.undo;
		return state;
	}

	public Snapshot snapshot() {
		return new Snapshot(dimension, phase, ticks, origin, ground, target, ambush, ambushed,
				chasing, killDelivered, blocks, undo);
	}

	public String dimension() { return dimension; }
	public Phase phase() { return phase; }
	public int ticks() { return ticks; }
	public long revision() { return revision; }
	public Point origin() { return origin; }
	public Point ground() { return ground; }
	public UUID target() { return target; }
	public Point ambush() { return ambush; }
	public boolean ambushed() { return ambushed; }
	public List<BlockBackup> blocks() { return blocks; }
	public PlacementUndo undo() { return undo; }

	public void recordPlacementUndo(Point offset, List<Integer> entries) {
		if (phase != Phase.RELOCATING) throw new IllegalStateException("Not relocating");
		PlacementUndo plan = new PlacementUndo(offset, entries);
		new Snapshot(dimension, phase, ticks, origin, ground, target, ambush,
				ambushed, chasing, killDelivered, blocks, plan);
		undo = plan;
		revision++;
	}

	public void clearPlacementUndo() {
		if (undo != null) {
			undo = null;
			revision++;
		}
	}

	public int visualState() {
		return switch (phase) {
			case STANDING -> 0;
			case BURROWING, RELOCATING -> 1;
			case BURIED, RECOVERING -> 2;
			case EMERGING, CHASE_WAIT -> 3;
			case NORMAL_STRIKE, CHASE_SWING, FINAL_STRIKE -> 4;
		};
	}

	public boolean beginBurrow(Point origin, Point ground, List<BlockBackup> backups) {
		return begin(Phase.BURROWING, origin, ground, backups, null);
	}

	public boolean beginRelocation(Point origin, Point ground, List<BlockBackup> backups, UUID avoidPlayer) {
		return begin(Phase.RELOCATING, origin, ground, backups, avoidPlayer);
	}

	private boolean begin(Phase next, Point origin, Point ground, List<BlockBackup> backups, UUID target) {
		if (phase != Phase.STANDING) return false;
		Snapshot accepted = new Snapshot(dimension, next, 0, Objects.requireNonNull(origin),
				Objects.requireNonNull(ground), target, null, false, false, false, backups);
		this.origin = accepted.origin();
		this.ground = accepted.ground();
		this.blocks = accepted.blocks();
		undo = null;
		this.target = target;
		ambush = null;
		ambushed = chasing = killDelivered = false;
		change(next);
		return true;
	}

	/** 保留现有规则：钻地后首次砍树才绑定目标；其他人的砍树不会夺走目标。 */
	public boolean recordLog(UUID player, Point position) {
		if (phase != Phase.BURIED) return false;
		ticks = 0;
		if (target != null && !target.equals(player)) return false;
		target = Objects.requireNonNull(player);
		ambush = Objects.requireNonNull(position);
		revision++;
		return ambushed;
	}

	public void arrive() {
		if (phase == Phase.BURIED && target != null && ambush != null && !ambushed) {
			ambushed = true;
			revision++;
		}
	}

	public boolean emerge(boolean chase) {
		if (phase != Phase.BURIED || target == null || !ambushed) return false;
		chasing = chase;
		killDelivered = false;
		change(Phase.EMERGING);
		return true;
	}

	public boolean canCancelByPlanting(UUID player) {
		return player.equals(target) && (phase == Phase.BURIED || chasing);
	}

	/** 目标离线/换维度：潜伏回待机；攻击链取消，迁居只失去避让对象、不取消。 */
	public void targetGone(UUID player) {
		if (!player.equals(target)) return;
		if (phase == Phase.RELOCATING) {
			target = null;
			revision++;
		} else if (phase == Phase.BURIED) {
			target = null;
			ambush = null;
			ambushed = false;
			ticks = 0;
			revision++;
		} else {
			requestRecovery();
		}
	}

	public Effect tick(boolean buriedUpdate) {
		if (phase == Phase.STANDING) return Effect.NONE;
		if (ticks < Integer.MAX_VALUE) ticks++;
		return switch (phase) {
			case BURROWING -> {
				if (ticks == BURROW_TICKS) { change(Phase.BURIED); yield Effect.BURY; }
				yield Effect.NONE;
			}
			case RELOCATING -> ticks == BURROW_TICKS ? Effect.RELOCATE : Effect.NONE;
			case BURIED -> {
				if (ticks > IDLE_TICKS && buriedUpdate) { requestRecovery(); yield Effect.RESTORE; }
				yield Effect.NONE;
			}
			case EMERGING -> {
				if (ticks != EMERGE_TICKS) yield Effect.NONE;
				if (chasing) yield Effect.CHECK_CHASE;
				change(Phase.NORMAL_STRIKE);
				yield Effect.BEGIN_SWING;
			}
			case CHASE_WAIT -> ticks == CHASE_INTERVAL ? Effect.CHECK_CHASE : Effect.NONE;
			case CHASE_SWING -> {
				if (ticks == SWING_TICKS) change(Phase.CHASE_WAIT);
				yield Effect.NONE;
			}
			case NORMAL_STRIKE, FINAL_STRIKE -> {
				int end = phase == Phase.FINAL_STRIKE ? KILL_TICK + SWING_TICKS : SWING_TICKS;
				if (ticks == KILL_TICK && !killDelivered) yield Effect.KILL;
				if (ticks >= end) { requestRecovery(); yield Effect.RESTORE; }
				yield Effect.NONE;
			}
			case RECOVERING -> {
				if (ticks >= 20) { ticks = 0; yield Effect.RESTORE; }
				yield Effect.NONE;
			}
			case STANDING -> Effect.NONE;
		};
	}

	public boolean resolveChase(boolean targetAvailable, boolean inKillRange) {
		if (!chasing || !((phase == Phase.EMERGING && ticks >= EMERGE_TICKS)
				|| (phase == Phase.CHASE_WAIT && ticks >= CHASE_INTERVAL))) return false;
		if (!targetAvailable) requestRecovery();
		else change(inKillRange ? Phase.FINAL_STRIKE : Phase.CHASE_SWING);
		return true;
	}

	public UUID consumeKillTarget() {
		if ((phase != Phase.NORMAL_STRIKE && phase != Phase.FINAL_STRIKE)
				|| ticks != KILL_TICK || killDelivered) return null;
		UUID result = target;
		target = null;
		killDelivered = true;
		revision++;
		return result;
	}

	public void requestRecovery() {
		target = null;
		ambush = null;
		ambushed = chasing = false;
		change(Phase.RECOVERING); // 恢复未完成前保留 origin/ground/blocks。
	}

	public void recoverAfterLoad() {
		if (phase != Phase.STANDING || !blocks.isEmpty()) requestRecovery();
	}

	public void finishStanding(Point base) {
		if (undo != null) throw new IllegalStateException("Cannot discard journal with incomplete placement undo");
		origin = Objects.requireNonNull(base);
		ground = null;
		blocks = List.of();
		target = null;
		ambush = null;
		ambushed = chasing = killDelivered = false;
		change(Phase.STANDING);
	}

	private void change(Phase phase) {
		this.phase = phase;
		ticks = 0;
		revision++;
	}
}
