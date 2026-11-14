package net.mcreator.mcanomalyarchives.anomaly.earth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarthEjectaTest {
    @Test void materialTravelsOutwardsAndFollowsABallisticArc() {
        for (int i = 0; i < EarthEjecta.COUNT; i++) {
            var a = EarthEjecta.at(i, 0); var b = EarthEjecta.at(i, 30); var c = EarthEjecta.at(i, 60);
            assertTrue(Math.hypot(b.x(), b.z()) > Math.hypot(a.x(), a.z()));
            assertTrue(Math.hypot(c.x(), c.z()) > Math.hypot(b.x(), b.z()));
            assertTrue(b.y() > a.y());
            assertTrue(c.y() - b.y() < b.y() - a.y());
        }
    }
    @Test void rewindIsDeterministicAndFinishedDebrisDisappears() {
        var later = EarthEjecta.at(23, 100); var earlier = EarthEjecta.at(23, 70);
        assertTrue(Math.hypot(earlier.x(), earlier.z()) < Math.hypot(later.x(), later.z()));
        assertTrue(EarthEjecta.at(23, 150).size() > 0);
        assertEquals(0, EarthEjecta.at(23, EarthEjecta.LIFETIME).size());
        assertEquals(0, EarthEjecta.at(23, 200).size());
    }
}
