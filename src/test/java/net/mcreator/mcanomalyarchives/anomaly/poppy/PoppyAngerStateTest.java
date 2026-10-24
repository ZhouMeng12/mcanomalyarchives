package net.mcreator.mcanomalyarchives.anomaly.poppy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;

class PoppyAngerStateTest {
    private final PoppyAngerState state = new PoppyAngerState();
    private final GlobalPos overworld = GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, 10));
    private final GlobalPos nether = GlobalPos.of(Level.NETHER, new BlockPos(10, 64, 10));

    @Test
    void sameCoordinatesInDifferentDimensionsHaveIndependentTimers() {
        state.watch(overworld, 100);
        state.watch(nether, 200);
        state.becomeAngry(overworld, 2500);
        assertEquals(100, state.lastWatched(overworld));
        assertEquals(200, state.lastWatched(nether));
        assertEquals(2500L, state.angryUntil(overworld));
        assertNull(state.angryUntil(nether));
        state.remove(nether);
        assertTrue(state.wasWatched(overworld));
    }

    @Test
    void changingToSadPoppyPreservesRecoveryTimer() {
        state.watch(overworld, 100);
        state.becomeAngry(overworld, 2500);
        state.onReplacement(overworld, true);
        assertTrue(state.wasWatched(overworld));
        assertEquals(2500L, state.angryUntil(overworld));
    }

    @Test
    void destroyingPoppyRemovesBothTimers() {
        state.watch(overworld, 100);
        state.becomeAngry(overworld, 2500);
        state.onReplacement(overworld, false);
        assertFalse(state.wasWatched(overworld));
        assertNull(state.angryUntil(overworld));
        assertTrue(state.watchTimes().isEmpty());
    }

    @Test
    void watchingAgainCalmsOnlyThatFlower() {
        state.watch(overworld, 100);
        state.watch(nether, 100);
        state.becomeAngry(overworld, 2500);
        state.becomeAngry(nether, 2500);
        state.watch(overworld, 1800);
        assertNull(state.angryUntil(overworld));
        assertEquals(1800, state.lastWatched(overworld));
        assertEquals(2500L, state.angryUntil(nether));
    }

    @Test
    void stoppingSessionDropsAllState() {
        state.watch(overworld, 100);
        state.becomeAngry(overworld, 2500);
        state.clear();
        assertFalse(state.wasWatched(overworld));
        assertNull(state.angryUntil(overworld));
    }

    @Test
    void saveReloadKeepsBothDimensionAndRecoveryDeadline() {
        state.watch(overworld, 100);
        state.watch(nether, 200);
        state.becomeAngry(overworld, 2500);
        PoppyAngerState restored = PoppyAngerState.load(state.save());
        assertEquals(100, restored.lastWatched(overworld));
        assertEquals(200, restored.lastWatched(nether));
        assertEquals(2500L, restored.angryUntil(overworld));
        assertNull(restored.angryUntil(nether));
    }

    @Test
    void iterationSnapshotSurvivesBlockRemovalDuringProcessing() {
        state.watch(overworld, 100);
        state.watch(nether, 200);
        var snapshot = state.watchTimes();
        for (GlobalPos key : snapshot.keySet()) state.remove(key);
        assertEquals(2, snapshot.size());
        assertTrue(state.watchTimes().isEmpty());
    }
}
