package net.mcreator.mcanomalyarchives.anomaly.nametag;

public final class NamedMaterialRules {
    public static final int TOOL_USES = 5;
    public static final int POTATO_TICKS = 1200;
    public static final int LIQUID_STEP_TICKS = 100;
    public static final int LIQUID_LAST_AGE = 131;
    private NamedMaterialRules() { }
    public static int nextLiquidAge(int age) { return age >= LIQUID_LAST_AGE ? -1 : age + 1; }
}
