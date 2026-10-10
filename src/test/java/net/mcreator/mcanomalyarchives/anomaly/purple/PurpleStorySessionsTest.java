package net.mcreator.mcanomalyarchives.anomaly.purple;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.Actor;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.Outcome;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.Session;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.Stage;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.TransitionStep;

class PurpleStorySessionsTest {
	private final PurpleStorySessions sessions = new PurpleStorySessions();
	private final UUID player = UUID.randomUUID();
	private final Actor original = new Actor(10, UUID.randomUUID());
	private final Actor replacement = new Actor(20, UUID.randomUUID());
	private final Actor dog = new Actor(21, UUID.randomUUID());

	private Session question() {
		return sessions.open(player, "minecraft:overworld", original, null, Stage.QUESTION);
	}

	private Session animal() {
		return sessions.open(player, "minecraft:overworld", replacement, dog, Stage.ANIMAL);
	}

	private Session finale() {
		Session session = animal();
		assertTrue(session.startPetting(20, 21, 100));
		assertTrue(session.completePetting(20, 21, 160));
		return session;
	}

	@Test
	void normalStoryFollowsAllStagesAndSettlesRewardOnce() {
		Session session = question();
		assertTrue(session.advanceToPhase2(10));
		assertTrue(session.beginPhase4(10));
		for (int tick = 1; tick <= 60; tick++) {
			TransitionStep step = session.tickTransition();
			if (tick == 20) assertTrue(session.bindReplacement(replacement, dog));
			assertEquals(tick == 5 ? TransitionStep.REMOVE_OLD
					: tick == 20 ? TransitionStep.SPAWN : tick == 60 ? TransitionStep.OPEN
					: TransitionStep.NONE, step);
		}
		assertEquals(Stage.ANIMAL, session.stage());
		assertTrue(session.startPetting(20, 21, 200));
		assertTrue(session.completePetting(20, 21, 260));
		assertEquals(Outcome.REWARDED, session.claimReward());
		assertEquals(Outcome.REJECTED, session.claimReward());
		assertEquals(Outcome.REJECTED, session.finish(20, 21, false, false));
		assertEquals(Stage.CLOSED, session.stage());
	}

	@Test
	void questionCannotSkipToPhase4PettingOrReward() {
		Session session = question();
		assertFalse(session.beginPhase4(10));
		assertFalse(session.startPetting(10, 0, 0));
		assertFalse(session.completePetting(10, 0, 1000));
		assertEquals(Outcome.REJECTED, session.claimReward());
		assertEquals(Stage.QUESTION, session.stage());
	}

	@Test
	void wrongActorCannotAdvanceCurrentStory() {
		Session session = question();
		assertFalse(session.advanceToPhase2(999));
		assertFalse(session.beginPhase4(999));
		assertEquals(Outcome.REJECTED, session.finish(999, 0, false, false));
		assertEquals(Stage.QUESTION, session.stage());
	}

	@Test
	void duplicateAdvanceDoesNotRestartTransitionTimer() {
		Session session = question();
		assertTrue(session.advanceToPhase2(10));
		assertFalse(session.advanceToPhase2(10));
		assertTrue(session.beginPhase4(10));
		for (int tick = 1; tick < 5; tick++) {
			assertFalse(session.beginPhase4(10));
			assertEquals(TransitionStep.NONE, session.tickTransition());
		}
		assertEquals(TransitionStep.REMOVE_OLD, session.tickTransition());
	}

	@Test
	void missingActorGraceOnlyCoversTheOriginalTransitionGap() {
		Session session = question();
		session.advanceToPhase2(10);
		session.beginPhase4(10);
		for (int tick = 1; tick <= 20; tick++) {
			session.tickTransition();
			assertEquals(tick >= 5 && tick < 20, session.actorsTemporarilyAbsent());
		}
	}

	@Test
	void actorReplacementIsOnlyAllowedAtSpawnStepAndOnce() {
		Session session = question();
		assertFalse(session.bindReplacement(replacement, dog));
		session.advanceToPhase2(10);
		session.beginPhase4(10);
		for (int tick = 1; tick < 20; tick++) session.tickTransition();
		assertFalse(session.bindReplacement(replacement, dog));
		session.tickTransition();
		assertTrue(session.bindReplacement(replacement, dog));
		assertFalse(session.bindReplacement(original, dog));
		assertEquals(replacement, session.purple());
	}

	@Test
	void incompleteTransitionNeverEnablesAnimalChoiceOrReward() {
		Session session = question();
		session.advanceToPhase2(10);
		session.beginPhase4(10);
		for (int tick = 0; tick < 65; tick++) {
			assertNotEquals(TransitionStep.OPEN, session.tickTransition());
		}
		assertEquals(Stage.TRANSITION, session.stage());
		assertFalse(session.startPetting(10, 0, 1000));
		assertEquals(Outcome.REJECTED, session.claimReward());
	}

	@Test
	void oldActorIdsCannotControlReplacementActors() {
		Session session = question();
		session.advanceToPhase2(10);
		session.beginPhase4(10);
		for (int tick = 1; tick <= 60; tick++) {
			session.tickTransition();
			if (tick == 20) session.bindReplacement(replacement, dog);
		}
		assertFalse(session.startPetting(10, 21, 100));
		assertEquals(Outcome.REJECTED, session.finish(10, 21, false, false));
		assertTrue(session.startPetting(20, 21, 100));
	}

	@Test
	void bothActorIdsMustMatchIncludingDogNotBeingZero() {
		Session session = animal();
		assertFalse(session.startPetting(20, 0, 0));
		assertFalse(session.startPetting(20, 10, 0));
		assertFalse(session.startPetting(21, 20, 0));
		assertEquals(Outcome.REJECTED, session.finish(20, 0, false, false));
		assertEquals(Stage.ANIMAL, session.stage());
	}

	@Test
	void completionRequiresAnAcceptedPettingStart() {
		Session session = animal();
		assertFalse(session.completePetting(20, 21, 100000));
		assertEquals(Outcome.REJECTED, session.claimReward());
		assertEquals(Stage.ANIMAL, session.stage());
	}

	@Test
	void earlyOrRewoundClockCompletionDoesNotAdvanceButCanRetryLater() {
		Session session = animal();
		session.startPetting(20, 21, 100);
		assertFalse(session.completePetting(20, 21, 99));
		assertFalse(session.completePetting(20, 21, 159));
		assertEquals(Stage.PETTING, session.stage());
		assertTrue(session.completePetting(20, 21, 160));
		assertEquals(Stage.FINAL, session.stage());
	}

	@Test
	void duplicatePettingStartDoesNotResetClock() {
		Session session = animal();
		assertTrue(session.startPetting(20, 21, 100));
		assertFalse(session.startPetting(20, 21, 159));
		assertTrue(session.completePetting(20, 21, 160));
	}

	@Test
	void repeatedCompletionCannotDropHeldItemTwice() {
		Session session = animal();
		session.startPetting(20, 21, 0);
		assertTrue(session.completePetting(20, 21, 60));
		assertFalse(session.completePetting(20, 21, 61));
		assertFalse(session.startPetting(20, 21, 62));
	}

	@Test
	void wrongPettingActorsDoNotConsumeCorrectCompletion() {
		Session session = animal();
		session.startPetting(20, 21, 100);
		assertFalse(session.completePetting(20, 999, 160));
		assertFalse(session.completePetting(999, 21, 160));
		assertTrue(session.completePetting(20, 21, 160));
	}

	@Test
	void rewardCannotBeClaimedDuringPetting() {
		Session session = animal();
		session.startPetting(20, 21, 0);
		assertEquals(Outcome.REJECTED, session.claimReward());
		assertEquals(Stage.PETTING, session.stage());
	}

	@Test
	void onlyAnimalStageAllowsTheExactKillChoice() {
		Session session = animal();
		assertEquals(Outcome.REJECTED, session.finish(20, 21, true, false));
		assertEquals(Outcome.REJECTED, session.finish(20, 21, false, true));
		assertEquals(Outcome.KILL_PLAYER, session.finish(20, 21, true, true));
		assertEquals(Outcome.REJECTED, session.finish(20, 21, true, true));
		assertEquals(Outcome.REJECTED, session.claimReward());
	}

	@Test
	void killChoiceInEarlyOrFinalStagesIsRejected() {
		Session early = question();
		assertEquals(Outcome.REJECTED, early.finish(10, 0, true, true));
		sessions.close(player);
		Session finalStage = finale();
		assertEquals(Outcome.REJECTED, finalStage.finish(20, 21, true, true));
		assertEquals(Stage.FINAL, finalStage.stage());
	}

	@Test
	void decliningEarlyDoesNotMarkAssimilationOrReward() {
		Session session = question();
		assertEquals(Outcome.CANCELLED, session.finish(10, 0, false, false));
		assertEquals(Outcome.REJECTED, session.claimReward());
	}

	@Test
	void finalDeclineAssimilatesWithoutRewardAndCannotClaimAfterwards() {
		Session session = finale();
		assertEquals(Outcome.ASSIMILATED, session.finish(20, 21, false, false));
		assertEquals(Outcome.REJECTED, session.claimReward());
	}

	@Test
	void endingOnePlayerDoesNotDeleteAnotherPlayersStory() {
		Session first = question();
		UUID other = UUID.randomUUID();
		Session second = sessions.open(other, "minecraft:overworld", replacement, dog, Stage.ANIMAL);
		assertSame(first, sessions.close(player));
		assertNull(sessions.get(player));
		assertEquals(Stage.CLOSED, first.stage());
		assertSame(second, sessions.get(other));
		assertEquals(Stage.ANIMAL, second.stage());
		assertTrue(second.startPetting(20, 21, 0));
	}

	@Test
	void logoutOrDeathClosesOldSessionBeforeReopeningWithNewActors() {
		Session old = animal();
		old.startPetting(20, 21, 0);
		sessions.close(player);
		Session next = question();
		assertFalse(old.completePetting(20, 21, 1000));
		assertEquals(Outcome.REJECTED, old.claimReward());
		assertEquals(Outcome.REJECTED, next.finish(20, 21, false, false));
		assertEquals(Stage.QUESTION, next.stage());
	}

	@Test
	void serverShutdownClosesEverySessionAndLeavesNoReusableState() {
		Session first = question();
		Session second = sessions.open(UUID.randomUUID(), "mcanomalyarchives:cloud_dem",
				replacement, dog, Stage.ANIMAL);
		List<Session> closed = sessions.closeAll();
		assertEquals(2, closed.size());
		assertTrue(sessions.active().isEmpty());
		assertEquals(Stage.CLOSED, first.stage());
		assertEquals(Stage.CLOSED, second.stage());
		assertEquals(TransitionStep.NONE, first.tickTransition());
		assertTrue(new PurpleStorySessions().active().isEmpty());
	}

	@Test
	void duplicateOpeningAndDirectFinalOpeningAreRejected() {
		question();
		assertThrows(IllegalStateException.class, () ->
				sessions.open(player, "minecraft:overworld", replacement, dog, Stage.ANIMAL));
		assertThrows(IllegalArgumentException.class, () ->
				sessions.open(UUID.randomUUID(), "minecraft:overworld", replacement, dog, Stage.FINAL));
		assertEquals(Stage.QUESTION, sessions.get(player).stage());
	}

	@Test
	void actorAndOpeningStageInvariantsAreValidated() {
		assertThrows(IllegalArgumentException.class, () -> new Actor(0, UUID.randomUUID()));
		assertThrows(NullPointerException.class, () -> new Actor(1, null));
		assertThrows(IllegalArgumentException.class, () ->
				sessions.open(player, "minecraft:overworld", original, null, Stage.ANIMAL));
		assertThrows(IllegalArgumentException.class, () ->
				sessions.open(player, "minecraft:overworld", original, original, Stage.ANIMAL));
		assertThrows(IllegalArgumentException.class, () -> sessions.open(player, "minecraft:overworld",
				original, new Actor(11, original.uuid()), Stage.ANIMAL));
		assertThrows(IllegalArgumentException.class, () ->
				sessions.open(player, "minecraft:overworld", original, dog, Stage.QUESTION));
	}

	@Test
	void registrySnapshotSupportsCleanupWhileIterating() {
		question();
		sessions.open(UUID.randomUUID(), "minecraft:overworld", replacement, dog, Stage.ANIMAL);
		List<UUID> removed = new ArrayList<>();
		for (Session session : sessions.active()) {
			sessions.close(session.player());
			removed.add(session.player());
		}
		assertEquals(2, removed.size());
		assertTrue(sessions.active().isEmpty());
	}
}
