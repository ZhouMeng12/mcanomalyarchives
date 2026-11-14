package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrangeTreeMotionTest {
	@Test void materialSpeedsArePerSecondNotPerUpdate() {
		for (double speed : new double[] {3.1, 1.5, 0.4})
			assertEquals(speed / 4, StrangeTreeMotion.step(0.5, 20, 0.5, 1, 0, 0.775, p -> speed), 1e-9);
	}
	@Test void upperTrunkObsidianSlowsMovement() {
		assertEquals(0.1, StrangeTreeMotion.step(0.5, 20, 0.5, 1, 0, 0.775,
				p -> p.y() == 24 ? 0.4 : 3.1), 1e-9);
	}
	@Test void surfaceAboveTreeDoesNotAffectSpeed() {
		assertEquals(0.775, StrangeTreeMotion.step(0.5, 20, 0.5, 1, 0, 0.775,
				p -> p.y() >= 25 ? 0 : 3.1), 1e-9);
	}
	@Test void sweptTrunkCannotEnterBedrock() {
		double step = StrangeTreeMotion.step(0.5, 20, 0.5, 1, 0, 0.775,
				p -> p.x() >= 1 ? 0 : 3.1);
		assertTrue(0.5 + step + 0.49 < 1);
	}
	@Test void negativeCoordinatesUseFloor() {
		assertEquals(0, StrangeTreeMotion.step(-0.5, 20, -0.5, 1, 0, 0.775,
				p -> p.x() == -1 ? 0 : 3.1));
	}
}
