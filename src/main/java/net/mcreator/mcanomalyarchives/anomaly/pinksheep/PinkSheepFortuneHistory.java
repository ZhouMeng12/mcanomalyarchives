package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import java.util.ArrayDeque;

/** A player cannot farm a new chain every time the same sheep relocates. */
public final class PinkSheepFortuneHistory {
	private long nextTick = Long.MIN_VALUE;
	private long generation;
	private final ArrayDeque<String> recent = new ArrayDeque<>();
	public boolean reserve(long tick) {
		if (tick < nextTick) return false;
		nextTick = tick + 2400;
		return true;
	}
	public boolean recent(String id) { return recent.contains(id); }
	public long generation() { return generation; }
	public void invalidate() { generation++; }
	public void completed(String id) {
		recent.remove(id);
		recent.addLast(id);
		while (recent.size() > 3) recent.removeFirst();
	}
}
