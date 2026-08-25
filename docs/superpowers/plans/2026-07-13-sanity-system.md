# Sanity System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a Cthulhu-style sanity system for NeoForge 1.21.1 using AttachmentType for persistent player data, event-driven sanity loss, HP-driven recovery, and client-side screen effects via Mixin.

**Architecture:** NeoForge AttachmentType stores sanity (0-100) on Player entities, synced to clients automatically. A server-side event handler listens for trigger events (dimension change, Sad effect, damage, fishing Tier3, CloudWater drinking, SadPoppy proximity). A recovery handler runs on PlayerTick with HP-ratio-driven recovery/drain and chair-based recovery. Two client-side files handle screen effects (vignette, desaturation, FOV jitter, audio, movement offset) injected via Mixin into LevelRenderer and GameRenderer.

**Tech Stack:** NeoForge 21.8.31, Minecraft 1.21.8, Java 21, Mixin 0.8, MCreator workspace conventions.

---

## File Structure

```
src/main/java/net/mcreator/strangerecord/
├── sanity/
│   └── PlayerSanity.java              # NEW: data class + Codec
├── init/
│   └── StrangerecordModAttachments.java # NEW: AttachmentType registration
├── events/
│   ├── SanityEventHandler.java         # NEW: event-driven sanity loss
│   ├── SanityRecoveryHandler.java      # NEW: HP-driven recovery, chair, brain-damage ticks
│   ├── PlayerLookAtCornPoppyListener.java # MODIFY: Sad effect → sanity loss
│   ├── CornPoppyAngryListener.java      # MODIFY: SadPoppy proximity → sanity loss
│   └── StrangeFishingRodLootHandler.java# MODIFY: Tier3 mob → sanity loss
├── client/
│   ├── SanityClientHandler.java        # NEW: client-side sanity cache + screen effects
│   └── SanityScreenEffects.java        # NEW: vignette, desaturation, FOV, audio, movement
├── item/
│   ├── CloudWaterButtleItem.java       # MODIFY: drinking → sanity loss
│   └── CloudWaterItem.java             # MODIFY: drinking → sanity loss
├── mixin/
│   └── LevelRendererMixin.java         # MODIFY: add vignette + desaturation injection
└── StrangerecordMod.java               # MODIFY: register AttachmentType
```

---

### Task 1: Create PlayerSanity data class

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/sanity/PlayerSanity.java`

- [ ] **Step 1: Write PlayerSanity.java**

```java
package net.mcreator.strangerecord.sanity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public class PlayerSanity {
    public static final int MAX_SANITY = 100;
    public static final int MIN_SANITY = 0;

    public static final Codec<PlayerSanity> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("sanity").forGetter(ps -> ps.sanity)
        ).apply(instance, PlayerSanity::new)
    );

    private int sanity;

    public PlayerSanity() {
        this.sanity = MAX_SANITY;
    }

    private PlayerSanity(int sanity) {
        this.sanity = Math.clamp(sanity, MIN_SANITY, MAX_SANITY);
    }

    public int getSanity() {
        return sanity;
    }

    public void setSanity(int value) {
        this.sanity = Math.clamp(value, MIN_SANITY, MAX_SANITY);
    }

    public void reduceSanity(int amount) {
        setSanity(this.sanity - amount);
    }

    public void increaseSanity(int amount) {
        setSanity(this.sanity + amount);
    }

    public void resetToMax() {
        this.sanity = MAX_SANITY;
    }
}
```

- [ ] **Step 2: Commit**

---

### Task 2: Register AttachmentType

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/init/StrangerecordModAttachments.java`
- Modify: `src/main/java/net/mcreator/strangerecord/StrangerecordMod.java`

- [ ] **Step 1: Write StrangerecordModAttachments.java**

```java
package net.mcreator.strangerecord.init;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.mcreator.strangerecord.StrangerecordMod;
import net.mcreator.strangerecord.sanity.PlayerSanity;

import java.util.function.Supplier;

public class StrangerecordModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, StrangerecordMod.MODID);

    public static final Supplier<AttachmentType<PlayerSanity>> PLAYER_SANITY =
        REGISTRY.register("player_sanity", () ->
            AttachmentType.builder(PlayerSanity::new)
                .serialize(PlayerSanity.CODEC)
                .build()
        );
}
```

- [ ] **Step 2: Modify StrangerecordMod.java — add import and registration**

In `StrangerecordMod.java`, add the import at the top:

```java
import net.mcreator.strangerecord.init.StrangerecordModAttachments;
```

In the constructor, add after the existing `StrangerecordModFluidTypes.REGISTRY.register(modEventBus);` line:

```java
StrangerecordModAttachments.REGISTRY.register(modEventBus);
```

- [ ] **Step 3: Commit**

---

### Task 3: Create SanityEventHandler — sanity decrease triggers

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/events/SanityEventHandler.java`
- Modify: `src/main/java/net/mcreator/strangerecord/StrangerecordMod.java`

- [ ] **Step 1: Write SanityEventHandler.java**

```java
package net.mcreator.strangerecord.events;

import net.neoforged.neoforge.event.entity.living.LivingHurtEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerPlayer;

import net.mcreator.strangerecord.init.StrangerecordModAttachments;
import net.mcreator.strangerecord.init.StrangerecordModMobEffects;
import net.mcreator.strangerecord.sanity.PlayerSanity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SanityEventHandler {

    // Cooldown trackers for per-player cooldowns (millis)
    private static final Map<UUID, Long> cloudDimensionCooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> sadPoppyCooldowns = new ConcurrentHashMap<>();
    private static final long DIMENSION_COOLDOWN_MS = 5 * 60 * 1000; // 5 min
    private static final long SAD_POPPY_COOLDOWN_MS = 30 * 1000; // 30 sec

    // Braindamage damage type key
    private static final ResourceKey<DamageType> BRAIN_DAMAGE_KEY = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath("strangerecord", "braindamage")
    );

    // Flag to mark brain-damage from low sanity (so it doesn't trigger more sanity loss)
    public static final String SANITY_BRAIN_DAMAGE_TAG = "sanity_brain_damage";

    public static void init() {
        NeoForge.EVENT_BUS.register(new SanityEventHandler());
    }

    // === Cloud Dimension entry ===
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String dimPath = event.getTo().location().getPath();
        if (!"cloud_dem".equals(dimPath)) return;

        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = cloudDimensionCooldowns.get(id);
        if (last != null && (now - last) < DIMENSION_COOLDOWN_MS) return;

        cloudDimensionCooldowns.put(id, now);
        PlayerSanity sanity = player.getData(StrangerecordModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(35);
    }

    // === Sad effect applied ===
    @SubscribeEvent
    public void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getEffectInstance().getEffect().equals(StrangerecordModMobEffects.SAD)) return;

        int level = event.getEffectInstance().getAmplifier() + 1;
        int loss = 8 * level;

        PlayerSanity sanity = player.getData(StrangerecordModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(loss);
    }

    // === Damage taken ===
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Skip damage caused by low-sanity brain-damage (avoid infinite loop)
        if (player.getPersistentData().getBoolean(SANITY_BRAIN_DAMAGE_TAG)) {
            player.getPersistentData().remove(SANITY_BRAIN_DAMAGE_TAG);
            return;
        }

        boolean isBrainDamage = event.getSource().is(BRAIN_DAMAGE_KEY);
        float damage = event.getAmount();

        PlayerSanity sanity = player.getData(StrangerecordModAttachments.PLAYER_SANITY);

        if (isBrainDamage) {
            sanity.reduceSanity(8);
        } else {
            int loss = Math.max(1, (int) Math.floor(damage / 2.0));
            sanity.reduceSanity(loss);
        }
    }

    // === SadPoppy proximity (called from CornPoppyAngryListener) ===
    public static void onSadPoppyProximity(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = sadPoppyCooldowns.get(id);
        if (last != null && (now - last) < SAD_POPPY_COOLDOWN_MS) return;

        sadPoppyCooldowns.put(id, now);
        PlayerSanity sanity = player.getData(StrangerecordModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(5);
    }

    // === Fishing Tier3 mob spawned (called from StrangeFishingRodLootHandler) ===
    public static void onFishingTier3(ServerPlayer player) {
        PlayerSanity sanity = player.getData(StrangerecordModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(20);
    }

    // === CloudWater drunk (called from CloudWaterButtleItem / CloudWaterItem) ===
    public static void onCloudWaterDrunk(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PlayerSanity sanity = serverPlayer.getData(StrangerecordModAttachments.PLAYER_SANITY);
        sanity.reduceSanity(25);
    }

    // === Death resets sanity ===
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        Player newPlayer = event.getEntity();
        if (!(newPlayer instanceof ServerPlayer)) return;
        PlayerSanity sanity = newPlayer.getData(StrangerecordModAttachments.PLAYER_SANITY);
        sanity.resetToMax();
    }
}
```

- [ ] **Step 2: Modify StrangerecordMod.java — add init call**

In the constructor's user code block, add:

```java
SanityEventHandler.init();
```

- [ ] **Step 3: Commit**

---

### Task 4: Create SanityRecoveryHandler — HP-driven recovery, chair, brain-damage ticks

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/events/SanityRecoveryHandler.java`
- Modify: `src/main/java/net/mcreator/strangerecord/StrangerecordMod.java`

- [ ] **Step 1: Write SanityRecoveryHandler.java**

```java
package net.mcreator.strangerecord.events;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import net.mcreator.strangerecord.init.StrangerecordModAttachments;
import net.mcreator.strangerecord.sanity.PlayerSanity;
import net.mcreator.strangerecord.entity.SittingEnityEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SanityRecoveryHandler {

    private static final ResourceKey<DamageType> BRAIN_DAMAGE_KEY = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath("strangerecord", "braindamage")
    );

    // Per-player tick counters (use ConcurrentHashMap for thread safety)
    private static final Map<UUID, Integer> recoveryTickCounters = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> chairTickCounters = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> brainDamageTickCounters = new ConcurrentHashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.register(new SanityRecoveryHandler());
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        UUID playerId = player.getUUID();
        PlayerSanity sanity = player.getData(StrangerecordModAttachments.PLAYER_SANITY);
        int currentSanity = sanity.getSanity();

        // === HP-driven recovery/drain ===
        float hpRatio = player.getHealth() / player.getMaxHealth();
        int recoveryInterval;
        boolean shouldDrain;

        if (hpRatio >= 0.8f) {
            recoveryInterval = 40; // +1 every 2s
            shouldDrain = false;
        } else if (hpRatio >= 0.5f) {
            recoveryInterval = 80; // +1 every 4s
            shouldDrain = false;
        } else if (hpRatio >= 0.2f) {
            recoveryInterval = 160; // +1 every 8s
            shouldDrain = false;
        } else {
            recoveryInterval = 100; // -1 every 5s
            shouldDrain = true;
        }

        int recoveryCounter = recoveryTickCounters.getOrDefault(playerId, 0) + 1;
        if (recoveryCounter >= recoveryInterval) {
            recoveryCounter = 0;
            if (shouldDrain) {
                sanity.reduceSanity(1);
            } else {
                sanity.increaseSanity(1);
            }
        }
        recoveryTickCounters.put(playerId, recoveryCounter);

        // === Chair recovery (independent of HP) ===
        if (player.getVehicle() instanceof SittingEnityEntity) {
            int chairCounter = chairTickCounters.getOrDefault(playerId, 0) + 1;
            if (chairCounter >= 40) { // every 2s
                chairCounter = 0;
                sanity.increaseSanity(3);
            }
            chairTickCounters.put(playerId, chairCounter);
        }

        // === Low sanity brain-damage ticks ===
        if (currentSanity <= 24) {
            int bdCounter = brainDamageTickCounters.getOrDefault(playerId, 0) + 1;
            if (bdCounter >= 200) { // every 10s
                bdCounter = 0;

                // Mark this damage so SanityEventHandler skips it
                player.getPersistentData().putBoolean(SanityEventHandler.SANITY_BRAIN_DAMAGE_TAG, true);
                DamageSource brainDamage = player.level().damageSources().source(BRAIN_DAMAGE_KEY);
                player.hurt(brainDamage, 1.0f);
            }
            brainDamageTickCounters.put(playerId, bdCounter);
        } else {
            brainDamageTickCounters.remove(playerId);
        }
    }
}
```

- [ ] **Step 2: Modify StrangerecordMod.java — add init call**

In the constructor's user code block, add:

```java
SanityRecoveryHandler.init();
```

- [ ] **Step 3: Commit**

---

### Task 5: Create SanityClientHandler — client-side sanity cache

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/client/SanityClientHandler.java`

- [ ] **Step 1: Write SanityClientHandler.java**

```java
package net.mcreator.strangerecord.client;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import net.mcreator.strangerecord.init.StrangerecordModAttachments;
import net.mcreator.strangerecord.sanity.PlayerSanity;

public class SanityClientHandler {

    private static int cachedSanity = PlayerSanity.MAX_SANITY;

    public static void init() {
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(new SanityClientHandler());
        }
    }

    public static int getCachedSanity() {
        return cachedSanity;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        PlayerSanity sanity = player.getData(StrangerecordModAttachments.PLAYER_SANITY);
        cachedSanity = sanity.getSanity();

        // Apply screen effects based on sanity
        SanityScreenEffects.update(cachedSanity, mc, player);
    }
}
```

- [ ] **Step 2: Commit**

---

### Task 6: Create SanityScreenEffects — all visual effects

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/client/SanityScreenEffects.java`

- [ ] **Step 1: Write SanityScreenEffects.java**

```java
package net.mcreator.strangerecord.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

public class SanityScreenEffects {

    private static final RandomSource RANDOM = RandomSource.create();
    private static int audioTimer = 0;
    private static int nextAudioInterval = 200; // 10s default

    // Vignette strength (0.0 to 1.0) — read by LevelRendererMixin
    public static float vignetteStrength = 0.0f;
    // Desaturation strength (0.0 to 1.0)
    public static float desaturationStrength = 0.0f;
    // FOV jitter amount
    public static float fovJitter = 0.0f;
    // Movement offset chance
    public static float movementOffsetChance = 0.0f;

    public static void update(int sanity, Minecraft mc, LocalPlayer player) {
        float sanityFraction = sanity / 100.0f;

        // Vignette: appears below 75, stronger as sanity drops
        if (sanity <= 74) {
            vignetteStrength = mapRange(sanity, 0, 74, 1.0f, 0.0f);
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
            desaturationStrength = mapRange(sanity, 0, 74, 1.0f, 0.0f);
        } else {
            desaturationStrength = 0.0f;
        }

        // FOV jitter: appears below 50
        if (sanity <= 49) {
            float baseStrength = mapRange(sanity, 0, 49, 1.0f, 0.0f);
            fovJitter = baseStrength * (float) Math.sin(System.currentTimeMillis() * 0.01) * 2.5f
                + RANDOM.nextFloat() * baseStrength * 1.5f;
        } else {
            fovJitter = 0.0f;
        }

        // Audio hallucinations: below 50
        if (sanity <= 49) {
            audioTimer++;
            float intensity = mapRange(sanity, 0, 49, 1.0f, 0.0f);
            nextAudioInterval = (int) (200 + (1.0f - intensity) * 400); // 10s-30s

            if (audioTimer >= nextAudioInterval) {
                audioTimer = 0;
                nextAudioInterval = 200 + RANDOM.nextInt(400);
                // Play low-volume sound
                if (mc.player != null) {
                    float volume = 0.1f + intensity * 0.2f;
                    mc.player.playSound(SoundEvents.AMBIENT_CAVE.get(), volume, 0.5f + RANDOM.nextFloat());
                }
            }
        } else {
            audioTimer = 0;
        }

        // Movement offset: only at 0-24
        if (sanity <= 24) {
            movementOffsetChance = mapRange(sanity, 0, 24, 1.0f, 0.0f) * 0.05f;
        } else {
            movementOffsetChance = 0.0f;
        }
    }

    private static float mapRange(float value, float inMin, float inMax, float outMax, float outMin) {
        float fraction = (value - inMin) / (inMax - inMin);
        fraction = Math.clamp(fraction, 0.0f, 1.0f);
        return outMin + (outMax - outMin) * fraction;
    }
}
```

- [ ] **Step 2: Commit**

---

### Task 7: Add Mixins for vignette, desaturation, FOV jitter, and movement offset

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/mixin/LevelRendererMixin.java`
- Create: `src/main/java/net/mcreator/strangerecord/mixin/GameRendererMixin.java`
- Create: `src/main/java/net/mcreator/strangerecord/mixin/MovementInputMixin.java`
- Modify: `src/main/resources/strangerecord.mixins.json`

- [ ] **Step 1: Modify LevelRendererMixin.java — add vignette + desaturation injection**

Add to the existing `LevelRendererMixin.java` (keeping all existing code):

```java
// Add these imports at top:
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.mcreator.strangerecord.client.SanityScreenEffects;

// Add this inject method inside the class:
@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V"))
private void injectSanityScreenEffects(CallbackInfo ci) {
    float vignette = SanityScreenEffects.vignetteStrength;
    float desaturation = SanityScreenEffects.desaturationStrength;

    if (vignette > 0.0f || desaturation > 0.0f) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        if (vignette > 0.0f) {
            renderVignette(vignette);
        }
        if (desaturation > 0.0f) {
            renderDesaturation(desaturation);
        }

        RenderSystem.disableBlend();
    }
}

private void renderVignette(float strength) {
    int width = mc.getWindow().getGuiScaledWidth();
    int height = mc.getWindow().getGuiScaledHeight();

    RenderSystem.setShaderColor(0.0f, 0.0f, 0.0f, strength * 0.8f);
    // Simple fullscreen quad with gradient would require a custom shader.
    // For simplicity, use a radial gradient approach via multiple textured quads.
    // Alternative: use a pre-made vignette texture.
    // For now, draw a full-screen dark overlay that fades toward center
    // using Minecraft's built-in blur or a simple dark overlay.
    // Placeholder — actual vignette rendering requires a shader or texture.
    // We'll use a dark fullscreen with alpha for initial implementation.
    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
}

private void renderDesaturation(float strength) {
    // NeoForge doesn't have a simple desaturation hook without shader.
    // Alternative: overlay a semi-transparent gray quad.
    // For a first pass, we skip shader-based desaturation and rely on vignette.
    // Future improvement: register a custom post-processing shader.
}
```

> **Note:** Vignette and desaturation require shader/post-processing for proper implementation. For the initial implementation, use a simple dark overlay texture approach. The Mixin injection point is correct; the actual rendering code will be refined during implementation.

- [ ] **Step 2: Create GameRendererMixin.java — FOV jitter**

```java
package net.mcreator.strangerecord.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.renderer.GameRenderer;

import net.mcreator.strangerecord.client.SanityScreenEffects;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void injectFovJitter(CallbackInfoReturnable<Double> cir) {
        float jitter = SanityScreenEffects.fovJitter;
        if (jitter != 0.0f) {
            cir.setReturnValue(cir.getReturnValue() + jitter);
        }
    }
}
```

- [ ] **Step 3: Create MovementInputMixin.java — random movement offset**

```java
package net.mcreator.strangerecord.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;

import net.mcreator.strangerecord.client.SanityScreenEffects;

import java.util.Random;

@Mixin(KeyboardInput.class)
public abstract class MovementInputMixin extends Input {

    private static final Random RANDOM = new Random();

    @Inject(method = "tick", at = @At("TAIL"))
    private void injectMovementOffset(boolean isSprinting, float speedModifier, CallbackInfo ci) {
        float chance = SanityScreenEffects.movementOffsetChance;
        if (chance > 0.0f && RANDOM.nextFloat() < chance) {
            // Randomly rotate movement direction slightly
            float angle = (RANDOM.nextFloat() - 0.5f) * 0.5f; // ±0.25 rad (~±15°)
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float newLeftImpulse = this.leftImpulse * cos - this.forwardImpulse * sin;
            float newForwardImpulse = this.leftImpulse * sin + this.forwardImpulse * cos;
            this.leftImpulse = newLeftImpulse;
            this.forwardImpulse = newForwardImpulse;
        }
    }
}
```

- [ ] **Step 4: Modify strangerecord.mixins.json — register new mixins**

```json
{
    "required": true,
    "minVersion": "0.8",
    "package": "net.mcreator.strangerecord.mixin",
    "compatibilityLevel": "JAVA_17",
    "client": [
        "CatRendererMixin",
        "LevelRendererMixin",
        "GameRendererMixin",
        "MovementInputMixin"
    ]
}
```

- [ ] **Step 5: Commit**

---

### Task 8: Modify CloudWater items — trigger sanity loss on drink

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/item/CloudWaterButtleItem.java`
- Modify: `src/main/java/net/mcreator/strangerecord/item/CloudWaterItem.java`

- [ ] **Step 1: Modify CloudWaterButtleItem.java**

Add import:

```java
import net.mcreator.strangerecord.events.SanityEventHandler;
```

In `finishUsingItem`, add before `return retval;`:

```java
SanityEventHandler.onCloudWaterDrunk(entity);
```

Full method:

```java
@Override
public ItemStack finishUsingItem(ItemStack itemstack, Level world, LivingEntity entity) {
    ItemStack retval = super.finishUsingItem(itemstack, world, entity);
    CloudWaterButtleWanJiaWanChengShiYongWuPinShiProcedure.execute(entity);
    SanityEventHandler.onCloudWaterDrunk(entity);
    return retval;
}
```

- [ ] **Step 2: Check CloudWaterItem.java for similar structure**

Read `CloudWaterItem.java` first to understand its structure, then add the same `SanityEventHandler.onCloudWaterDrunk(entity);` call in the appropriate method (likely `finishUsingItem` or its procedure class).

- [ ] **Step 3: Commit**

---

### Task 9: Hook sanity triggers into existing event listeners

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/events/CornPoppyAngryListener.java`
- Modify: `src/main/java/net/mcreator/strangerecord/events/PlayerLookAtCornPoppyListener.java`
- Modify: `src/main/java/net/mcreator/strangerecord/events/StrangeFishingRodLootHandler.java`

- [ ] **Step 1: Modify CornPoppyAngryListener.java — add sanity loss for SadPoppy proximity**

Add import:

```java
import net.mcreator.strangerecord.events.SanityEventHandler;
```

In `applyAngryEffect` method, after the existing `applyAngryDebuffs(player);` line, add:

```java
if (player instanceof ServerPlayer serverPlayer) {
    SanityEventHandler.onSadPoppyProximity(serverPlayer);
}
```

- [ ] **Step 2: Modify PlayerLookAtCornPoppyListener.java — Sad effect now handled by SanityEventHandler**

No changes needed here — the Sad effect application already happens, and `SanityEventHandler.onMobEffectAdded` will pick it up automatically via NeoForge event bus.

- [ ] **Step 3: Modify StrangeFishingRodLootHandler.java — Tier3 mob sanity loss**

Add import:

```java
import net.mcreator.strangerecord.events.SanityEventHandler;
```

In `dispatchLoot`, after the mob spawn block where `entity != null` is checked, add sanity loss for Tier3:

```java
if (entity != null) {
    // existing velocity code...
    
    // Sanity loss for Tier 3 mobs (wither, warden)
    if (weirdness >= 0.70 && player instanceof ServerPlayer serverPlayer) {
        SanityEventHandler.onFishingTier3(serverPlayer);
    }
}
```

- [ ] **Step 4: Commit**

---

### Task 10: Register client handlers and final wiring

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/StrangerecordMod.java`

- [ ] **Step 1: Add SanityClientHandler init**

In the constructor's user code block, add:

```java
SanityClientHandler.init();
```

- [ ] **Step 2: Verify all imports in StrangerecordMod.java**

Make sure all new imports are present:

```java
import net.mcreator.strangerecord.init.StrangerecordModAttachments;
import net.mcreator.strangerecord.events.SanityEventHandler;
import net.mcreator.strangerecord.events.SanityRecoveryHandler;
import net.mcreator.strangerecord.client.SanityClientHandler;
```

- [ ] **Step 3: Build and verify**

Run: `gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

---

## Assumptions & Decisions

1. **AttachmentType sync**: NeoForge `AttachmentType` with `.serialize(Codec)` automatically syncs to clients. No manual packet needed.
2. **Cloud dimension ID**: Assumed to be `cloud_dem` based on `CloudDemDimension.java`. Verify during implementation.
3. **Vignette rendering**: Uses overlay approach via Mixin into `LevelRenderer.renderLevel`. Full shader-based vignette is deferred to future iteration.
4. **Movement offset**: Uses `KeyboardInput` mixin. Gamepad input (`ControllerInput`) not covered — acceptable for initial implementation.
5. **Event registration**: `@EventBusSubscriber` on `StrangeFishingRodLootHandler` uses `Dist.DEDICATED_SERVER` — this is fine, SanityEventHandler is registered via `NeoForge.EVENT_BUS.register()` directly.
6. **Sad effect trigger**: `MobEffectEvent.Added` fires on server side when effect is added. This covers both player and NPC Sad application.
7. **Brain damage key**: Uses `strangerecord:braindamage` damage type registered in `data/strangerecord/damage_type/braindamage.json`.
