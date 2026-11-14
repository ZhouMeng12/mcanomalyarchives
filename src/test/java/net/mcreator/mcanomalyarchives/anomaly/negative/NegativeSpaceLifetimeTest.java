package net.mcreator.mcanomalyarchives.anomaly.negative;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NegativeSpaceLifetimeTest {
    @Test void spaceDissolvesAfterThirtySecondsNotTwentyFive() {
        int age = 0, elapsed = 0;
        while (age >= 0) {
            if (elapsed < 600) assertTrue(age <= 5);
            age = NegativeSpaceLifetime.nextAge(age);
            elapsed += NegativeSpaceLifetime.STEP_TICKS;
            assertTrue(elapsed <= 600, "Space must not live indefinitely");
        }
        assertEquals(600, elapsed);
    }
    @Test void restoredIntermediateAgeContinuesInsteadOfResetting() {
        assertEquals(5, NegativeSpaceLifetime.nextAge(4));
        assertEquals(-1, NegativeSpaceLifetime.nextAge(5));
    }
}
