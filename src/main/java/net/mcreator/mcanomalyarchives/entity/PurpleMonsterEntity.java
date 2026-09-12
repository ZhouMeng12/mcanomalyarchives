package net.mcreator.mcanomalyarchives.entity;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import net.mcreator.mcanomalyarchives.event.PurpleBossFightTrigger;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

public class PurpleMonsterEntity extends Monster implements GeoAnimatable {
	private static final EntityDataAccessor<Boolean> PERFORM_MODE =
		SynchedEntityData.defineId(PurpleMonsterEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> TEXTURE_VARIANT =
		SynchedEntityData.defineId(PurpleMonsterEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> PLAYING_SWAYHAND =
		SynchedEntityData.defineId(PurpleMonsterEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> MODEL_VARIANT =
		SynchedEntityData.defineId(PurpleMonsterEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> STALKER_MODE =
		SynchedEntityData.defineId(PurpleMonsterEntity.class, EntityDataSerializers.BOOLEAN);

	@Nullable
	private UUID performTargetUUID;

	private int stalkerTickCount = 0; // 窥视者模式存活计时
	private static final int STALKER_MAX_TICKS = 1200; // 60秒后自动消失
	private boolean stalkerSeen = false; // 是否已被玩家看到（计数后进入延迟消失）
	private int stalkerSeenTicks = 0; // 被看到后的等待 tick
	private static final int STALKER_SEEN_DISAPPEAR_TICKS = 80; // 被看到后 4 秒才消失

	// ===== GeckoLib 动画缓存 =====
	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

	public PurpleMonsterEntity(EntityType<PurpleMonsterEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(false);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PERFORM_MODE, false);
		builder.define(TEXTURE_VARIANT, 0);
		builder.define(PLAYING_SWAYHAND, false);
		builder.define(MODEL_VARIANT, 0);
		builder.define(STALKER_MODE, false);
	}

	public boolean isPerformMode() {
		return this.entityData.get(PERFORM_MODE);
	}

	public int getTextureVariant() {
		return this.entityData.get(TEXTURE_VARIANT);
	}

	public void setTextureVariant(int variant) {
		this.entityData.set(TEXTURE_VARIANT, variant);
	}

	public int getModelVariant() {
		return this.entityData.get(MODEL_VARIANT);
	}

	public void setModelVariant(int variant) {
		this.entityData.set(MODEL_VARIANT, variant);
	}

	public boolean isStalkerMode() {
		return this.entityData.get(STALKER_MODE);
	}

	public void setStalkerMode(boolean stalker) {
		this.entityData.set(STALKER_MODE, stalker);
		if (stalker) {
			this.setNoAi(true);
			this.goalSelector.getAvailableGoals().forEach(g -> this.goalSelector.removeGoal(g));
			this.targetSelector.getAvailableGoals().forEach(g -> this.targetSelector.removeGoal(g));
			this.getNavigation().stop();
		}
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
			// 清除所有 AI Goal，停止移动
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
	public Vec3 getPassengerRidingPosition(Entity entity) {
		return super.getPassengerRidingPosition(entity).add(0, -0.35F, 0);
	}

	@Override
	public void tick() {
		super.tick();
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
		if (isStalkerMode() && !this.level().isClientSide()) {
			tickStalker();
		}
	}

	private void tickStalker() {
		stalkerTickCount++;
		if (this.level().isClientSide()) return;

		// 找最近玩家
		Player nearestPlayer = this.level().getNearestPlayer(this, 40);
		if (nearestPlayer == null) {
			// 无人：超时后遁走
			if (stalkerTickCount >= STALKER_MAX_TICKS) {
				vanish();
			}
			return;
		}
		// 盯着玩家：头部（含上下俯仰）对准玩家眼睛，躯干跟随
		Vec3 eye = nearestPlayer.getEyePosition(1.0F);
		double dx = eye.x - this.getX();
		double dy = eye.y - this.getEyeY();
		double dz = eye.z - this.getZ();
		float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
		float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		// 快速插值，盯得更紧
		this.yHeadRot += net.minecraft.util.Mth.wrapDegrees(targetYaw - this.yHeadRot) * 0.3F;
		this.setXRot(this.getXRot() + net.minecraft.util.Mth.wrapDegrees(targetPitch - this.getXRot()) * 0.3F);
		this.yBodyRot = this.yHeadRot;
		this.setYRot(this.yHeadRot);
		this.setDeltaMovement(0, this.getDeltaMovement().y, 0);

		// 超时：播放粒子后遁走（不再原地消失）
		if (stalkerTickCount >= STALKER_MAX_TICKS) {
			vanish();
			return;
		}
		// 玩家直视或靠近 → 计数一次遭遇，然后遁走
		double distSqr = this.distanceToSqr(nearestPlayer);
		boolean isClose = distSqr < 64; // 8格内
		boolean isLookedAt = false;
		if (distSqr < 900) { // 30格内才检测视角
			Vec3 lookVec = nearestPlayer.getLookAngle();
			Vec3 toStalker = new Vec3(this.getX() - nearestPlayer.getX(),
				this.getEyeY() - nearestPlayer.getEyeY(),
				this.getZ() - nearestPlayer.getZ()).normalize();
			isLookedAt = lookVec.dot(toStalker) > 0.95;
		}
		if (isClose || isLookedAt) {
			// 被看到：只计数一次，然后进入"延迟消失"（不立刻消失）
			if (!stalkerSeen) {
				stalkerSeen = true;
				if (nearestPlayer instanceof ServerPlayer sp) {
					countSighting(sp);
				}
			}
		}
		if (stalkerSeen) {
			stalkerSeenTicks++;
			if (stalkerSeenTicks >= STALKER_SEEN_DISAPPEAR_TICKS) {
				vanish();
			}
		}
	}

	/** 遁走：播放粒子（+音效）后消失 */
	private void vanish() {
		if (this.level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.SMOKE,
					this.getX(), this.getY() + 1.0, this.getZ(),
					12, 0.3, 0.4, 0.3, 0.02);
			serverLevel.playSound(null, this.blockPosition(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.1f);
		}
		this.remove(Entity.RemovalReason.DISCARDED);
	}

	/** 遭遇计数 +1（持久化，用于渐进逼近距离档位） */
	private void countSighting(ServerPlayer player) {
		int sightings = player.getData(McanomalyarchivesModAttachments.PURPLE_STALKER_SIGHTINGS);
		player.setData(McanomalyarchivesModAttachments.PURPLE_STALKER_SIGHTINGS, sightings + 1);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (isStalkerMode()) {
			// 窥视者无敌；被玩家攻击 → 触发 Boss 战（占位钩子），成功后遁走
			if (!this.level().isClientSide() && source.getEntity() instanceof ServerPlayer player) {
				if (PurpleBossFightTrigger.tryTriggerBossFight(player)) {
					vanish();
					return false;
				}
			}
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void checkDespawn() {
		if (isPerformMode() || isStalkerMode()) {
			return;
		}
		super.checkDespawn();
	}

	@Override
	public SoundEvent getHurtSound(DamageSource ds) {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.generic.hurt"));
	}

	@Override
	public SoundEvent getDeathSound() {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.generic.death"));
	}

	public static void init(RegisterSpawnPlacementsEvent event) {
		event.register(McanomalyarchivesModEntities.PURPLE_MONSTER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
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
		return builder;
	}

	// ===== GeckoLib 动画控制 =====
	private PlayState predicate(AnimationState<PurpleMonsterEntity> test) {
		if (this.entityData.get(PLAYING_SWAYHAND)) {
			test.setAnimation(RawAnimation.begin().thenPlay("animation.purplemonster.swayhand"));
			return PlayState.CONTINUE;
		}
		if (isPerformMode()) {
			test.setAnimation(RawAnimation.begin().thenPlay("animation.purplemonster.hand"));
			return PlayState.CONTINUE;
		}
		if (test.isMoving()) {
			test.setAnimation(RawAnimation.begin().thenLoop("animation.purplemonster.walk"));
		} else {
			test.setAnimation(RawAnimation.begin().thenPlay("animation.purplemonster.idle"));
		}
		return PlayState.CONTINUE;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
		controllerRegistrar.add(new AnimationController<>(this, "controller", 5, this::predicate));
	}

	public void triggerSwayhand() {
		if (!this.level().isClientSide()) return;
		this.entityData.set(PLAYING_SWAYHAND, true);
	}

	public void triggerModelHand() {
		if (!this.level().isClientSide()) return;
		this.entityData.set(PLAYING_SWAYHAND, false);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.animatableInstanceCache;
	}

	@Override
	public double getTick(Object entity) {
		return this.tickCount;
	}
}