package net.mcreator.mcanomalyarchives.client.hud;

/**
 * 电量可视化 - 将FE能量值映射为4段显示格数。
 * 纯逻辑，无 Minecraft 依赖。
 */
public class EnergyVisuals {

    public static final int MAX_ENERGY = 12000;
    public static final int TOTAL_SEGMENTS = 4;
    public static final int ENERGY_PER_SEGMENT = MAX_ENERGY / TOTAL_SEGMENTS; // 3000

    /**
     * @param energy    当前FE值
     * @param maxEnergy 最大FE值
     * @return 应该点亮的格数 (0-4)
     */
    public static int getSegmentCount(int energy, int maxEnergy) {
        if (energy <= 0) return 0;
        if (energy >= maxEnergy) return TOTAL_SEGMENTS;
        // ceiling division: (energy + segmentSize - 1) / segmentSize
        int segmentSize = maxEnergy / TOTAL_SEGMENTS;
        return Math.min((energy + segmentSize - 1) / segmentSize, TOTAL_SEGMENTS);
    }
}
