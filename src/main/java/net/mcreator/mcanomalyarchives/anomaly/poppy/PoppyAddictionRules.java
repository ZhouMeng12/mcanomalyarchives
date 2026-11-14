package net.mcreator.mcanomalyarchives.anomaly.poppy;

/** Shared progression and geometry; all times are game ticks, never wall time. */
public final class PoppyAddictionRules {
    private PoppyAddictionRules() {}
    public static final int TRIGGER = 200;
    public static final int EXECUTION_END = 2200;
    public static int level(int watchingTicks) {
        return Math.max(0, Math.min(10, watchingTicks / 200));
    }
    public static float intensity(int watchingTicks) {
        return Math.max(0, Math.min(10, watchingTicks / 200f));
    }
    public static boolean lethal(int watchingTicks) { return watchingTicks >= EXECUTION_END; }
    public static boolean hardGaze(int level) { return level >= 4; }
    public record Progress(int ticks, int away, boolean withdrawal) {}
    public static Progress advance(int ticks, int away, boolean looking, int elapsed) {
        if (looking) return new Progress(ticks + elapsed, 0, false);
        if (level(ticks) == 0) return new Progress(0, 0, false);
        int lost = away + elapsed;
        return lost >= 20 ? new Progress(0, 0, true) : new Progress(ticks, lost, false);
    }
    public static double height(float intensity) { return 0.8 + Math.min(9, intensity) * 0.55; }
    public static double width(float intensity) { return 0.8 + Math.min(10, intensity) * 0.16; }
    public static double lean(float intensity) { return Math.max(0, intensity - 5) * 0.18; }
    public static double bend(float intensity) {
        double t = Math.max(0, Math.min(1, (intensity - 3) / 7.0));
        return t * t * (3 - 2 * t);
    }
    public static double headHeight(float intensity) { return height(intensity) * (0.86 - 0.22 * bend(intensity)); }
    public static double headSide(float intensity) { return -0.12 * height(intensity) * bend(intensity); }
    public static float scare(int ticks) { return Math.max(0, Math.min(1, (ticks - 2160) / 35f)); }
    public static float approachAngle(float current, float target, float maxStep) {
        float delta = (target - current) % 360;
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        return current + Math.max(-maxStep, Math.min(maxStep, delta));
    }
}
