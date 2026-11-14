package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

/** One-shot incident clock. Removing a source cancels its release, even on the release tick. */
public final class PinkSheepAccidentSequence {
	public enum Action { NONE, CREATE_SOURCE, RELEASE, CANCEL }
	private final long sourceAt, releaseAt, expiresAt;
	private boolean created, finished;
	public PinkSheepAccidentSequence(long start) {
		sourceAt = start + 20; releaseAt = start + 60; expiresAt = start + 160;
	}
	public Action tick(long now, boolean eligible, boolean sourceAlive, boolean ready) {
		if (finished) return Action.NONE;
		if (!eligible || now >= expiresAt || (created && !sourceAlive)) {
			finished = true; return Action.CANCEL;
		}
		if (!created && now >= sourceAt) { created = true; return Action.CREATE_SOURCE; }
		if (created && now >= releaseAt && ready) { finished = true; return Action.RELEASE; }
		return Action.NONE;
	}
	public boolean finished() { return finished; }
}
