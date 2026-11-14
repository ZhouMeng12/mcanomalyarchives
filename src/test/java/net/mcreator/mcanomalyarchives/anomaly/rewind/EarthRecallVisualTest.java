package net.mcreator.mcanomalyarchives.anomaly.rewind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EarthRecallVisualTest {
    @Test void noTerrainStillHasACompleteSixSecondSequence() {
        assertEquals(0,EarthRecallVisual.progress(20,0,0));
        assertEquals(.5f,EarthRecallVisual.progress(70,0,0));
        assertEquals(1,EarthRecallVisual.progress(120,0,0));
    }
    @Test void largeCraterHoldsPreviewUntilRestorationCatchesUp() {
        assertEquals(.25f,EarthRecallVisual.progress(200,40000,30000));
        assertEquals(20,EarthRecallVisual.packetAge(200,.25f));
        assertEquals(50,EarthRecallVisual.packetAge(1000,1));
        assertTrue(EarthRecallVisual.packetAge(1000,1)<60);
    }
    @Test void startupThenReverseProgressStayMonotonic() {
        int last=-1;
        for(int age=0;age<=120;age++) {
            int value=EarthRecallVisual.packetAge(age,EarthRecallVisual.progress(age,0,0));
            assertTrue(value>=last);last=value;
        }
    }
}
