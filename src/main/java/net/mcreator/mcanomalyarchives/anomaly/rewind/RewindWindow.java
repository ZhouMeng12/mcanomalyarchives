package net.mcreator.mcanomalyarchives.anomaly.rewind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** A bounded contiguous recording, shared by the server recorder and its regression tests. */
public final class RewindWindow<T> {
    public static final int HISTORY_TICKS = 200;
    public static final int SAMPLE_TICKS = 5;
    public record Sample<T>(long tick, T state) { }
    private final ArrayDeque<Sample<T>> samples = new ArrayDeque<>();

    public void add(long tick, T state) {
        if (!samples.isEmpty() && (tick <= samples.getLast().tick() || tick - samples.getLast().tick() > SAMPLE_TICKS)) clear();
        samples.addLast(new Sample<>(tick, state));
        // Extra insertion samples must not evict ten seconds of history merely by sample count.
        while (!samples.isEmpty() && samples.getFirst().tick() < tick - HISTORY_TICKS - SAMPLE_TICKS) samples.removeFirst();
    }

    public List<Sample<T>> lastTenSeconds(long now) {
        List<Sample<T>> result = new ArrayList<>();
        Sample<T> start = null;
        for (Sample<T> sample : samples) {
            if (sample.tick() <= now - HISTORY_TICKS) start = sample;
        }
        if (start == null || now - start.tick() > HISTORY_TICKS + SAMPLE_TICKS
                || samples.getLast().tick() != now) return List.of();
        for (Sample<T> sample : samples) if (sample.tick() >= start.tick()) result.add(sample);
        return List.copyOf(result);
    }

    /** Use the available contiguous past when a full ten seconds has not yet been recorded. */
    public List<Sample<T>> availableHistory(long now) {
        var full = lastTenSeconds(now);
        if (!full.isEmpty()) return full;
        if (samples.size() < 2 || samples.getLast().tick() != now
                || now - samples.getFirst().tick() > HISTORY_TICKS + SAMPLE_TICKS) return List.of();
        return List.copyOf(samples);
    }

    public void clear() { samples.clear(); }
}
