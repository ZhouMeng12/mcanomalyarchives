package net.mcreator.mcanomalyarchives.anomaly.purple;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PurplePettingProgressTest {
    @Test void blockedApproachAndReturnBothExit() {
        for (int phase : new int[]{2, 4}) {
            var p = new PurplePettingProgress();
            assertTrue(p.tick(phase, true, 3));
            for (int i = 0; i < 19; i++) assertTrue(p.tick(phase, true, 3));
            assertFalse(p.tick(phase, true, 3));
        }
    }
    @Test void movementResetsStallAndPhaseChangeResetsDistance() {
        var p = new PurplePettingProgress();
        for (int i = 0; i < 30; i++) assertTrue(p.tick(2, true, 5 - i * .1));
        for (int i = 0; i < 45; i++) assertTrue(p.tick(3, true, 1));
        for (int i = 0; i < 20; i++) assertTrue(p.tick(4, true, 4 - i * .1));
    }
    @Test void lostTrackingHasGraceButNotInfiniteWait() {
        var p = new PurplePettingProgress();
        for (int i = 0; i < 39; i++) assertTrue(p.tick(1, false, 0));
        assertTrue(p.tick(1, true, 0));
        for (int i = 0; i < 39; i++) assertTrue(p.tick(1, false, 0));
        assertFalse(p.tick(1, false, 0));
    }
    @Test void acknowledgementWaitAlsoHasDeadline() {
        var p = new PurplePettingProgress();
        for (int i = 0; i < 199; i++) assertTrue(p.tick(5, true, 0));
        assertFalse(p.tick(5, true, 0));
    }
}
