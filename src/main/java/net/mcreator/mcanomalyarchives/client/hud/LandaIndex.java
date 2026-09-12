package net.mcreator.mcanomalyarchives.client.hud;

/**
 * 兰达指数（上瘾度）显示值计算。
 * 纯逻辑，无 Minecraft 依赖。
 */
public class LandaIndex {

    public static final double MAX_LANDA = 7.0;

    /**
     * @param effectAmplifier 药水效果 amplifier (amplifier = 实际等级-1)。
     *                        传入 0 表示无效果。
     * @return 显示的兰达指数，范围 1.0-7.0；传入 0 返回 -1.0（表示不显示）。
     */
    public static double fromEffect(int effectLevel) {
        if (effectLevel <= 0) return -1.0;
        return Math.min(effectLevel, MAX_LANDA);
    }
}
