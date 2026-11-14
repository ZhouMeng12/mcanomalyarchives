package net.mcreator.mcanomalyarchives.anomaly.poppy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoppyAddictionRulesTest {
    @Test void tenSecondEntryAndEscalationHaveDistinctHardLockAndExecutionBoundaries() {
        assertEquals(0, PoppyAddictionRules.level(195));
        assertEquals(1, PoppyAddictionRules.level(200));
        assertFalse(PoppyAddictionRules.hardGaze(PoppyAddictionRules.level(795)));
        assertTrue(PoppyAddictionRules.hardGaze(PoppyAddictionRules.level(800)));
        assertEquals(10, PoppyAddictionRules.level(2000));
        assertFalse(PoppyAddictionRules.lethal(2195));
        assertTrue(PoppyAddictionRules.lethal(2200));
    }
    @Test void continuousWatchingSurvivesEveryRefreshAndDiesAfterFullMaxLevelInterval() {
        var progress = new PoppyAddictionRules.Progress(0, 0, false);
        for (int t = 5; t <= 2200; t += 5) {
            progress = PoppyAddictionRules.advance(progress.ticks(), progress.away(), true, 5);
            assertEquals(t, progress.ticks());
            assertEquals(t >= 2200, PoppyAddictionRules.lethal(progress.ticks()));
        }
    }
    @Test void aBriefLookAwayRetainsDoseButAFullSecondWithdraws() {
        var progress = new PoppyAddictionRules.Progress(600, 0, false);
        for (int t = 5; t <= 15; t += 5) {
            progress = PoppyAddictionRules.advance(progress.ticks(), progress.away(), false, 5);
            assertEquals(600, progress.ticks()); assertFalse(progress.withdrawal());
        }
        var resumed = PoppyAddictionRules.advance(progress.ticks(), progress.away(), true, 5);
        assertEquals(605, resumed.ticks()); assertEquals(0, resumed.away());
        var withdrawn = PoppyAddictionRules.advance(progress.ticks(), progress.away(), false, 5);
        assertTrue(withdrawn.withdrawal()); assertEquals(0, withdrawn.ticks());
    }
    @Test void lookingAwayBeforeAddictionResetsInitialExposure() {
        assertEquals(0, PoppyAddictionRules.advance(195, 0, false, 5).ticks());
    }
    @Test void pullingAcrossAngleWrapUsesShortDirectionAndCannotOvershoot() {
        assertEquals(181, PoppyAddictionRules.approachAngle(179, -179, 5), .001);
        assertEquals(-181, PoppyAddictionRules.approachAngle(-179, 179, 5), .001);
        assertEquals(3, PoppyAddictionRules.approachAngle(0, 90, 3), .001);
        assertEquals(1, PoppyAddictionRules.approachAngle(0, 1, 3), .001);
    }
    @Test void crownLungeFinishesBeforeTheExistingLethalThreshold() {
        assertEquals(0,PoppyAddictionRules.scare(2160));
        assertTrue(PoppyAddictionRules.scare(2170)>0);
        assertEquals(1,PoppyAddictionRules.scare(2195));
        assertFalse(PoppyAddictionRules.lethal(2195));
        assertTrue(PoppyAddictionRules.lethal(2200));
    }
}
