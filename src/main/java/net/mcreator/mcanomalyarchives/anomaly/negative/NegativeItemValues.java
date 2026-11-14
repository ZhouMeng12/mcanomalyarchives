package net.mcreator.mcanomalyarchives.anomaly.negative;

/** Signed anomaly values backed by valid positive vanilla stacks and persistent data. */
public final class NegativeItemValues {
    private NegativeItemValues() {}
    public static String countLabel(int magnitude) { return Integer.toString(-Math.max(0,magnitude)); }
    public static int afterUse(int remaining) { return remaining>=-1?0:remaining+1; }
    public static int vanillaDamage(int maxDamage,int remaining) {
        return (int)Math.min(Integer.MAX_VALUE,(long)maxDamage-Math.min(0,remaining));
    }
    public static int foodAfter(int before,int nutrition) { return Math.max(0,before-nutrition); }
    public static float saturationAfter(float before,float nutrition,int food) {
        return Math.min(food,Math.max(0,before-nutrition));
    }
}
