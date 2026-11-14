package net.mcreator.mcanomalyarchives.anomaly.cloud;

/** Server-owned delayed flight response. Repeated hits cannot extend the flight indefinitely. */
public final class CloudEscapeMotion {
    public static final int REACTION_TICKS = 10, FLIGHT_TICKS = 100, RECOVERY_TICKS = 40;
    private int age = -1;
    private double x = 1, z;

    public boolean disturb(double awayX, double awayZ) {
        if (age >= 0 || !Double.isFinite(awayX) || !Double.isFinite(awayZ)) return false;
        if (Math.abs(awayX) >= Math.abs(awayZ)) {
            x = awayX < 0 ? -1 : 1; z = 0;
        } else {
            x = 0; z = awayZ < 0 ? -1 : 1;
        }
        age = 0;
        return true;
    }

    public void tick() {
        if (age >= 0 && ++age >= REACTION_TICKS + FLIGHT_TICKS + RECOVERY_TICKS) age = -1;
    }

    public boolean fleeing() { return age >= REACTION_TICKS && age < REACTION_TICKS + FLIGHT_TICKS; }
    public boolean reacting() { return age >= 0 && age < REACTION_TICKS; }
    public double multiplier() { return fleeing() ? 4.0 : 1.0; }
    public double x() { return x; }
    public double z() { return z; }
}
