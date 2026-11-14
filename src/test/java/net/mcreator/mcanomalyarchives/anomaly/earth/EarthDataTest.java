package net.mcreator.mcanomalyarchives.anomaly.earth;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EarthDataTest {
    @Test void savedCursorAndAnchorSurviveRestart() {
        var id = UUID.randomUUID(); var anchor = UUID.randomUUID();
        EarthData data = new EarthData();
        data.set(new EarthData.State(id, anchor, new BlockPos(-60, 80, 140), 650, 1, 2345, -17, true, false, new int[]{4, 9}));
        var loaded = EarthData.load(data.save(new CompoundTag(), null), null).state;
        assertEquals(id, loaded.id()); assertEquals(anchor, loaded.anchor());
        assertEquals(650, loaded.age()); assertEquals(1, loaded.phase());
        assertEquals(2345, loaded.column()); assertEquals(-17, loaded.y());
        assertTrue(loaded.noAi()); assertFalse(loaded.noGravity());
        assertArrayEquals(new int[]{4,9}, loaded.deferred());
    }
    @Test void historyCannotBeMutatedThroughDeferredArray() {
        int[] deferred = {7};
        var s = new EarthData.State(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, 200, 0, 3, 8, false, false, deferred);
        deferred[0] = 8; s.deferred()[0] = 9;
        assertArrayEquals(new int[]{7}, s.deferred());
    }
    @Test void emptySavedDataDoesNotStartAnEvent() {
        assertNull(EarthData.load(new CompoundTag(), null).state);
    }
    @Test void tallColumnCursorDoesNotGetClampedBackToEightOnReload() {
        EarthData data = new EarthData();
        data.set(new EarthData.State(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, 650, 1, 900, 230, false, false, new int[]{4}));
        assertEquals(230, EarthData.load(data.save(new CompoundTag(), null), null).state.y());
        data.set(new EarthData.State(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, 650, 1, 900, EarthRules.COLUMN_START, false, false, new int[0]));
        assertEquals(EarthRules.COLUMN_START, EarthData.load(data.save(new CompoundTag(), null), null).state.y());
    }
    @Test void previousEightBlockScanResumesFromTheTopWithoutReplayingTheExplosion() {
        EarthData data = new EarthData();
        data.set(new EarthData.State(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, 650, 1, 900, 8, false, false, new int[]{4}));
        CompoundTag old = data.save(new CompoundTag(), null); old.remove("ScanVersion");
        var migrated = EarthData.load(old, null).state;
        assertEquals(1, migrated.phase()); assertEquals(650, migrated.age());
        assertEquals(0, migrated.column()); assertEquals(EarthRules.COLUMN_START, migrated.y());
        assertEquals(0, migrated.deferred().length);
    }
    @Test void preExplosionRebuildWaitSurvivesSaveWithoutRestartingTheScan() {
        EarthData data = new EarthData();
        data.set(new EarthData.State(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, 599, 2, 50000, 67, false, false, new int[0]));
        var state = EarthData.load(data.save(new CompoundTag(), null), null).state;
        assertEquals(2, state.phase()); assertEquals(67, state.y());
        assertFalse(EarthRules.canCollapse(state.phase(), state.age()));
    }
    @Test void shuffledVersionThreeCursorRestartsRegionalScanWithoutAnotherExplosion() {
        EarthData data = new EarthData();
        data.set(new EarthData.State(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, 930, 1, 40000, -12, false, false, new int[0]));
        CompoundTag old = data.save(new CompoundTag(), null);
        old.putInt("ScanVersion", 3);
        var migrated = EarthData.load(old, null).state;
        assertEquals(930, migrated.age()); assertEquals(1, migrated.phase());
        assertEquals(0, migrated.column()); assertEquals(EarthRules.COLUMN_START, migrated.y());
    }
}
