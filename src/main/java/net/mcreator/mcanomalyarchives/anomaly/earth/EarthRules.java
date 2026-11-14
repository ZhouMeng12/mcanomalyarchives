package net.mcreator.mcanomalyarchives.anomaly.earth;

/** Gameplay scale, not a simulation of planetary gravity. */
public final class EarthRules {
    public static final int FINAL_RADIUS = 128, DEPTH = 72;
    public static final int FIELD_RADIUS = 256, BLAST_AGE = 600, END_AGE = 800;
    public static final int BLOCK_BUDGET = 4096, SCAN_BUDGET = 32768, REWIND_LIMIT = 1048576;
    public static final int REWIND_PENDING_LIMIT = 32768;
    public static final int COLUMN_START = Integer.MAX_VALUE;
    public static final float EXPLOSION_POWER = 32;
    private EarthRules() { }
    public static int radius(int phase) { return FINAL_RADIUS; }
    public static int bottom(int x, int z, int phase) {
        int radius = radius(phase);
        double fraction = 1 - (x * (double)x + z * (double)z) / (radius * (double)radius);
        return -(int)Math.floor(Math.max(0, fraction) * DEPTH);
    }
    public static boolean canCollapse(int phase, int age) { return false; /* Visual distortion only; never excavate terrain. */ }
    public static net.minecraft.world.phys.Vec3 releaseVelocity(net.minecraft.world.phys.Vec3 velocity, net.minecraft.world.phys.Vec3 toCenter) {
        var inward = toCenter.normalize();
        return velocity.subtract(inward.scale(Math.max(0, velocity.dot(inward))));
    }
    public static float visualRadius(float age) {
        float t=Math.max(0,Math.min(1,(age-60f)/480f));
        return 10+(FIELD_RADIUS-10)*t*t*(3-2*t);
    }
    public static float warpStrength(float age) {
        return age < BLAST_AGE ? Math.min(1, Math.max(0, age / 600f)) : 0;
    }
    public static double pull(double distance, int age) {
        if (distance < .2 || distance >= FIELD_RADIUS || age >= BLAST_AGE) return 0;
        return (.035 + .2 * Math.min(1, age / 600.0)) * Math.pow(1 - distance / FIELD_RADIUS, 2);
    }
}
