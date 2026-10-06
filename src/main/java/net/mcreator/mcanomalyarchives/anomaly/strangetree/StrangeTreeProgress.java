package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;

/** 一个玩家在一个维度的伐木/补种会话；退出、死亡或换维度不保留旧坐标集合。 */
public final class StrangeTreeProgress {
	public static final int TREE_THRESHOLD = 8;
	public static final int REPLANT_WINDOW = 200;
	public static final int REPLANT_RANGE = 3;
	private final String dimension;
	private final Set<Point> counted = new HashSet<>();
	private final ArrayDeque<Break> recent = new ArrayDeque<>();
	private int cuts;
	private int replants;

	private record Break(Point point, long tick) { }

	public StrangeTreeProgress(String dimension) {
		this.dimension = dimension;
	}

	public String dimension() { return dimension; }
	public int cuts() { return cuts; }

	public void recordBreak(Point point, long tick) {
		recent.addLast(new Break(point, tick));
		while (!recent.isEmpty() && tick - recent.peekFirst().tick > REPLANT_WINDOW) recent.removeFirst();
	}

	public boolean countTree(Point cut, Supplier<Set<Point>> connectedLogs) {
		if (counted.contains(cut)) return false;
		counted.add(cut);
		counted.addAll(connectedLogs.get());
		if (++cuts < TREE_THRESHOLD) return false;
		cuts = replants = 0;
		counted.clear();
		return true;
	}

	public boolean replant(Point point, long tick) {
		boolean matched = recent.stream().anyMatch(record -> tick >= record.tick
				&& tick - record.tick <= REPLANT_WINDOW && point.withinCube(record.point, REPLANT_RANGE));
		if (!matched) return false;
		cuts = 0;
		counted.clear();
		if (++replants >= 3) {
			replants = 0;
			return true;
		}
		return false;
	}
}
