package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreePlacement.Entry;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;

class StrangeTreePlacementTest {
	private final Point first = new Point(0, 64, 0);
	private final Point second = new Point(0, 65, 0);
	private final List<Entry<String>> entries = List.of(new Entry<>(0, first, "tree"), new Entry<>(1, second, "leaves"));

	private static final class World implements StrangeTreePlacement.Access<String> {
		final Map<Point, String> blocks = new HashMap<>();
		int writes;
		int rejectWrite = -1;
		int throwWrite = -1;
		Point unloaded;
		Point replaceAfterFirstWrite;
		@Override public boolean ready(Point point) { return point.y() >= -64 && point.y() < 320 && !point.equals(unloaded); }
		@Override public String read(Point point) { return blocks.getOrDefault(point, "air"); }
		@Override public boolean isAir(String state) { return state.equals("air"); }
		@Override public String air() { return "air"; }
		@Override public boolean write(Point point, String state) {
			writes++;
			if (writes == throwWrite) throw new IllegalStateException("Injected world failure");
			if (writes == rejectWrite || !ready(point)) return false;
			blocks.put(point, state);
			if (writes == 1 && replaceAfterFirstWrite != null) blocks.put(replaceAfterFirstWrite, "player-stone");
			return true;
		}
	}

	@Test
	void actualRejectedWriteCannotReportSuccessfulRestoration() {
		World world = new World();
		world.rejectWrite = 1;
		assertFalse(StrangeTreePlacement.apply(entries, world, attempted -> { }));
		assertEquals("air", world.read(first));
	}

	@Test
	void fullPreflightPreventsPartialWriteWhenCrownIsOutOfBuildHeight() {
		World world = new World();
		List<Entry<String>> crown = List.of(new Entry<>(0, new Point(0, 319, 0), "tree"),
				new Entry<>(1, new Point(0, 320, 0), "leaves"));
		assertFalse(StrangeTreePlacement.apply(crown, world, attempted -> fail("Must not write after failed preflight")));
		assertEquals(0, world.writes);
	}

	@Test
	void acceptedWriteMustActuallyBeVisibleBeforeReportingSuccess() {
		StrangeTreePlacement.Access<String> lies = new StrangeTreePlacement.Access<>() {
			@Override public boolean ready(Point point) { return true; }
			@Override public String read(Point point) { return "air"; }
			@Override public boolean isAir(String state) { return state.equals("air"); }
			@Override public String air() { return "air"; }
			@Override public boolean write(Point point, String state) { return true; }
		};
		assertFalse(StrangeTreePlacement.apply(entries, lies, attempted -> { }));
	}

	@Test
	void intentIsRecordedBeforeEachAttemptIncludingRejectedWrite() {
		World world = new World();
		world.rejectWrite = 2;
		List<List<Integer>> intents = new ArrayList<>();
		assertFalse(StrangeTreePlacement.apply(entries, world, planned -> {
			assertEquals(planned.size() - 1, world.writes);
			intents.add(planned);
		}));
		assertEquals(List.of(List.of(0), List.of(0, 1)), intents);
	}

	@Test
	void playerBlocksAreSkippedNotIncludedInUndoIntent() {
		World world = new World();
		world.blocks.put(second, "player-stone");
		List<List<Integer>> intents = new ArrayList<>();
		assertTrue(StrangeTreePlacement.apply(entries, world, intents::add));
		assertEquals("player-stone", world.read(second));
		assertEquals(List.of(List.of(0)), intents);
	}

	@Test
	void failedRelocationCanUndoItsAcceptedWritesBeforeOriginalRecovery() {
		World world = new World();
		world.rejectWrite = 2;
		List<Integer> planned = new ArrayList<>();
		assertFalse(StrangeTreePlacement.apply(entries, world, indices -> {
			planned.clear();
			planned.addAll(indices);
		}));
		assertEquals("tree", world.read(first));
		assertEquals("air", world.read(second));
		assertTrue(StrangeTreePlacement.rollback(planned.stream().map(entries::get).toList(), world));
		assertEquals("air", world.read(first));
		assertEquals("air", world.read(second));
	}

	@Test
	void failedRollbackMustRemainPendingAndCanRetryIdempotently() {
		World world = new World();
		world.blocks.put(first, "tree");
		world.blocks.put(second, "leaves");
		world.rejectWrite = 1;
		assertFalse(StrangeTreePlacement.rollback(entries, world));
		assertEquals("leaves", world.read(second));
		assertEquals("air", world.read(first));
		assertTrue(StrangeTreePlacement.rollback(entries, world));
		assertEquals("air", world.read(second));
	}

	@Test
	void rollbackNeverDeletesChangedPlayerBlocksOrUnattemptedPositions() {
		World world = new World();
		world.blocks.put(first, "player-stone");
		world.blocks.put(second, "leaves");
		assertTrue(StrangeTreePlacement.rollback(List.of(entries.getFirst()), world));
		assertEquals("player-stone", world.read(first));
		assertEquals("leaves", world.read(second));
		assertEquals(0, world.writes);
	}

	@Test
	void unloadedRollbackWaitsWithoutForcingWorldWrites() {
		World world = new World();
		world.blocks.put(first, "tree");
		world.blocks.put(second, "leaves");
		world.unloaded = second;
		assertFalse(StrangeTreePlacement.rollback(entries, world));
		assertEquals(0, world.writes);
		world.unloaded = null;
		assertTrue(StrangeTreePlacement.rollback(entries, world));
	}

	@Test
	void writeExceptionRetainsAttemptedIntentAndAllowsRollback() {
		World world = new World();
		world.throwWrite = 2;
		List<Integer> planned = new ArrayList<>();
		assertFalse(StrangeTreePlacement.apply(entries, world, indices -> {
			planned.clear();
			planned.addAll(indices);
		}));
		assertEquals(List.of(0, 1), planned);
		assertTrue(StrangeTreePlacement.rollback(entries, world));
		assertEquals("air", world.read(first));
	}

	@Test
	void callbackExceptionCannotWriteBeforeIntentWasSaved() {
		World world = new World();
		assertThrows(IllegalStateException.class, () -> StrangeTreePlacement.apply(entries, world, indices -> {
			throw new IllegalStateException("Saving failed");
		}));
		assertEquals(0, world.writes);
	}

	@Test
	void rollbackWriteExceptionDoesNotDeleteUndoRecordOrPreventOtherCleanup() {
		World world = new World();
		world.blocks.put(first, "tree");
		world.blocks.put(second, "leaves");
		world.throwWrite = 1;
		assertFalse(StrangeTreePlacement.rollback(entries, world));
		assertEquals("air", world.read(first));
		assertEquals("leaves", world.read(second));
		assertTrue(StrangeTreePlacement.rollback(entries, world));
	}

	@Test
	void failedPlacementUndoIsPersistedAndMustCompleteBeforeReloadedOriginalRestoration() {
		String log = "{Name:\"mcanomalyarchives:strangetreelog\"}";
		String leaves = "{Name:\"mcanomalyarchives:strangetreeleaves\"}";
		StrangeTreeState state = new StrangeTreeState("minecraft:overworld", first);
		state.beginRelocation(first, new Point(0, 63, 0), List.of(
				new StrangeTreeState.BlockBackup(first, log),
				new StrangeTreeState.BlockBackup(second, leaves)), null);
		Point offset = new Point(20, 0, 0);
		List<Entry<String>> destination = List.of(new Entry<>(0, new Point(20, 64, 0), log),
				new Entry<>(1, new Point(20, 65, 0), leaves));
		World world = new World();
		world.rejectWrite = 2;
		AtomicReference<CompoundTag> persisted = new AtomicReference<>();
		assertFalse(StrangeTreePlacement.apply(destination, world, indices -> {
			state.recordPlacementUndo(offset, indices);
			persisted.set(StrangeTreeNbt.write(state.snapshot()));
		}));
		assertEquals(List.of(0, 1), StrangeTreeNbt.read(persisted.get()).undo().entries());
		state.requestRecovery();
		persisted.set(StrangeTreeNbt.write(state.snapshot()));
		StrangeTreeState reloaded = StrangeTreeState.fromSnapshot(StrangeTreeNbt.read(persisted.get()));
		reloaded.recoverAfterLoad();
		world.rejectWrite = 3;
		assertFalse(StrangeTreePlacement.rollback(destination, world));
		assertThrows(IllegalStateException.class, () -> reloaded.finishStanding(first));
		assertFalse(reloaded.blocks().isEmpty());
		assertEquals("air", world.read(first)); // 清理目标失败时还不能还原原树、制造第二棵。
		assertTrue(StrangeTreePlacement.rollback(destination, world));
		reloaded.clearPlacementUndo();
		List<Entry<String>> original = List.of(new Entry<>(0, first, log), new Entry<>(1, second, leaves));
		assertTrue(StrangeTreePlacement.apply(original, world, indices -> { }));
		reloaded.finishStanding(first);
		assertNull(reloaded.undo());
		assertTrue(reloaded.blocks().isEmpty());
		assertEquals(log, world.read(first));
		assertEquals("air", world.read(destination.getFirst().point()));
	}

	@Test
	void worldCallbackCannotCauseLaterPlayerBlockToBeOverwrittenUsingStalePreflight() {
		World world = new World();
		world.replaceAfterFirstWrite = second;
		List<List<Integer>> intents = new ArrayList<>();
		assertTrue(StrangeTreePlacement.apply(entries, world, intents::add));
		assertEquals("player-stone", world.read(second));
		assertEquals(1, world.writes);
		assertEquals(List.of(List.of(0)), intents);
	}
}
