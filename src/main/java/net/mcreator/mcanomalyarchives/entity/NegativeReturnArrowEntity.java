package net.mcreator.mcanomalyarchives.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.mcreator.mcanomalyarchives.codex.CodexObservations;
import net.mcreator.mcanomalyarchives.anomaly.negative.NegativeBowReturn;

/** Vanilla arrow motion/model. It flies once toward its original shooter, who may move or take cover. */
public final class NegativeReturnArrowEntity extends Arrow {
    private ServerPlayer target;
    private int targetEpoch;

    public NegativeReturnArrowEntity(EntityType<? extends NegativeReturnArrowEntity> type, Level level) {
        super(type, level);
        pickup = AbstractArrow.Pickup.DISALLOWED;
        setNoGravity(true);
    }

    public void aimAt(ServerPlayer shooter, Vec3 source) {
        target = shooter;
        targetEpoch = NegativeBowReturn.epoch(shooter);
        setPos(source);
        Vec3 aim = shooter.getBoundingBox().getCenter().subtract(source);
        shoot(aim.x, aim.y, aim.z, 4f, 0f);
        level().playSound(null, blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f, .7f);
    }

    @Override
    public void tick() {
        Vec3 previous = position();
        if (!level().isClientSide) {
            if (target == null || !target.isAlive() || target.isRemoved() || target.level() != level()
                    || targetEpoch != NegativeBowReturn.epoch(target)
                    || target.isSpectator() || target.getServer().getPlayerList().getPlayer(target.getUUID()) != target
                    || tickCount >= 80) {
                discard();
                return;
            }
            if (distanceToSqr(target) < 24 * 24 && target.hasLineOfSight(this)
                    && target.getLookAngle().dot(position().subtract(target.getEyePosition()).normalize()) > .3) {
                CodexObservations.record(target, "negative_bow_return");
            }
        }
        super.tick();
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            // Server particles are visible to nearby spectators too; draw the travelled segment, not just its endpoint.
            Vec3 travelled = position().subtract(previous);
            int steps = Math.min(12, Math.max(1, (int)Math.ceil(travelled.length() * 3)));
            var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.85f, .67f, 1f), .75f);
            for (int i = 0; i < steps; i++) {
                Vec3 point = previous.add(travelled.scale(i / (double)steps));
                server.sendParticles(dust, point.x, point.y, point.z, 1, .015, .015, .015, 0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity == target && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!level().isClientSide && hit.getEntity() == target) {
            CodexObservations.record(target, "negative_bow_return");
            // A physical lethal arrow: vanilla shields, invulnerability and totems still apply.
            target.hurt(damageSources().arrow(this, this), 1000f);
            level().playSound(null, blockPosition(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1f, 1f);
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
