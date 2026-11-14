package net.mcreator.mcanomalyarchives.anomaly.negative;

public final class NegativeSpaceLifetime {
    public static final int STEP_TICKS = 100;
    public static final int LAST_AGE = 5;
    private NegativeSpaceLifetime() { }
    /** -1 means remove; six scheduled steps total exactly 600 ticks. */
    public static int nextAge(int age) { return age >= LAST_AGE ? -1 : age + 1; }
}
