package net.mcreator.mcanomalyarchives.client.hud;

import java.util.Random;

/**
 * 设备序列号生成器 - 大写字母+数字1-9。
 * 纯逻辑，无 Minecraft 依赖。
 */
public class SerialGenerator {

    private static final Random RNG = new Random();

    /**
     * @return 2字符序列号，如 "X7", "B3", "M1"
     */
    public static String generate() {
        char letter = (char) ('A' + RNG.nextInt(26));
        char digit = (char) ('1' + RNG.nextInt(9));
        return "" + letter + digit;
    }
}
