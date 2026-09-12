package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;

public class AddictionClientHandler {

    private static boolean isSensitivityModified;
    private static double originalSensitivity;
    private static BlockPos cachedPoppy;
    private static int cachedPoppyTick = Integer.MIN_VALUE;

    public static void init() {
        if (FMLEnvironment.dist.isClient()) {
            McanomalyarchivesMod.LOGGER.info("=== AddictionClientHandler INITIALIZED ===");
            NeoForge.EVENT_BUS.register(new AddictionClientHandler());
        }
    }

    private static BlockPos findNearestPoppy(LocalPlayer player) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition(1f);
        
        double searchRadius = 10.0;
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        
        for (int x = (int) (eyePos.x - searchRadius); x <= (int) (eyePos.x + searchRadius); x++) {
            for (int y = (int) (eyePos.y - searchRadius); y <= (int) (eyePos.y + searchRadius); y++) {
                for (int z = (int) (eyePos.z - searchRadius); z <= (int) (eyePos.z + searchRadius); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block == McanomalyarchivesModBlocks.CORN_POPPY.get()) {
                        Vec3 center = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                        double distSq = eyePos.distanceToSqr(center);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static float[] getLookAtAngles(LocalPlayer player, BlockPos targetPos) {
        Vec3 eyePos = player.getEyePosition(1f);
        Vec3 targetCenter = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        Vec3 direction = targetCenter.subtract(eyePos).normalize();

        double x = direction.x;
        double y = direction.y;
        double z = direction.z;

        double horizontalDistance = Math.sqrt(x * x + z * z);
        
        float yaw = (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0f);
        float pitch = (float) -Math.toDegrees(Math.atan2(y, horizontalDistance));
        
        return new float[]{yaw, pitch};
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        MobEffectInstance effect = player.getEffect(McanomalyarchivesModMobEffects.ADDICTE);
        boolean hasAddiction = effect != null;
        int addictionLevel = hasAddiction ? effect.getAmplifier() + 1 : 0;

        if (hasAddiction && addictionLevel > 0) {
            if (!isSensitivityModified) {
                originalSensitivity = mc.options.sensitivity().get();
                isSensitivityModified = true;
            }

            int effectiveLevel = Math.min(addictionLevel, 6);
            double sensitivityMultiplier = Math.max(0.005, 1.0 - effectiveLevel * 0.15);
            mc.options.sensitivity().set(originalSensitivity * sensitivityMultiplier);

            // 方块扫描结果缓存 5 tick（镜头牵引仍每 tick 平滑）
            if (Math.abs(player.tickCount - cachedPoppyTick) >= 5) {
                cachedPoppy = findNearestPoppy(player);
                cachedPoppyTick = player.tickCount;
            }
            BlockPos poppyPos = cachedPoppy;
            if (poppyPos != null) {
                float[] angles = getLookAtAngles(player, poppyPos);
                float targetYaw = angles[0];
                float targetPitch = angles[1];
                
                float currentYaw = player.getYRot();
                float currentPitch = player.getXRot();
                
                float recoveryStrength = effectiveLevel * 0.3f;
                float yawDiff = currentYaw - targetYaw;
                float pitchDiff = currentPitch - targetPitch;
                
                while (yawDiff > 180) yawDiff -= 360;
                while (yawDiff < -180) yawDiff += 360;
                
                float newYaw = currentYaw - yawDiff * recoveryStrength;
                float newPitch = currentPitch - pitchDiff * recoveryStrength;
                player.setYRot(newYaw);
                player.setXRot(newPitch);
            }

        } else {
            if (isSensitivityModified) {
                mc.options.sensitivity().set(originalSensitivity);
                isSensitivityModified = false;
            }
        }
    }
}
