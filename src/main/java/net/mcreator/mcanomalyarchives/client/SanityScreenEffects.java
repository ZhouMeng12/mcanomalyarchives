package net.mcreator.mcanomalyarchives.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

public class SanityScreenEffects {

    private static final RandomSource RANDOM = RandomSource.create();
    private static int audioTimer = 0;
    private static int nextAudioInterval = 200;
    private static int noiseTimer = 0;

    // Vignette strength (0.0 to 1.0)
    public static float vignetteStrength = 0.0f;
    // Desaturation strength (0.0 to 1.0)
    public static float desaturationStrength = 0.0f;
    // Daze pulse (0.0 to 1.0) — trance-like breathing flicker for desaturation overlay
    public static float dazePulse = 0.0f;
    // Noise intensity (0.0 to 1.0) - only when wearing detector
    public static float noiseIntensity = 0.0f;
    // Noise density multiplier for dynamic changes
    public static float noiseDensity = 1.0f;

    public static void update(int sanity, Minecraft mc, LocalPlayer player, boolean wearingDetector) {
        float sanityFraction = sanity / 100.0f;

        // Vignette: appears below 75
        if (sanity <= 74) {
            vignetteStrength = mapRange(sanity, 0, 74, 0.0f, 1.0f);
            // Low-frequency flicker at 50-74
            if (sanity >= 50) {
                if (RANDOM.nextFloat() < 0.05f) {
                    vignetteStrength *= 0.5f + RANDOM.nextFloat() * 0.5f;
                }
            }
        } else {
            vignetteStrength = 0.0f;
        }

        // Desaturation: appears below 75
        if (sanity <= 74) {
            desaturationStrength = mapRange(sanity, 0, 74, 0.0f, 1.0f);
        } else {
            desaturationStrength = 0.0f;
        }

        // Daze pulse: breathing-like flicker for desaturation overlay, strongest at low sanity
        if (sanity <= 49) {
            float intensity = mapRange(sanity, 0, 49, 0.0f, 1.0f);
            // Slow sine wave (周期 ~4秒) + random jitter for organic trance feel
            float base = (float) (Math.sin(System.currentTimeMillis() * 0.0015) * 0.5 + 0.5);
            float jitter = RANDOM.nextFloat() * 0.3f;
            dazePulse = Math.clamp(base * intensity + jitter * intensity * 0.5f, 0f, 1f);
        } else {
            dazePulse = 0.0f;
        }

        // Audio hallucinations: tiered system
        if (sanity <= 49 && sanity > 24) {
            // Moderate (25-49): low-frequency ambient whispers
            audioTimer++;
            float intensity = mapRange(sanity, 24, 49, 0.0f, 1.0f);
            nextAudioInterval = (int) (300 + (1.0f - intensity) * 300); // 15-30s

            if (audioTimer >= nextAudioInterval) {
                audioTimer = 0;
                if (mc.player != null) {
                    SoundEvent sound = RANDOM.nextBoolean()
                        ? SoundEvents.AMBIENT_CAVE.value()
                        : SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value();
                    float volume = 0.1f + intensity * 0.15f;
                    mc.player.playSound(sound, volume, 0.5f + RANDOM.nextFloat());
                }
            }
        } else if (sanity <= 24) {
            // Severe (0-24): frequent hallucinations + guardian ambient
            audioTimer++;
            float intensity = mapRange(sanity, 0, 24, 0.0f, 1.0f);
            nextAudioInterval = (int) (200 + (1.0f - intensity) * 200); // 10-20s

            if (audioTimer >= nextAudioInterval) {
                audioTimer = 0;
                if (mc.player != null) {
                    SoundEvent sound = switch (RANDOM.nextInt(3)) {
                        case 0 -> SoundEvents.AMBIENT_CAVE.value();
                        case 1 -> SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value();
                        default -> SoundEvents.ELDER_GUARDIAN_AMBIENT;
                    };
                    float volume = 0.2f + intensity * 0.2f;
                    mc.player.playSound(sound, volume, 0.5f + RANDOM.nextFloat());
                }
            }
        } else {
            audioTimer = 0;
        }

        // Noise effect: only when wearing detector and sanity <= 50
        if (wearingDetector && sanity <= 49) {
            float targetIntensity = mapRange(sanity, 0, 49, 0.0f, 1.0f);
            
            noiseTimer++;
            if (noiseTimer >= 20) {
                noiseTimer = 0;
                
                float flicker = 0.95f + RANDOM.nextFloat() * 0.1f;
                float densityVariation = 0.95f + RANDOM.nextFloat() * 0.1f;
                
                noiseDensity = densityVariation;
                noiseIntensity = noiseIntensity + (targetIntensity * flicker - noiseIntensity) * 0.5f;
            }
        } else {
            noiseIntensity = 0.0f;
            noiseDensity = 1.0f;
            noiseTimer = 0;
        }
    }

    private static float mapRange(float value, float inMin, float inMax, float outMax, float outMin) {
        float fraction = (value - inMin) / (inMax - inMin);
        fraction = Math.clamp(fraction, 0.0f, 1.0f);
        return outMin + (outMax - outMin) * fraction;
    }
}
