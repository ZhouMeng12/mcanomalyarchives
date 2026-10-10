package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.BlockBackup;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Phase;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Snapshot;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.PlacementUndo;

class StrangeTreeNbtTest {
	private final Point base = new Point(-17, 64, 30);
	private Snapshot sample() {
		return new Snapshot("minecraft:overworld", Phase.BURIED, 123, base, new Point(-17, 63, 30),
				UUID.randomUUID(), new Point(10, 64, 40), true, false, false,
				List.of(new BlockBackup(base, "{Name:\"mcanomalyarchives:strangetreelog\",Properties:{axis:\"y\"}}")));
	}

	@Test
	void actualNbtCodecPreservesEveryPersistentField() {
		Snapshot expected = sample();
		assertEquals(expected, StrangeTreeNbt.read(StrangeTreeNbt.write(expected)));
	}

	@Test
	void optionalFieldsAndEmptyStandingJournalRoundTrip() {
		Snapshot empty = new StrangeTreeState("mcanomalyarchives:cloud_dem", base).snapshot();
		assertEquals(empty, StrangeTreeNbt.read(StrangeTreeNbt.write(empty)));
	}

	@Test
	void readingDoesNotMutateStoredDataOrSilentlyNormalizeAttack() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		CompoundTag before = tag.copy();
		StrangeTreeState state = StrangeTreeState.fromSnapshot(StrangeTreeNbt.read(tag));
		state.recoverAfterLoad();
		assertEquals(Phase.RECOVERING, state.phase());
		assertEquals(before, tag); // adapter 决定何时写入取消后的状态。
	}

	@Test
	void unknownVersionFailsWithoutDeletingOriginalJournal() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		tag.putInt("Version", 99);
		CompoundTag before = tag.copy();
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
		assertEquals(before, tag);
	}

	@Test
	void invalidPhaseAndMissingPositionAreRejected() {
		CompoundTag invalid = StrangeTreeNbt.write(sample());
		invalid.putString("Phase", "NOT_A_PHASE");
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(invalid));
		CompoundTag missing = StrangeTreeNbt.write(sample());
		missing.getCompound("Origin").remove("X");
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(missing));
	}

	@Test
	void wrongBlockListTypeIsNotTreatedAsAnEmptyJournal() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		ListTag malformed = new ListTag();
		malformed.add(StringTag.valueOf("not a block"));
		tag.put("Blocks", malformed);
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
	}

	@Test
	void foreignCoordinatesCannotEscapeOriginalTreeVolume() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		tag.getList("Blocks", 10).getCompound(0).putInt("X", 5000);
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
	}

	@Test
	void clockUpdatesReuseTheExistingBlockJournal() {
		StrangeTreeState state = new StrangeTreeState("minecraft:overworld", base);
		state.beginBurrow(base, new Point(-17, 63, 30), sample().blocks());
		CompoundTag tag = StrangeTreeNbt.write(state.snapshot());
		Object journal = tag.get("Blocks");
		state.tick(true);
		StrangeTreeNbt.writeClock(tag, state);
		assertSame(journal, tag.get("Blocks"));
		assertEquals(1, StrangeTreeNbt.read(tag).ticks());
	}

	@Test
	void invalidBlockSnbtFailsBeforeCallerCanRewriteJournal() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		tag.getList("Blocks", 10).getCompound(0).putString("State", "not-snbt");
		tag.putString("ForensicMarker", "keep-original");
		CompoundTag original = tag.copy();
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
		assertEquals(original, tag);
	}

	@Test
	void missingOrUnknownBlockNameIsNotAcceptedAsARecoveryBlock() {
		CompoundTag missing = StrangeTreeNbt.write(sample());
		missing.getList("Blocks", 10).getCompound(0).putString("State", "{}");
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(missing));
		CompoundTag unknown = StrangeTreeNbt.write(sample());
		unknown.getList("Blocks", 10).getCompound(0).putString("State", "{Name:\"missing:block\"}");
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(unknown));
	}

	@Test
	void malformedPropertyTypesAreRejectedWithoutLosingOriginalBytes() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		tag.getList("Blocks", 10).getCompound(0).putString("State",
				"{Name:\"mcanomalyarchives:strangetreelog\",Properties:{axis:4}}");
		CompoundTag original = tag.copy();
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
		assertEquals(original, tag);
	}

	@Test
	void nonTreeBlockCannotBeInjectedIntoRecoveryJournal() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		tag.getList("Blocks", 10).getCompound(0).putString("State", "{Name:\"minecraft:stone\"}");
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
	}

	@Test
	void versionOneWithoutUndoRemainsReadableAndIsUpgradedOnlyWhenWritten() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		tag.putInt("Version", 1);
		CompoundTag original = tag.copy();
		Snapshot parsed = StrangeTreeNbt.read(tag);
		assertEquals(original, tag);
		assertNull(parsed.undo());
		assertEquals(2, StrangeTreeNbt.write(parsed).getInt("Version"));
	}

	@Test
	void versionTwoPersistsAttemptedRelocationIndicesAndOffset() {
		Snapshot old = sample();
		Snapshot relocating = new Snapshot(old.dimension(), Phase.RELOCATING, 60, base, old.ground(),
				old.target(), null, false, false, false, old.blocks(),
				new PlacementUndo(new Point(20, 5, -20), List.of(0)));
		CompoundTag stored = StrangeTreeNbt.write(relocating);
		assertEquals(relocating, StrangeTreeNbt.read(stored));
		assertEquals(2, stored.getInt("Version"));
	}

	@Test
	void invalidUndoCannotEscapeJournalOrRepeatSameIndex() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		tag.putString("Phase", "RECOVERING");
		CompoundTag undo = new CompoundTag();
		CompoundTag offset = new CompoundTag();
		offset.putInt("X", 20);
		offset.putInt("Y", 0);
		offset.putInt("Z", 0);
		undo.put("Offset", offset);
		undo.putIntArray("Entries", new int[] {5});
		tag.put("PlacementUndo", undo);
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
		undo.putIntArray("Entries", new int[] {0, 0});
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
		undo.putIntArray("Entries", new int[] {0});
		offset.putInt("X", 5000);
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
	}

	@Test
	void versionOneCannotSilentlyIgnoreVersionTwoUndoData() {
		CompoundTag tag = StrangeTreeNbt.write(sample());
		tag.putInt("Version", 1);
		tag.put("PlacementUndo", new CompoundTag());
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeNbt.read(tag));
	}
}
