package net.mcreator.mcanomalyarchives.entity;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

import javax.annotation.Nullable;
import java.util.UUID;

public class PurpleDogEntity extends PathfinderMob {
	private static final EntityDataAccessor<Boolean> PERFORM_MODE =
		SynchedEntityData.defineId(PurpleDogEntity.class, EntityDataSerializers.BOOLEAN);

	public final AnimationState animationState0 = new AnimationState();

	@Nullable
	private UUID performTargetUUID;

	public PurpleDogEntity(EntityType<PurpleDogEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(false);
		this.moveControl = new FlyingMoveControl(this, 10, true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PERFORM_MODE, false);
	}

	public boolean isPerformMode() {
		return this.entityData.get(PERFORM_MODE);
	}

	@Nullable
	public UUID getPerformTargetUUID() {
		return performTargetUUID;
	}

	public void setPerformMode(@Nullable UUID targetUUID) {
		this.entityData.set(PERFORM_MODE, targetUUID != null);
		this.performTargetUUID = targetUUID;
		if (targetUUID != null) {
			// 禁用AI，防止LookControl覆盖朝向
			this.setNoAi(true);
			this.goalSelector.getAvailableGoals().forEach(g -> this.goalSelector.removeGoal(g));
			this.targetSelector.getAvailableGoals().forEach(g -> this.targetSelector.removeGoal(g));
			this.getNavigation().stop();
			this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
		} else {
			// 退出表演模式，重新启用AI
			this.setNoAi(false);
			this.registerGoals();
		}
	}

	@Override
	protected PathNavigation createNavigation(Level world) {
		return new FlyingPathNavigation(this, world);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, false) {
			@Override
			protected boolean canPerformAttack(LivingEntity entity) {
				return this.isTimeToAttack() && this.mob.distanceToSqr(entity) < (this.mob.getBbWidth() * this.mob.getBbWidth() + entity.getBbWidth()) && this.mob.getSensing().hasLineOfSight(entity);
			}
		});
		this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
		this.goalSelector.addGoal(5, new FloatGoal(this));
	}

	@Override
	public SoundEvent getHurtSound(DamageSource ds) {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.generic.hurt"));
	}

	@Override
	public SoundEvent getDeathSound() {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.generic.death"));
	}

	@Override
	public boolean causeFallDamage(float l, float d, DamageSource source) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.animationState0.animateWhen(true, this.tickCount);
		}
		if (isPerformMode() && performTargetUUID != null && !this.level().isClientSide()) {
			Player target = this.level().getPlayerByUUID(performTargetUUID);
			if (target != null) {
				// 直接计算朝向玩家的角度
				double dx = target.getX() - this.getX();
				double dz = target.getZ() - this.getZ();
				float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
				this.setYRot(yaw);
				this.yBodyRot = yaw;
				this.yHeadRot = yaw;
				this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
			}
		}
	}

	@Override
	public void checkDespawn() {
		if (isPerformMode()) {
			return;
		}
		super.checkDespawn();
	}

	@Override
	public void travel(Vec3 dir) {
		super.travel(dir);
	}

	@Override
	protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
	}

	@Override
	public void setNoGravity(boolean ignored) {
		super.setNoGravity(true);
	}

	public void aiStep() {
		super.aiStep();
		this.setNoGravity(true);
	}

	public static void init(RegisterSpawnPlacementsEvent event) {
		event.register(McanomalyarchivesModEntities.PURPLE_DOG.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(entityType, world, reason, pos, random) -> (world.getDifficulty() != Difficulty.PEACEFUL && Monster.isDarkEnoughToSpawn(world, pos, random) && Mob.checkMobSpawnRules(entityType, world, reason, pos, random)),
				RegisterSpawnPlacementsEvent.Operation.REPLACE);
	}

	public static AttributeSupplier.Builder createAttributes() {
		AttributeSupplier.Builder builder = Mob.createMobAttributes();
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3);
		builder = builder.add(Attributes.MAX_HEALTH, 20);
		builder = builder.add(Attributes.ARMOR, 0);
		builder = builder.add(Attributes.ATTACK_DAMAGE, 3);
		builder = builder.add(Attributes.FOLLOW_RANGE, 16);
		builder = builder.add(Attributes.STEP_HEIGHT, 0.6);
		builder = builder.add(Attributes.FLYING_SPEED, 0.3);
		return builder;
	}
}