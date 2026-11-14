package net.mcreator.mcanomalyarchives.anomaly.rewind;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RewindWindowTest {
    @Test void shortRewindUsesAvailablePastButNeverStaleOrSingleFrame() {
        var window = new RewindWindow<Integer>();
        window.add(0, 0);
        assertTrue(window.availableHistory(0).isEmpty());
        for (int tick = 5; tick <= 60; tick += 5) window.add(tick, tick);
        assertEquals(0, window.availableHistory(60).getFirst().tick());
        assertEquals(60, window.availableHistory(60).getLast().tick());
        assertTrue(window.availableHistory(61).isEmpty());
        window.add(80, 80);
        assertTrue(window.availableHistory(80).isEmpty());
    }
    @Test void repeatedInsertionSamplesDoNotEvictValidHistory() {
        var window = new RewindWindow<Integer>();
        for (int tick = 0; tick <= 1000; tick++) window.add(tick, tick);
        assertEquals(800, window.lastTenSeconds(1000).getFirst().tick());
        assertEquals(201, window.lastTenSeconds(1000).size());
    }
    @Test void requiresCompleteFreshHistory() {
        var window = new RewindWindow<Integer>();
        for (int tick = 0; tick < 200; tick += 5) window.add(tick, tick);
        assertTrue(window.lastTenSeconds(195).isEmpty());
        window.add(200, 200);
        assertEquals(0, window.lastTenSeconds(200).getFirst().tick());
        assertTrue(window.lastTenSeconds(201).isEmpty());
    }
    @Test void boundedHistorySelectsPreviousSampleForOffGridInsertion() {
        var window = new RewindWindow<Integer>();
        for (int tick = 0; tick <= 1000; tick += 5) window.add(tick, tick);
        window.add(1003, 1003);
        var frames = window.lastTenSeconds(1003);
        assertEquals(800, frames.getFirst().tick());
        assertEquals(1003, frames.getLast().tick());
        assertTrue(frames.size() <= 43);
    }
    @Test void gapsClockResetAndExplicitResetInvalidateHistory() {
        var window = new RewindWindow<Integer>();
        for (int tick = 0; tick <= 200; tick += 5) window.add(tick, tick);
        window.add(210, 210);
        assertTrue(window.lastTenSeconds(210).isEmpty());
        window.add(0, 0);
        assertTrue(window.lastTenSeconds(0).isEmpty());
        window.clear();
        assertTrue(window.lastTenSeconds(0).isEmpty());
    }
}
