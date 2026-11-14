package net.mcreator.mcanomalyarchives.anomaly.pinksheep;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepObservation.Result.*;

class PinkSheepObservationTest {
	final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), sheep = UUID.randomUUID();
	@Test void anotherObserverKeepsSheepDuringBlinkRegardlessOfProcessingOrder() {
		for (boolean reverse : new boolean[] {false, true}) {
			var state = new PinkSheepObservation();
			state.beginTick(); state.observe(a, sheep, 0, 2);
			state.beginTick();
			if (reverse) state.observe(b, sheep, 1, 100);
			assertEquals(BLINK, state.observe(a, sheep, 1, 2));
			if (!reverse) state.observe(b, sheep, 1, 100);
			assertTrue(state.watched(sheep));
			state.beginTick();
			assertEquals(NONE, state.observe(a, sheep, 2, 2));
			assertFalse(state.watched(sheep));
		}
	}
	@Test void firstWitnessCannotBeStolenButRelocationStartsNewEncounter() {
		var state = new PinkSheepObservation();
		for (int t = 0; t < 40; t++) { state.beginTick(); state.observe(a, sheep, t, 900); }
		assertEquals(a, state.witness(sheep));
		for (int t = 40; t < 80; t++) assertNotEquals(FIRST_SIGHT, state.observe(b, sheep, t, 900));
		state.resetSheep(sheep);
		for (int t = 80; t < 120; t++) state.observe(b, sheep, t, 900);
		assertEquals(b, state.witness(sheep));
	}
	@Test void closingAllEyesRemovesAllObservationAndReopensAfterFiveTicks() {
		var state = new PinkSheepObservation();
		state.closeEyes(a, 100); state.closeEyes(b, 100);
		for (int t = 100; t < 105; t++) {
			state.beginTick();
			assertEquals(NONE, state.observe(a, sheep, t, 900));
			assertEquals(NONE, state.observe(b, sheep, t, 900));
			assertFalse(state.watched(sheep));
		}
		assertEquals(WATCH, state.observe(a, sheep, 105, 900));
	}
	@Test void lookingAwayResetsContinuousSight() {
		var state = new PinkSheepObservation();
		for (int t = 0; t < 39; t++) state.observe(a, sheep, t, 900);
		state.observe(a, null, 39, 900);
		assertEquals(WATCH, state.observe(a, sheep, 40, 900));
		assertNull(state.witness(sheep));
	}
	@Test void sharedEyeFatigueClearsAlreadyCountedObserversInSameTick() {
		var state = new PinkSheepObservation();
		state.beginTick(); state.observe(a, sheep, 0, 900); state.observe(b, sheep, 0, 900);
		assertEquals(2, state.observers(sheep).size());
		state.closeAllEyes(sheep, 0);
		assertFalse(state.watched(sheep)); assertTrue(state.observers(sheep).isEmpty());
		state.beginTick(); assertEquals(NONE, state.observe(b, sheep, 1, 900));
	}
}
