package net.mcreator.mcanomalyarchives.client.hud;

/**
 * 诺德指数解析器 - 将理智值映射为7档中文文本和颜色。
 * 纯逻辑，无 Minecraft 依赖。
 */
public class NordIndexResolver {

    public static final int COLOR_GREEN = 0xFF99FF00;
    public static final int COLOR_YELLOW = 0xFFFFCC00;
    public static final int COLOR_RED = 0xFFFF3333;
    public static final int COLOR_RED_BLINK = 0xFFFF4444;

    private static final int[][] LEVELS = {
        // {min, isYellow, isRed, isBlink}
        {90, 0, 0, 0},  // 极高 - green
        {75, 0, 0, 0},  // 较高 - green
        {60, 0, 0, 0},  // 正常 - green
        {45, 1, 0, 0},  // 较低 - yellow
        {30, 1, 0, 0},  // 低 - yellow
        {15, 1, 1, 0},  // 警戒 - red
        {0,  1, 1, 1},  // 危险 - red blink
    };

    private static final String[] NAMES = {
        "极高", "较高", "正常", "较低", "低", "极低", "危险"
    };

    public static String getText(int sanity) {
        return NAMES[getLevelIndex(sanity)];
    }

    public static int getColor(int sanity) {
        int idx = getLevelIndex(sanity);
        int[] level = LEVELS[idx];
        if (level[3] == 1) return COLOR_RED_BLINK;
        if (level[2] == 1) return COLOR_RED;
        if (level[1] == 1) return COLOR_YELLOW;
        return COLOR_GREEN;
    }

    public static int getLevelIndex(int sanity) {
        for (int i = 0; i < LEVELS.length; i++) {
            if (sanity >= LEVELS[i][0]) return i;
        }
        return LEVELS.length - 1;
    }
}
