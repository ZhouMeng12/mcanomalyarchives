package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

public class CornPoppyClientListener {
    private static class ClientPlayerState {
        float originalYawSpeed;
        float originalPitchSpeed;
        double originalSensitivity;
        boolean isSensitivityModified;
        float targetYaw;
        float targetPitch;
        boolean hasTargetAngle;
    }

    private static final Map<UUID, ClientPlayerState> clientPlayerStates = new ConcurrentHashMap<>();
    private static final int MAX_ADDICTION_LEVEL = 10;

    private static final ResourceLocation CORN_POPPY_OVERLAY = ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, "textures/block/invicon_poppy.png");
    private static final int OVERLAY_WIDTH = 256; // 大尺寸
    private static final int OVERLAY_HEIGHT = 256; // 大尺寸
    private static final int BASE_ALPHA = 77;
    private static final int MAX_OVERLAY_COUNT = 30;
    private static final int IMAGES_PER_LEVEL = 3;
    private static final long BUFFER_TIME = 20;
    
    private static final Random random = new Random();
    private static float[] imageStartX = new float[MAX_OVERLAY_COUNT];
    private static float[] imageStartY = new float[MAX_OVERLAY_COUNT];
    private static float[] imageTargetX = new float[MAX_OVERLAY_COUNT];
    private static float[] imageTargetY = new float[MAX_OVERLAY_COUNT];
    private static float[] imageProgress = new float[MAX_OVERLAY_COUNT];
    private static float[] imageScales = new float[MAX_OVERLAY_COUNT];
    private static long[] imageSpawnTimes = new long[MAX_OVERLAY_COUNT];
    private static long effectStartTime = 0;
    private static boolean hasActiveEffect = false;
    private static int currentAddictionLevel = 0;

    private static int logTimer = 0;
    
    public static void init() {
        if (FMLEnvironment.dist.isClient()) {
            McanomalyarchivesMod.LOGGER.info("=== CornPoppyClientListener INITIALIZED ===");
            NeoForge.EVENT_BUS.register(new CornPoppyClientListener());
        }
    }

    private static void initializeEdgePositions(int count) {
        for (int i = 0; i < count; i++) {
            imageSpawnTimes[i] = -1;
            
            int edge = random.nextInt(3);
            float margin = 0.05f;
            
            switch (edge) {
                case 0:
                    imageStartX[i] = -0.1f;
                    imageStartY[i] = margin + random.nextFloat() * (1.0f - 2 * margin);
                    imageTargetX[i] = margin + random.nextFloat() * 0.3f;
                    imageTargetY[i] = margin + random.nextFloat() * (1.0f - 2 * margin);
                    break;
                case 1:
                    imageStartX[i] = 1.1f;
                    imageStartY[i] = margin + random.nextFloat() * (1.0f - 2 * margin);
                    imageTargetX[i] = 1.0f - margin - random.nextFloat() * 0.3f;
                    imageTargetY[i] = margin + random.nextFloat() * (1.0f - 2 * margin);
                    break;
                case 2:
                    imageStartX[i] = margin + random.nextFloat() * (1.0f - 2 * margin);
                    imageStartY[i] = -0.1f;
                    imageTargetX[i] = margin + random.nextFloat() * (1.0f - 2 * margin);
                    imageTargetY[i] = margin + random.nextFloat() * 0.3f;
                    break;
            }
            
            imageProgress[i] = 0;
            imageScales[i] = 5.0f + random.nextFloat() * 3.0f; // 大尺寸
        }
    }

    private static float easeOutQuad(float t) {
        return t * (2 - t);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null) {
            return;
        }

        UUID playerId = player.getUUID();
        ClientPlayerState state = clientPlayerStates.computeIfAbsent(playerId, k -> new ClientPlayerState());

        boolean hasAddiction = player.hasEffect(McanomalyarchivesModMobEffects.ADDICTE);
        int addictionLevel = hasAddiction ? player.getEffect(McanomalyarchivesModMobEffects.ADDICTE).getAmplifier() + 1 : 0;

        if (hasAddiction && addictionLevel > 0 && addictionLevel <= MAX_ADDICTION_LEVEL) {
            if (!state.isSensitivityModified) {
                state.originalSensitivity = mc.options.sensitivity().get();
                state.isSensitivityModified = true;
            }

            double sensitivityMultiplier = 1.0 - (addictionLevel - 1) * 0.15;
            sensitivityMultiplier = Math.max(0.05, sensitivityMultiplier);
            double newSensitivity = state.originalSensitivity * sensitivityMultiplier;
            
            try {
                mc.options.sensitivity().set(newSensitivity);
            } catch (Exception e) {
                e.printStackTrace();
            }

            state.hasTargetAngle = true;
            state.targetYaw = player.getYRot();
            state.targetPitch = player.getXRot();
        } else {
            if (state.isSensitivityModified) {
                try {
                    mc.options.sensitivity().set(state.originalSensitivity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                state.isSensitivityModified = false;
            }

            if (state.hasTargetAngle) {
                float yawDiff = player.getYRot() - state.targetYaw;
                float pitchDiff = player.getXRot() - state.targetPitch;
                
                if (Math.abs(yawDiff) > 0.1f || Math.abs(pitchDiff) > 0.1f) {
                    float recoverySpeed = 0.05f;
                    float newYaw = player.getYRot() - yawDiff * recoverySpeed;
                    float newPitch = player.getXRot() - pitchDiff * recoverySpeed;
                    
                    player.setYRot(newYaw);
                    player.setXRot(newPitch);
                } else {
                    state.hasTargetAngle = false;
                }
            }
        }

        updateOverlay(player, addictionLevel);
    }

    private static void updateOverlay(LocalPlayer player, int addictionLevel) {
        Minecraft mc = Minecraft.getInstance();
        logTimer++;
        
        if (logTimer % 300 == 0) {
            McanomalyarchivesMod.LOGGER.info("updateOverlay called - addictionLevel=" + addictionLevel);
        }

        if (player == null || mc.level == null) {
            return;
        }

        long currentGameTime = mc.level.getGameTime();

        if (addictionLevel <= 0) {
            hasActiveEffect = false;
            effectStartTime = 0;
            currentAddictionLevel = 0;
            for (int i = 0; i < MAX_OVERLAY_COUNT; i++) {
                imageSpawnTimes[i] = -1;
                imageProgress[i] = 0;
            }
            if (logTimer % 300 == 0) {
                McanomalyarchivesMod.LOGGER.info("No addiction effect, clearing overlays");
            }
            return;
        }

        if (!hasActiveEffect || addictionLevel != currentAddictionLevel) {
            hasActiveEffect = true;
            effectStartTime = currentGameTime;
            currentAddictionLevel = addictionLevel;
            initializeEdgePositions(MAX_OVERLAY_COUNT);
        }

        long effectDuration = currentGameTime - effectStartTime;
        
        if (effectDuration < BUFFER_TIME) {
            return;
        }

        int overlayCount = Math.min(addictionLevel * IMAGES_PER_LEVEL, MAX_OVERLAY_COUNT);
        long spawnInterval = Math.max(2, 10 - addictionLevel);

        try {
            for (int i = 0; i < overlayCount; i++) {
                if (imageSpawnTimes[i] < 0) {
                    long spawnTime = effectStartTime + BUFFER_TIME + (long) i * spawnInterval;
                    if (currentGameTime >= spawnTime) {
                        imageSpawnTimes[i] = currentGameTime;
                    } else {
                        continue;
                    }
                }

                float growthSpeed = 0.005f + (addictionLevel * 0.002f);
                imageProgress[i] = Math.min(1.0f, imageProgress[i] + growthSpeed);
            }
        } catch (Exception e) {
            McanomalyarchivesMod.LOGGER.error("Failed to update corn poppy overlay", e);
        }
    }

    public static void renderOverlay(GuiGraphics guiGraphics) {
        if (logTimer % 400 == 0) {
            McanomalyarchivesMod.LOGGER.info("renderOverlay called - hasActiveEffect=" + hasActiveEffect);
        }
        
        if (!hasActiveEffect) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int overlayCount = Math.min(currentAddictionLevel * IMAGES_PER_LEVEL, MAX_OVERLAY_COUNT);
        
        if (logTimer % 400 == 0) {
            McanomalyarchivesMod.LOGGER.info("renderOverlay - overlayCount=" + overlayCount);
        }

        try {
            for (int i = 0; i < overlayCount; i++) {
                if (imageSpawnTimes[i] < 0) {
                    continue;
                }

                float easedProgress = easeOutQuad(imageProgress[i]);
                float currentX = imageStartX[i] + (imageTargetX[i] - imageStartX[i]) * easedProgress;
                float currentY = imageStartY[i] + (imageTargetY[i] - imageStartY[i]) * easedProgress;

                int drawX = (int) (currentX * screenWidth);
                int drawY = (int) (currentY * screenHeight);
                float scale = imageScales[i];
                int width = (int) (OVERLAY_WIDTH * scale);
                int height = (int) (OVERLAY_HEIGHT * scale);
                
                if (logTimer % 400 == 0 && i == 0) {
                    McanomalyarchivesMod.LOGGER.info("renderOverlay - drawing poppy at x=" + drawX + ", y=" + drawY + ", w=" + width + ", h=" + height);
                }

                // 使用 Items.POPPY 的纹理渲染大虞美人
                ItemStack poppyStack = new ItemStack(Items.POPPY);
                guiGraphics.renderItem(poppyStack, drawX - width/2, drawY - height/2);
            }
        } catch (Exception e) {
            McanomalyarchivesMod.LOGGER.error("Failed to render corn poppy overlay", e);
        }
    }
}
