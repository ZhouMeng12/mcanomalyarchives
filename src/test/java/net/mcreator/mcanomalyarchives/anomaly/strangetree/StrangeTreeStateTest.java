package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.BlockBackup;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Effect;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Phase;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;

class StrangeTreeStateTest {
	private final Point base = new Point(0, 65, 0);
	private final Point ground = new Point(0, 64, 0);
	private final UUID player = UUID.randomUUID();
	private final List<BlockBackup> blocks = List.of(
			new BlockBackup(base, "{Name:\"mcanomalyarchives:strangetreelog\"}"));
	private final StrangeTreeState state = new StrangeTreeState("minecraft:overworld", base);

	private void bury() {
		assertTrue(state.beginBurrow(base, ground, blocks));
		for (int tick = 1; tick < 60; tick++) assertEquals(Effect.NONE, state.tick(true));
		assertEquals(Effect.BURY, state.tick(true));
		assertEquals(Phase.BURIED, state.phase());
	}

	private void emerge(boolean chase) {
		bury();
		assertFalse(state.recordLog(player, new Point(10, 64, 0)));
		state.arrive();
		assertTrue(state.recordLog(player, new Point(11, 64, 0)));
		assertTrue(state.emerge(chase));
	}

	@Test
	void journalsOriginalTreeAndBuriesOnlyAtSixtyLoadedTicks() {
		bury();
		assertEquals(base, state.origin());
		assertEquals(blocks, state.blocks());
		assertEquals(2, state.visualState());
		assertNull(state.target()); // 保留原规则：下一次砍树才绑定。
	}

	@Test
	void cannotBeginSecondOperationWhileTreeIsAlreadyBusy() {
		assertTrue(state.beginBurrow(base, ground, blocks));
		assertFalse(state.beginRelocation(base, ground, blocks, player));
		assertFalse(state.beginBurrow(base, ground, List.of()));
		assertEquals(blocks, state.blocks());
		assertEquals(Phase.BURROWING, state.phase());
	}

	@Test
	void onlyBuriedTreeCanBindTargetOrEmerge() {
		assertFalse(state.recordLog(player, base));
		assertFalse(state.emerge(false));
		bury();
		state.recordLog(player, base);
		assertFalse(state.emerge(false)); // 还没抵达埋伏点。
		state.arrive();
		assertTrue(state.emerge(false));
	}

	@Test
	void anotherLoggerCannotStealBoundTarget() {
		bury();
		state.recordLog(player, base);
		UUID other = UUID.randomUUID();
		assertFalse(state.recordLog(other, ground));
		assertEquals(player, state.target());
		assertEquals(base, state.ambush());
	}

	@Test
	void normalAttackHasSingleKillAtTwentyOneAndRestoresAtForty() {
		emerge(false);
		for (int tick = 1; tick < 30; tick++) assertEquals(Effect.NONE, state.tick(true));
		assertEquals(Effect.BEGIN_SWING, state.tick(true));
		assertEquals(4, state.visualState());
		for (int tick = 1; tick <= 40; tick++) {
			Effect effect = state.tick(true);
			if (tick == 21) {
				assertEquals(Effect.KILL, effect);
				assertEquals(player, state.consumeKillTarget());
				assertNull(state.consumeKillTarget());
			} else if (tick == 40) assertEquals(Effect.RESTORE, effect);
			else assertEquals(Effect.NONE, effect);
		}
		assertEquals(Phase.RECOVERING, state.phase());
		assertEquals(blocks, state.blocks()); // 世界确认还原之前绝不删快照。
	}

	@Test
	void recoveryCancelsUnfinishedKillAndKeepsRestorationJournal() {
		emerge(false);
		state.requestRecovery();
		assertNull(state.target());
		for (int tick = 0; tick < 100; tick++) assertNotEquals(Effect.KILL, state.tick(true));
		assertNull(state.consumeKillTarget());
		assertEquals(base, state.origin());
		assertEquals(blocks, state.blocks());
	}

	@Test
	void chasingAlternatesSwingAndWaitingWithoutRestartingWholeStory() {
		emerge(true);
		for (int tick = 1; tick < 30; tick++) state.tick(true);
		assertEquals(Effect.CHECK_CHASE, state.tick(true));
		assertTrue(state.resolveChase(true, false));
		assertEquals(Phase.CHASE_SWING, state.phase());
		for (int tick = 0; tick < 40; tick++) assertEquals(Effect.NONE, state.tick(true));
		assertEquals(Phase.CHASE_WAIT, state.phase());
		assertEquals(3, state.visualState());
		for (int tick = 1; tick < 30; tick++) assertEquals(Effect.NONE, state.tick(true));
		assertEquals(Effect.CHECK_CHASE, state.tick(true));
		assertTrue(state.resolveChase(true, true));
		assertEquals(Phase.FINAL_STRIKE, state.phase());
	}

	@Test
	void chaseFinalStrikeKillsOnceAndRestoresAtSixtyOne() {
		emerge(true);
		for (int tick = 0; tick < 30; tick++) state.tick(true);
		state.resolveChase(true, true);
		for (int tick = 1; tick <= 61; tick++) {
			Effect effect = state.tick(true);
			if (tick == 21) {
				assertEquals(Effect.KILL, effect);
				assertEquals(player, state.consumeKillTarget());
			} else if (tick == 61) assertEquals(Effect.RESTORE, effect);
			else assertEquals(Effect.NONE, effect);
		}
	}

	@Test
	void plantingCanCancelChaseDuringFinalWindupBeforeKill() {
		emerge(true);
		for (int tick = 0; tick < 30; tick++) state.tick(true);
		state.resolveChase(true, true);
		assertTrue(state.canCancelByPlanting(player));
		state.requestRecovery();
		for (int tick = 0; tick < 61; tick++) assertNotEquals(Effect.KILL, state.tick(true));
	}

	@Test
	void ordinaryCloseRangeStrikeDoesNotGainNewSaplingInterruptRule() {
		emerge(false);
		assertFalse(state.canCancelByPlanting(player));
	}

	@Test
	void missingChaseTargetRequestsRecoveryInsteadOfSchedulingAnotherKill() {
		emerge(true);
		for (int tick = 0; tick < 30; tick++) state.tick(true);
		assertTrue(state.resolveChase(false, false));
		assertEquals(Phase.RECOVERING, state.phase());
		assertNull(state.target());
		assertEquals(blocks, state.blocks());
	}

	@Test
	void earlyOrDuplicateChaseChecksCannotAdvanceInvalidPhases() {
		assertFalse(state.resolveChase(true, true));
		emerge(true);
		assertFalse(state.resolveChase(true, true));
		for (int tick = 0; tick < 30; tick++) state.tick(true);
		assertTrue(state.resolveChase(true, false));
		assertFalse(state.resolveChase(true, true));
		assertEquals(Phase.CHASE_SWING, state.phase());
	}

	@Test
	void idleTimeoutRespectsFiveTickUpdateGate() {
		bury();
		for (int tick = 0; tick < 1800; tick++) assertEquals(Effect.NONE, state.tick(true));
		assertEquals(Effect.NONE, state.tick(false));
		assertEquals(Effect.RESTORE, state.tick(true));
		assertEquals(Phase.RECOVERING, state.phase());
	}

	@Test
	void targetLeavingBuriedTreeClearsStaleDestinationAndReturnsToIdle() {
		bury();
		state.recordLog(player, base);
		state.arrive();
		state.targetGone(player);
		assertNull(state.target());
		assertNull(state.ambush());
		assertFalse(state.ambushed());
		assertEquals(Phase.BURIED, state.phase());
		assertEquals(0, state.ticks());
	}

	@Test
	void anotherPlayerLeavingDoesNotAffectOwnedAttack() {
		emerge(true);
		state.targetGone(UUID.randomUUID());
		assertEquals(Phase.EMERGING, state.phase());
		assertEquals(player, state.target());
	}

	@Test
	void leavingDuringAttackPreventsStrikingRespawnedSameUuid() {
		emerge(false);
		state.targetGone(player);
		assertEquals(Phase.RECOVERING, state.phase());
		assertNull(state.consumeKillTarget());
		for (int tick = 0; tick < 70; tick++) assertNotEquals(Effect.KILL, state.tick(true));
	}

	@Test
	void relocationDoesNotDependOnAvoidPlayerRemainingOnline() {
		state.beginRelocation(base, ground, blocks, player);
		state.targetGone(player);
		assertEquals(Phase.RELOCATING, state.phase());
		assertNull(state.target());
		for (int tick = 1; tick < 60; tick++) assertEquals(Effect.NONE, state.tick(true));
		assertEquals(Effect.RELOCATE, state.tick(true));
	}

	@Test
	void failedRelocationRetainsBlocksForRollback() {
		state.beginRelocation(base, ground, blocks, null);
		for (int tick = 0; tick < 60; tick++) state.tick(true);
		state.requestRecovery();
		assertEquals(blocks, state.blocks());
		assertEquals(base, state.origin());
	}

	@Test
	void loadNeverResumesAttackOrReplaysAlreadyDeliveredHit() {
		emerge(false);
		for (int tick = 0; tick < 30 + 21; tick++) state.tick(true);
		state.consumeKillTarget();
		StrangeTreeState restored = StrangeTreeState.fromSnapshot(state.snapshot());
		restored.recoverAfterLoad();
		assertEquals(Phase.RECOVERING, restored.phase());
		assertNull(restored.target());
		assertEquals(blocks, restored.blocks());
		assertNull(restored.consumeKillTarget());
	}

	@Test
	void loadingStandingTreeWithNoJournalDoesNotInventARecoveryOperation() {
		StrangeTreeState restored = StrangeTreeState.fromSnapshot(state.snapshot());
		restored.recoverAfterLoad();
		assertEquals(Phase.STANDING, restored.phase());
	}

	@Test
	void journalOnlyClearsAfterWorldConfirmsStandingRestoration() {
		bury();
		state.requestRecovery();
		for (int tick = 0; tick < 200; tick++) state.tick(true);
		assertEquals(blocks, state.blocks());
		state.finishStanding(base);
		assertEquals(Phase.STANDING, state.phase());
		assertEquals(0, state.visualState());
		assertTrue(state.blocks().isEmpty());
		assertNull(state.target());
	}

	@Test
	void snapshotsAreImmutableAndBoundedToCaptureVolume() {
		assertThrows(IllegalArgumentException.class, () -> new StrangeTreeState.Snapshot("minecraft:overworld",
				Phase.BURROWING, 0, base, ground, null, null, false, false, false,
				List.of(new BlockBackup(new Point(100, 65, 0), "{Name:\"minecraft:stone\"}"))));
		assertThrows(UnsupportedOperationException.class, () -> state.snapshot().blocks().add(blocks.getFirst()));
		assertThrows(IllegalArgumentException.class, () -> new BlockBackup(base, ""));
	}

	@Test
	void incompleteRelocationUndoSurvivesRecoveryAndReload() {
		state.beginRelocation(base, ground, blocks, player);
		state.recordPlacementUndo(new Point(20, 5, 0), List.of(0));
		state.requestRecovery();
		StrangeTreeState restored = StrangeTreeState.fromSnapshot(state.snapshot());
		restored.recoverAfterLoad();
		assertEquals(state.undo(), restored.undo());
		assertEquals(blocks, restored.blocks());
		assertThrows(IllegalStateException.class, () -> restored.finishStanding(base));
		restored.clearPlacementUndo(); // 世界确认目的地撤回已完成后才允许原位提交。
		restored.finishStanding(base);
		assertEquals(Phase.STANDING, restored.phase());
		assertTrue(restored.blocks().isEmpty());
	}

	@Test
	void intentRequiresActiveRelocationAndValidCapturedIndex() {
		assertThrows(IllegalStateException.class, () -> state.recordPlacementUndo(new Point(20, 0, 0), List.of(0)));
		state.beginRelocation(base, ground, blocks, player);
		assertThrows(IllegalArgumentException.class, () -> state.recordPlacementUndo(new Point(20, 0, 0), List.of(5)));
		assertNull(state.undo());
		state.recordPlacementUndo(new Point(20, 0, 0), List.of(0));
		assertThrows(UnsupportedOperationException.class, () -> state.undo().entries().add(1));
	}
}
