package net.mcreator.mcanomalyarchives.anomaly.purple;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleExecutionSequence.Step;

class PurpleExecutionSequenceTest {
    @Test void hitOnceThenWithdrawAndEndOnce() {
        var s = new PurpleExecutionSequence();
        for (int i = 1; i <= 60; i++) assertEquals(i == 22 ? Step.HIT : i == 36 ? Step.END : Step.NONE, s.tick());
    }
    @Test void everyPreHitCancellationPreventsDamage() {
        for (int at = 0; at < 22; at++) {
            var s = new PurpleExecutionSequence();
            for (int i = 0; i < at; i++) s.tick();
            s.cancel();
            for (int i = 0; i < 40; i++) assertEquals(Step.NONE, s.tick());
        }
    }
    @Test void meshOnlyReachesVictimOnHitThenRetracts() {
        assertEquals(0, PurpleExecutionSequence.extension(9));
        assertEquals(.5, PurpleExecutionSequence.extension(16));
        assertEquals(1, PurpleExecutionSequence.extension(22));
        assertEquals(.5, PurpleExecutionSequence.extension(29));
        assertEquals(0, PurpleExecutionSequence.extension(36));
    }
}
