package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StrangeTreeExposureTest {
	@Test void smallHolesDoNotTriggerButTwoThirdsExposureDoes() {
		assertFalse(StrangeTreeExposure.shouldBurrow(0, 18));
		assertFalse(StrangeTreeExposure.shouldBurrow(11, 18));
		assertTrue(StrangeTreeExposure.shouldBurrow(12, 18));
		assertTrue(StrangeTreeExposure.shouldBurrow(18, 18));
	}
	@Test void UnloadedOrIncompleteSamplesCannotTrigger() {
		assertFalse(StrangeTreeExposure.shouldBurrow(12, 12));
		assertFalse(StrangeTreeExposure.shouldBurrow(19, 18));
	}
}
