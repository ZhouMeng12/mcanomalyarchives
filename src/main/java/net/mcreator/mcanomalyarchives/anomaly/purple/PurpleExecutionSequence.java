package net.mcreator.mcanomalyarchives.anomaly.purple;

/** One authoritative hit, followed by withdrawal. Cancellation is terminal. */
public final class PurpleExecutionSequence {
    public enum Step { NONE, HIT, END }
    public static final int HIT_TICK = 22, END_TICK = 36;
    private int ticks;
    private boolean closed;
    public int ticks() { return ticks; }
    public boolean hit() { return ticks >= HIT_TICK; }
    public Step tick() {
        if (closed) return Step.NONE;
        ticks++;
        if (ticks == HIT_TICK) return Step.HIT;
        if (ticks >= END_TICK) { closed = true; return Step.END; }
        return Step.NONE;
    }
    public void cancel() { closed = true; }
    public static double extension(double tick) {
        return tick < 10 ? 0 : tick <= HIT_TICK ? Math.min(1, (tick - 10) / 12)
                : Math.max(0, (END_TICK - tick) / 14);
    }
}
