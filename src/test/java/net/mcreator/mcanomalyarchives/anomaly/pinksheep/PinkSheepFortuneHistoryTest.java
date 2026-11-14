package net.mcreator.mcanomalyarchives.anomaly.pinksheep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PinkSheepFortuneHistoryTest {
	@Test void repeatedEncountersCannotStackPendingRewards() {
		var history = new PinkSheepFortuneHistory();
		assertTrue(history.reserve(0)); assertFalse(history.reserve(899));
		assertFalse(history.reserve(2399)); assertTrue(history.reserve(2400));
	}
	@Test void onlyThreeMostRecentDistinctSuccessfulChainsAreExcluded() {
		var history = new PinkSheepFortuneHistory();
		for (String id : new String[] {"egg", "wool", "apple", "egg", "honey"}) history.completed(id);
		assertTrue(history.recent("egg")); assertTrue(history.recent("honey"));
		assertTrue(history.recent("apple")); assertFalse(history.recent("wool"));
	}
	@Test void dimensionChangeInvalidatesBothStagesEvenIfPlayerComesBack() {
		var history = new PinkSheepFortuneHistory();
		history.reserve(0); long ticket = history.generation();
		history.invalidate(); history.invalidate();
		assertNotEquals(ticket, history.generation());
		assertFalse(history.reserve(100));
	}
}
