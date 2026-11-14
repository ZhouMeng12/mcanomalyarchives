package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import java.util.*;

/** Server-tick observation ledger. Blinks remove one observer, never the sheep itself. */
public final class PinkSheepObservation {
	public enum Result { NONE, WATCH, FIRST_SIGHT, BLINK }
	private record Gaze(UUID sheep, int ticks) { }
	private final Map<UUID, Gaze> gazes = new HashMap<>();
	private final Map<UUID, Long> closedUntil = new HashMap<>();
	private final Map<UUID, UUID> firstWitness = new HashMap<>();
	private final Set<UUID> watched = new HashSet<>();

	public void beginTick() { watched.clear(); }
	public Result observe(UUID player, UUID sheep, long tick, int blinkAfter) {
		if (tick < closedUntil.getOrDefault(player, 0L) || sheep == null) {
			gazes.remove(player);
			return Result.NONE;
		}
		Gaze previous = gazes.get(player);
		int ticks = previous != null && previous.sheep.equals(sheep) ? previous.ticks + 1 : 1;
		if (ticks >= blinkAfter) {
			closeEyes(player, tick);
			return Result.BLINK;
		}
		gazes.put(player, new Gaze(sheep, ticks));
		watched.add(sheep);
		if (ticks >= 40 && firstWitness.putIfAbsent(sheep, player) == null) return Result.FIRST_SIGHT;
		return Result.WATCH;
	}
	public void closeEyes(UUID player, long tick) {
		closedUntil.put(player, tick + 5);
		gazes.remove(player);
	}
	public boolean watched(UUID sheep) { return watched.contains(sheep); }
	public UUID witness(UUID sheep) { return firstWitness.get(sheep); }
	public Set<UUID> observers(UUID sheep) {
		Set<UUID> result = new HashSet<>();
		gazes.forEach((player, gaze) -> { if (gaze.sheep.equals(sheep)) result.add(player); });
		return result;
	}
	public void closeAllEyes(UUID sheep, long tick) {
		for (UUID player : observers(sheep)) closeEyes(player, tick);
		watched.remove(sheep);
	}
	public void forgetPlayer(UUID player) { gazes.remove(player); closedUntil.remove(player); }
	public void resetSheep(UUID sheep) {
		firstWitness.remove(sheep);
		watched.remove(sheep);
		gazes.values().removeIf(gaze -> gaze.sheep.equals(sheep));
	}
	public void clear() { gazes.clear(); closedUntil.clear(); firstWitness.clear(); watched.clear(); }
}
