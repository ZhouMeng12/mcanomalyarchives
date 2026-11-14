package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepAccidentSequence.Action.*;

class PinkSheepAccidentSequenceTest {
	@Test void cueSourceAndReleaseAreDistinctAndOneShot() {
		var sequence = new PinkSheepAccidentSequence(100);
		assertEquals(NONE, sequence.tick(119, true, true, true));
		assertEquals(CREATE_SOURCE, sequence.tick(120, true, true, true));
		assertEquals(NONE, sequence.tick(159, true, true, true));
		assertEquals(RELEASE, sequence.tick(160, true, true, true));
		assertEquals(NONE, sequence.tick(161, true, false, true));
	}
	@Test void removedSourceOnReleaseTickCancelsInsteadOfRecreatingIt() {
		var sequence = new PinkSheepAccidentSequence(0);
		sequence.tick(20, true, true, false);
		assertEquals(CANCEL, sequence.tick(60, true, false, true));
		assertEquals(NONE, sequence.tick(61, true, true, true));
	}
	@Test void arrowMustActuallyReachChargeAndCannotWaitForever() {
		var sequence = new PinkSheepAccidentSequence(0);
		sequence.tick(20, true, true, false);
		assertEquals(NONE, sequence.tick(60, true, true, false));
		assertEquals(NONE, sequence.tick(159, true, true, false));
		assertEquals(CANCEL, sequence.tick(160, true, true, true));
	}
	@Test void leavingDimensionOrDisablingRuleCancelsPreparationPermanently() {
		var sequence = new PinkSheepAccidentSequence(0);
		assertEquals(CANCEL, sequence.tick(10, false, true, true));
		assertEquals(NONE, sequence.tick(20, true, true, true));
	}
}
