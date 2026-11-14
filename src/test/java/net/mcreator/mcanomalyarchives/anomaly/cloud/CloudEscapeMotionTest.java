package net.mcreator.mcanomalyarchives.anomaly.cloud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudEscapeMotionTest {
    @Test void delayedResponseHasBoundedFlightAndRecovery() {
        var motion = new CloudEscapeMotion();
        assertTrue(motion.disturb(-20, 3));
        for (int i=0;i<CloudEscapeMotion.REACTION_TICKS;i++) {
            assertFalse(motion.fleeing());
            assertEquals(1, motion.multiplier());
            motion.tick();
        }
        for (int i=0;i<CloudEscapeMotion.FLIGHT_TICKS;i++) {
            assertTrue(motion.fleeing());
            assertEquals(4, motion.multiplier());
            assertEquals(-1, motion.x()); assertEquals(0, motion.z());
            assertFalse(motion.disturb(0, -100), "Repeated arrows must not prolong or reverse flight");
            motion.tick();
        }
        assertFalse(motion.fleeing());
        for (int i=0;i<CloudEscapeMotion.RECOVERY_TICKS;i++) {
            assertFalse(motion.disturb(1, 0));
            motion.tick();
        }
        assertTrue(motion.disturb(0, -2));
        assertEquals(0, motion.x()); assertEquals(-1, motion.z());
    }

    @Test void directionsAlwaysStayHorizontalAndPointAwayFromThreat() {
        for (double x : new double[]{-50, -1, 0, 1, 50}) {
            for (double z : new double[]{-50, -1, 0, 1, 50}) {
                var motion = new CloudEscapeMotion();
                motion.disturb(x,z);
                assertEquals(1, Math.abs(motion.x()) + Math.abs(motion.z()));
                assertTrue(motion.x()*x + motion.z()*z >= 0);
            }
        }
    }

    @Test void invalidThreatCannotPoisonMovementOrLockFutureResponses() {
        var motion = new CloudEscapeMotion();
        assertFalse(motion.disturb(Double.NaN, 0));
        assertFalse(motion.disturb(0, Double.POSITIVE_INFINITY));
        assertTrue(motion.disturb(0, 0));
        assertEquals(1, motion.x());
    }
}
