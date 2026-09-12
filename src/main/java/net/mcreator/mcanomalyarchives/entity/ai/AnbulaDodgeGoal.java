package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;
import net.mcreator.mcanomalyarchives.compat.TaczCompat;

import java.util.EnumSet;
import java.util.List;

/**
 * 安布拉闪避：近战概率预判侧闪 + 投射物/音波预判侧闪。
 * 两种闪避有独立冷却，互不干扰。
 */
public class AnbulaDodgeGoal extends Goal {
    private final AnbulaEntity anbula;

    // 投射物闪避
    private static final double PROJ_DETECT_RANGE = 8.0;
    private static final double PROJ_DODGE_THRESHOLD = 4.0;
    private static final int PROJ_DODGE_COOLDOWN = 12;
    private static final int PROJ_DODGE_DURATION = 8;
    private int projCooldown = 0;

    // 近战闪避
    private static final double MELEE_DETECT_RANGE = 2.5;
    private static final int MELEE_DODGE_COOLDOWN = 15;
    private static final double MELEE_DODGE_CHANCE = 0.35;
    private int meleeCooldown = 0;
    private int meleeCloseTicks = 0;

    // 当前闪避状态
    private int dodgeTicks = 0;
    private boolean isDodging = false;

    public AnbulaDodgeGoal(AnbulaEntity anbula) {
        this.anbula = anbula;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!anbula.isBerserk() || anbula.isFaint()) return false;
        LivingEntity target = anbula.getTarget();
        if (target == null || !target.isAlive()) return false;

        // 先冷却递减
        updateCooldowns();

        // 投射物闪避检测（持枪时仍可闪避）
        if (projCooldown <= 0 && detectIncomingProjectile()) {
            return true;
        }

        // 手持 TACZ 枪械时不触发近战闪避（远程风筝由 TaczGoal 管理距离）
        if (TaczCompat.isGun(anbula.getMainHandItem())) return false;

        // 近战闪避检测
        if (meleeCooldown <= 0 && detectMeleeThreat(target)) {
            return true;
        }

        return false;
    }

    private void updateCooldowns() {
        if (projCooldown > 0) projCooldown--;
        if (meleeCooldown > 0) meleeCooldown--;
    }

    // ===== 投射物检测 =====

    private boolean detectIncomingProjectile() {
        AABB box = anbula.getBoundingBox().inflate(PROJ_DETECT_RANGE);
        List<Projectile> projectiles = anbula.level().getEntitiesOfClass(Projectile.class, box,
                p -> p.getOwner() != anbula && p.isAlive());

        for (Projectile proj : projectiles) {
            double dist = anbula.distanceToSqr(proj);
            if (dist > PROJ_DODGE_THRESHOLD * PROJ_DODGE_THRESHOLD) continue;

            // 检查投射物是否在飞向自己（不是飞过）
            Vec3 projDir = proj.getDeltaMovement().normalize();
            Vec3 toAnbula = anbula.position().subtract(proj.position()).normalize();
            double dot = projDir.dot(toAnbula);
            if (dot <= 0.3) continue; // 不是朝自己飞来

            projCooldown = PROJ_DODGE_COOLDOWN;
            return true;
        }
        return false;
    }

    // ===== 近战检测 =====

    private boolean detectMeleeThreat(LivingEntity target) {
        double dist = anbula.distanceToSqr(target);
        if (dist > MELEE_DETECT_RANGE * MELEE_DETECT_RANGE) {
            meleeCloseTicks = 0;
            return false;
        }

        meleeCloseTicks++;
        if (meleeCloseTicks < 5) return false; // 需贴身0.25秒

        // 每tick 35%概率触发
        if (anbula.getRandom().nextDouble() < MELEE_DODGE_CHANCE) {
            meleeCloseTicks = 0;
            meleeCooldown = MELEE_DODGE_COOLDOWN;
            return true;
        }
        return false;
    }

    // ===== 闪避执行 =====

    @Override
    public boolean canContinueToUse() {
        return isDodging && dodgeTicks < PROJ_DODGE_DURATION;
    }

    @Override
    public void start() {
        isDodging = true;
        dodgeTicks = 0;
        if (!executeDodge()) {
            isDodging = false; // 没空间，取消本次闪避
        }
    }

    /**
     * 同高度水平闪避：保持当前 Y，仅水平位移，避免地洞中瞬移到地表。
     * 优先当前 Y，其次向下 1 格（下台阶）；找不到空间返回 false（不闪避）。
     */
    private boolean executeDodge() {
        LivingEntity target = anbula.getTarget();
        if (target == null) return false;

        Vec3 dodgeDir;

        if (meleeCooldown > 0) {
            // 近战闪避：垂直于目标朝向
            float targetYaw = target.getYRot();
            double rad = Math.toRadians(targetYaw);
            Vec3 targetFacing = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            dodgeDir = new Vec3(-targetFacing.z, 0, targetFacing.x);
        } else {
            // 投射物闪避：随机侧向
            dodgeDir = anbula.getRandom().nextBoolean()
                    ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        }

        // 随机方向
        if (anbula.getRandom().nextBoolean()) {
            dodgeDir = dodgeDir.scale(-1);
        }

        int baseY = anbula.getBlockY();
        Vec3[] offsets = { dodgeDir.scale(2.0), dodgeDir.scale(-2.0) };
        for (Vec3 offset : offsets) {
            int x = (int) Math.floor(anbula.getX() + offset.x);
            int z = (int) Math.floor(anbula.getZ() + offset.z);
            // 优先同 y，其次降一格（下台阶）
            for (int dy = 0; dy >= -1; dy--) {
                int y = baseY + dy;
                if (hasSpaceAtLevel(x, y, z)) {
                    anbula.setPos(x + 0.5, y, z + 0.5);
                    spawnPoofParticles();
                    return true;
                }
            }
        }
        return false; // 没空间，不闪避
    }

    /** 检查 (x, y, z) 处是否有 2 格高的行走空间且脚下有支撑 */
    private boolean hasSpaceAtLevel(int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return anbula.level().getBlockState(feet.below()).isSolid()
                && !anbula.level().getBlockState(feet).isSolid()
                && !anbula.level().getBlockState(feet.above()).isSolid();
    }

    private void spawnPoofParticles() {
        if (anbula.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    anbula.getX(), anbula.getY() + 0.5, anbula.getZ(),
                    8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @Override
    public void tick() {
        dodgeTicks++;
        if (dodgeTicks >= PROJ_DODGE_DURATION) {
            isDodging = false;
        }
    }

    @Override
    public void stop() {
        isDodging = false;
        dodgeTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
