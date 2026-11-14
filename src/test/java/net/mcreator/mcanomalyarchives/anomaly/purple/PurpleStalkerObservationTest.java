package net.mcreator.mcanomalyarchives.anomaly.purple;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStalkerObservation.Watcher;

class PurpleStalkerObservationTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    @Test void fartherWatcherCountsEvenWhenNearestLooksAwayAndOnlyOnce() {
        var s = new PurpleStalkerObservation();
        var watchers = List.of(new Watcher(b, true, true, 4, 0), new Watcher(a, true, true, 900, 1));
        for (int i = 0; i < 5; i++) assertFalse(s.tick(watchers));
        assertTrue(s.tick(watchers));
        assertFalse(s.tick(watchers));
    }
    @Test void differentObserversCannotCombineBriefGlances() {
        var s = new PurpleStalkerObservation();
        for (int i = 0; i < 30; i++)
            assertFalse(s.tick(List.of(new Watcher(i % 2 == 0 ? a : b, true, true, 100, 1))));
    }
    @Test void blockedAndIneligibleObserversNeverCount() {
        var s = new PurpleStalkerObservation();
        for (int i = 0; i < 20; i++) assertFalse(s.tick(List.of(
                new Watcher(a, false, true, 1, 1), new Watcher(b, true, false, 1, 1))));
    }
    @Test void wallOrMissingObserverResetsContinuousObservation() {
        var s = new PurpleStalkerObservation();
        var watch = List.of(new Watcher(a, true, true, 100, 1));
        for (int i = 0; i < 5; i++) assertFalse(s.tick(watch));
        assertFalse(s.tick(List.of()));
        for (int i = 0; i < 5; i++) assertFalse(s.tick(watch));
        assertTrue(s.tick(watch));
    }
}
