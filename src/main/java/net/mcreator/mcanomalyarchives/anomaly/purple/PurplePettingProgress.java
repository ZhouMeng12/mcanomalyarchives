package net.mcreator.mcanomalyarchives.anomaly.purple;

/** Client animation watchdog; the server enforces its own independent deadline. */
public final class PurplePettingProgress {
    private int ticks, missingTicks, stalledTicks, phase = -1;
    private double best = Double.POSITIVE_INFINITY;

    public boolean tick(int currentPhase, boolean actorPresent, double remainingDistance) {
        if (++ticks >= PurpleStorySessions.Session.MAX_PETTING_TICKS) return false;
        missingTicks = actorPresent ? 0 : missingTicks + 1;
        if (missingTicks >= 40) return false;
        if (!actorPresent) return true;
        if (phase != currentPhase) {
            phase = currentPhase;
            best = Double.POSITIVE_INFINITY;
            stalledTicks = 0;
        }
        if (phase != 2 && phase != 4) return true;
        if (!Double.isFinite(remainingDistance)) return false;
        if (remainingDistance < best - .01) {
            best = remainingDistance;
            stalledTicks = 0;
        } else stalledTicks++;
        return stalledTicks < 20;
    }
}
