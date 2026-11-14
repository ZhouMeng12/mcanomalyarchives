package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PinkSheepMeteorFlightTest {
	@Test void acceleratesAlongFixedRouteAndStopsAtEndpoint() {
		var start = new Vec3(24, 100, 0);
		var target = new Vec3(0, 40, 0);
		var flight = new PinkSheepMeteorFlight(start, target, 50);
		assertEquals(start, flight.position(0));
		assertEquals(start.lerp(target, .25), flight.position(25));
		assertEquals(target, flight.position(50));
		assertEquals(target, flight.position(200));
		assertTrue(flight.position(49).distanceTo(flight.position(50)) > flight.position(0).distanceTo(flight.position(1)));
	}
	@Test void everyIntegratedStepArrivesAtTheFixedPathNotAtAMovingVictim() {
		var flight = new PinkSheepMeteorFlight(new Vec3(30, 100, 10), new Vec3(0, 40, 0), 50);
		Vec3 position = flight.start();
		for (int t = 0; t < 50; t++) position = position.add(flight.velocity(position, t));
		assertEquals(0, position.distanceTo(flight.target()), 1e-8);
	}
	@Test void invalidDurationsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new PinkSheepMeteorFlight(Vec3.ZERO, Vec3.ZERO, 0));
	}
}
