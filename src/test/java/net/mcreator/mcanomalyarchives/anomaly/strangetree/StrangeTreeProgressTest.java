package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;

class StrangeTreeProgressTest {
	@Test
	void connectedLogsCountAsOneTree() {
		StrangeTreeProgress progress = new StrangeTreeProgress("minecraft:overworld");
		Point low = new Point(0, 64, 0), high = new Point(0, 65, 0);
		assertFalse(progress.countTree(low, () -> Set.of(low, high)));
		assertFalse(progress.countTree(high, () -> Set.of(high)));
		assertEquals(1, progress.cuts());
	}

	@Test
	void exactlyEightDistinctTreesTriggerAndClearCountedCluster() {
		StrangeTreeProgress progress = new StrangeTreeProgress("minecraft:overworld");
		for (int cut = 0; cut < 8; cut++) {
			Point point = new Point(cut * 4, 64, 0);
			assertEquals(cut == 7, progress.countTree(point, () -> Set.of(point)));
		}
		assertEquals(0, progress.cuts());
		Point first = new Point(0, 64, 0);
		assertFalse(progress.countTree(first, () -> Set.of(first)));
		assertEquals(1, progress.cuts());
	}

	@Test
	void validReplantWithinTenSecondsAndThreeBlocksResetsCount() {
		StrangeTreeProgress progress = new StrangeTreeProgress("minecraft:overworld");
		Point cut = new Point(0, 64, 0);
		progress.countTree(cut, () -> Set.of(cut));
		progress.recordBreak(cut, 100);
		assertFalse(progress.replant(new Point(3, 67, 3), 300));
		assertEquals(0, progress.cuts());
	}

	@Test
	void lateFarOrRewoundReplantDoesNotResetCount() {
		StrangeTreeProgress progress = new StrangeTreeProgress("minecraft:overworld");
		Point cut = new Point(0, 64, 0);
		progress.countTree(cut, () -> Set.of(cut));
		progress.recordBreak(cut, 100);
		assertFalse(progress.replant(cut, 99));
		assertFalse(progress.replant(cut, 301));
		assertFalse(progress.replant(new Point(4, 64, 0), 200));
		assertEquals(1, progress.cuts());
	}

	@Test
	void thirdQualifyingReplantRaisesAbandonSignal() {
		StrangeTreeProgress progress = new StrangeTreeProgress("minecraft:overworld");
		Point cut = new Point(0, 64, 0);
		progress.recordBreak(cut, 0);
		assertFalse(progress.replant(cut, 10));
		assertFalse(progress.replant(cut, 20));
		assertTrue(progress.replant(cut, 30));
		assertFalse(progress.replant(cut, 40));
	}

	@Test
	void newDimensionOrLoginSessionStartsWithoutOldCoordinates() {
		StrangeTreeProgress previous = new StrangeTreeProgress("minecraft:overworld");
		Point cut = new Point(0, 64, 0);
		previous.countTree(cut, () -> Set.of(cut));
		previous.recordBreak(cut, 0);
		StrangeTreeProgress next = new StrangeTreeProgress("mcanomalyarchives:cloud_dem");
		assertEquals(0, next.cuts());
		assertFalse(next.replant(cut, 20));
		assertEquals("mcanomalyarchives:cloud_dem", next.dimension());
	}

	@Test
	void alreadyCountedLogDoesNotRescanWholeConnectedTree() {
		StrangeTreeProgress progress = new StrangeTreeProgress("minecraft:overworld");
		Point low = new Point(0, 64, 0), high = new Point(0, 65, 0);
		AtomicInteger scans = new AtomicInteger();
		progress.countTree(low, () -> { scans.incrementAndGet(); return Set.of(low, high); });
		progress.countTree(high, () -> { scans.incrementAndGet(); return Set.of(high); });
		assertEquals(1, scans.get());
	}
}
