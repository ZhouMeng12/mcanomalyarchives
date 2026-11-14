package net.mcreator.mcanomalyarchives.anomaly.earth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarthRulesTest {
    @Test void craterBoundsAreFinalBeforeExplosionAndNeverExpandAfterwards() {
        assertEquals(EarthRules.radius(1), EarthRules.radius(0));
        assertEquals(-72, EarthRules.bottom(0, 0, 1));
        assertEquals(-72, EarthRules.bottom(0, 0, 0));
        for (int phase = 0; phase <= 1; phase++) {
            int radius = EarthRules.radius(phase);
            assertEquals(0, EarthRules.bottom(radius, 0, phase));
            assertEquals(0, EarthRules.bottom(radius + 100, 0, phase));
            for (int x = 0; x < radius; x++) {
                assertTrue(EarthRules.bottom(x + 1, 0, phase) >= EarthRules.bottom(x, 0, phase));
                assertEquals(EarthRules.bottom(x, 7, phase), EarthRules.bottom(-x, -7, phase));
            }
        }
    }
    @Test void gravityStrengthensButStopsAtBoundaryAndAftermath() {
        assertTrue(EarthRules.pull(4, 500) > EarthRules.pull(4, 20));
        assertTrue(EarthRules.pull(4, 500) > EarthRules.pull(200, 500));
        assertEquals(0, EarthRules.pull(256, 500));
        assertEquals(0, EarthRules.pull(0, 500));
        assertEquals(0, EarthRules.pull(4, EarthRules.END_AGE));
    }
    @Test void fullTenSecondMutationWindowFitsRewindBudget() {
        assertTrue(EarthRules.BLOCK_BUDGET * 200 < EarthRules.REWIND_LIMIT);
        assertTrue(EarthRules.BLOCK_BUDGET * 5 < EarthRules.REWIND_PENDING_LIMIT);
    }
    @Test void explosionImmediatelyStopsAttractionAndAllFurtherCarving() {
        assertFalse(EarthRules.canCollapse(0, EarthRules.BLAST_AGE - 1));
        assertTrue(EarthRules.pull(4, EarthRules.BLAST_AGE - 1) > 0);
        assertFalse(EarthRules.canCollapse(1, EarthRules.BLAST_AGE));
        assertFalse(EarthRules.canCollapse(0, EarthRules.BLAST_AGE));
        for (int age = EarthRules.BLAST_AGE; age < EarthRules.END_AGE + 250; age++) {
            assertEquals(0, EarthRules.pull(4, age));
            assertFalse(EarthRules.canCollapse(1, age));
        }
    }
    @Test void residualInwardMomentumIsRemovedWithoutCancellingTangentialOrOutwardMotion() {
        var toward = new net.minecraft.world.phys.Vec3(1, 0, 0);
        var released = EarthRules.releaseVelocity(new net.minecraft.world.phys.Vec3(2.5, -.2, .3), toward);
        assertEquals(0, released.x, 1e-8);
        assertEquals(-.2, released.y, 1e-8); assertEquals(.3, released.z, 1e-8);
        var away = new net.minecraft.world.phys.Vec3(-2, 1, .5);
        assertEquals(away, EarthRules.releaseVelocity(away, toward));
        assertEquals(away, EarthRules.releaseVelocity(away, net.minecraft.world.phys.Vec3.ZERO));
    }
    @Test void visualWarpRemainsButRealTerrainIsNeverExcavated() {
        assertEquals(.5, EarthRules.warpStrength(300), 1e-6);
        for (int phase = 0; phase <= 2; phase++)
            for (int tick = 0; tick <= 1100; tick++) assertFalse(EarthRules.canCollapse(phase, tick));
        assertEquals(0, EarthRules.warpStrength(600), 1e-6);
        assertEquals(0, EarthRules.warpStrength(606), 1e-6);
        assertEquals(0, EarthRules.warpStrength(612), 1e-6);
        assertFalse(EarthRules.canCollapse(2, 599));
    }
    @Test void localizedWarpStartsAtTenAndExpandsSmoothlyToFullField() {
        assertEquals(10,EarthRules.visualRadius(0));
        assertEquals(10,EarthRules.visualRadius(60));
        float previous=10;
        for(int tick=61;tick<=600;tick++) {
            float radius=EarthRules.visualRadius(tick);
            assertTrue(radius>=previous && radius<=256); previous=radius;
        }
        assertEquals(256,EarthRules.visualRadius(540));
        assertEquals(256,EarthRules.visualRadius(10000));
    }
}
