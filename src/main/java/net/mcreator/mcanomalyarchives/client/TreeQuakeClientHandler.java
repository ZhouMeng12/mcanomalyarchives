package net.mcreator.mcanomalyarchives.client;

import net.minecraft.world.phys.Vec3;

/** 怪树地震动：按真实毫秒计时（与帧率无关），正弦摆动，振幅随时间衰减 */
public final class TreeQuakeClientHandler {

    private static long shakeStartMs = 0;
    private static long shakeEndMs = 0;
    private static float shakeAmplitude = 0.4f;

    private TreeQuakeClientHandler() {
    }

    /** 触发一次震颤：amplitude 为初始振幅（格），durationTicks 为时长（tick） */
    public static void startShake(float amplitude, int durationTicks) {
        long now = System.currentTimeMillis();
        shakeStartMs = now;
        shakeEndMs = now + (long) durationTicks * 50L;
        shakeAmplitude = amplitude;
    }

    public static boolean isShaking() {
        return System.currentTimeMillis() < shakeEndMs;
    }

    /** 当前帧的抖动偏移：正弦摆动（更像地震摇晃），振幅随时间衰减 */
    public static Vec3 getOffset() {
        long now = System.currentTimeMillis();
        if (now >= shakeEndMs) return Vec3.ZERO;
        long total = shakeEndMs - shakeStartMs;
        if (total <= 0) return Vec3.ZERO;
        float strength = (float) (shakeEndMs - now) / (float) total;
        float amp = shakeAmplitude * strength;
        double t = now / 40.0;
        double x = Math.sin(t) * amp;
        double y = Math.sin(t * 1.7 + 2.0) * amp * 0.5;
        double z = Math.cos(t * 1.3) * amp;
        return new Vec3(x, y, z);
    }
}
