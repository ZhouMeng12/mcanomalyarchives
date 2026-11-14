package net.mcreator.mcanomalyarchives.anomaly.poppy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoppyScareSequenceTest {
    @Test void theTwoShotsPlayInOrderBeforeTheExistingLethalThreshold() {
        assertEquals(PoppyScareSequence.Stage.NONE,PoppyScareSequence.stage(PoppyAddictionRules.scare(2160)));
        assertEquals(PoppyScareSequence.Stage.MOUTH,PoppyScareSequence.stage(PoppyAddictionRules.scare(2161)));
        assertEquals(PoppyScareSequence.Stage.MOUTH,PoppyScareSequence.stage(PoppyAddictionRules.scare(2179)));
        assertEquals(PoppyScareSequence.Stage.WARNINGS,PoppyScareSequence.stage(PoppyAddictionRules.scare(2180)));
        assertEquals(PoppyScareSequence.Stage.WARNINGS,PoppyScareSequence.stage(PoppyAddictionRules.scare(2199)));
        assertFalse(PoppyAddictionRules.lethal(2199));
        assertTrue(PoppyAddictionRules.lethal(2200));
    }
}
