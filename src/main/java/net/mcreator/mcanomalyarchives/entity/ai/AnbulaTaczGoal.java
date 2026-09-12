package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;
import net.mcreator.mcanomalyarchives.compat.TaczCompat;

import java.util.EnumSet;

/**
 * 安布拉 TACZ 枪械射击 Goal。
 * 利用 TACZ 的 IGunOperator Mixin（所有 LivingEntity 已实现），
 * 通过 TaczCompat 反射调用 shoot()/reload()。
 * <p>
 * 行为：
 * - 中近距离风筝走位（5-14 格）
 * - 点射：射击瞬间站定，按目标移动速度预判瞄准
 * - 弹药耗尽时换弹
 */
public class AnbulaTaczGoal extends Goal {
    private final AnbulaEntity anbula;
    private static final double KITE_MIN_RANGE = 25.0;       // 5 格平方
    private static final double KITE_MAX_RANGE = 196.0;      // 14 格平方
    private static final double MELEE_DISTANCE_SQ = 9.0;     // 3 格平方，贴脸才近战
    private static final int SHOOT_COOLDOWN = 5;             // 点射间隔 tick（站定射击提高准度）
    private static final int RELOAD_PAUSE = 5;               // 换弹暂停 tick

    private int shootCooldown;
    private int reloadWait;
    private int seeTime;
    private int strafeDirection;
    private int strafeTimer;

    public AnbulaTaczGoal(AnbulaEntity anbula) {
        this.anbula = anbula;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!anbula.isBerserk() || anbula.isFaint()) return false;
        LivingEntity target = anbula.getTarget();
        if (target == null || !target.isAlive()) return false;

        // 只有手持 TACZ 枪械时激活
        if (!TaczCompat.isGun(anbula.getMainHandItem())) return false;

        // 弹药耗尽 且 敌人贴脸 → 切换近战（终止 Goal，交给近战 AI）
        int ammo = TaczCompat.getAmmoCount(anbula.getMainHandItem());
        if (ammo <= 0 && anbula.distanceToSqr(target) < MELEE_DISTANCE_SQ) {
            return false;
        }

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        // 初始化 TACZ ShooterDataHolder（Non-player 实体不会自动调用）
        TaczCompat.initializeShooter(anbula);
        shootCooldown = 0;
        reloadWait = 0;
        seeTime = 0;
        strafeDirection = 0;
        strafeTimer = 0;
    }

    @Override
    public void tick() {
        if (shootCooldown > 0) shootCooldown--;

        LivingEntity target = anbula.getTarget();
        if (target == null) return;

        boolean canSee = anbula.getSensing().hasLineOfSight(target);
        if (canSee) {
            seeTime++;
        } else {
            seeTime = 0;
        }

        anbula.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double dist = anbula.distanceToSqr(target);

        // 弹药检查：归零时换弹（短暂停顿模拟换弹）
        int ammo = TaczCompat.getAmmoCount(anbula.getMainHandItem());
        if (ammo <= 0) {
            if (reloadWait == 0) {
                int maxAmmo = TaczCompat.getMaxAmmo(anbula.getMainHandItem());
                TaczCompat.setAmmo(anbula.getMainHandItem(), maxAmmo > 0 ? maxAmmo : 30);
            }
            reloadWait++;
            if (reloadWait < RELOAD_PAUSE) return;
            reloadWait = 0;
        }

        // 距离管理（点射瞬间站定，避免移动散布）
        boolean wantsShoot = canSee && shootCooldown <= 0;
        if (wantsShoot) {
            // 站定射击：几乎完全停住，TACZ 移动散布归零
            anbula.getNavigation().stop();
            Vec3 v = anbula.getDeltaMovement();
            anbula.setDeltaMovement(v.x * 0.1, v.y, v.z * 0.1);
        } else if (dist < KITE_MIN_RANGE) {
            // 太近 → 直接速度推动后退（比导航更快，不受其他 Goal 中断）
            Vec3 away = anbula.position().subtract(target.position()).normalize().scale(0.6);
            anbula.setDeltaMovement(anbula.getDeltaMovement().add(away.x, 0.1, away.z));
            anbula.getNavigation().stop();
        } else if (dist > KITE_MAX_RANGE) {
            // 太远 → 追击
            anbula.getNavigation().moveTo(target, 1.2);
        } else {
            // 最佳距离 → 横向风筝
            strafeTimer++;
            if (strafeTimer >= 15) {
                strafeTimer = 0;
                strafeDirection = anbula.getRandom().nextBoolean() ? 1 : -1;
            }
            Vec3 toTarget = target.position().subtract(anbula.position()).normalize();
            Vec3 side = new Vec3(-toTarget.z * strafeDirection, 0, toTarget.x * strafeDirection).scale(0.5);
            anbula.getNavigation().moveTo(
                    anbula.getX() + side.x,
                    anbula.getY(),
                    anbula.getZ() + side.z,
                    0.9);
        }

        if (!wantsShoot) return;

        // 预判射击：按目标水平移动速度提前瞄准，弥补子弹飞行时间
        double flyTicks = Math.min(Math.sqrt(dist) / 8.0, 2.0);
        double tx = target.getX() + target.getDeltaMovement().x * flyTicks;
        double tz = target.getZ() + target.getDeltaMovement().z * flyTicks;
        double ty = target.getEyeY();

        double dx = tx - anbula.getX();
        double dy = ty - anbula.getEyeY();
        double dz = tz - anbula.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));

        String result = TaczCompat.tryShoot(anbula, pitch, yaw);
        if ("SUCCESS".equals(result)) {
            shootCooldown = SHOOT_COOLDOWN;
        }
        // COOL_DOWN / 其他 → 等下一 tick
    }

    @Override
    public void stop() {
        reloadWait = 0;
    }
}
