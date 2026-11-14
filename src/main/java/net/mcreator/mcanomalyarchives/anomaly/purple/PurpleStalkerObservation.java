package net.mcreator.mcanomalyarchives.anomaly.purple;

import java.util.*;

/** Continuous observation is per observer; progress belongs to the encounter owner. */
public final class PurpleStalkerObservation {
    public record Watcher(UUID id, boolean eligible, boolean visible, double distanceSquared, double dot) { }
    private final Map<UUID, Integer> consecutive = new HashMap<>();
    private boolean discovered;

    public boolean tick(List<Watcher> watchers) {
        if (discovered) return false;
        Set<UUID> observing = new HashSet<>();
        for (Watcher w : watchers) {
            if (!w.eligible || !w.visible || w.distanceSquared > 1600 || w.dot <= .95) continue;
            observing.add(w.id);
            int ticks = consecutive.merge(w.id, 1, Integer::sum);
            if (ticks >= 6) discovered = true;
        }
        consecutive.keySet().retainAll(observing);
        return discovered;
    }
}
