package net.mcreator.mcanomalyarchives.entity;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.common.NeoForgeMod;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;

public class StrangeTreeEntity extends PathfinderMob {

	/** 0=站立（脚模型） 1=钻地中（整树模型+动画） 2=已钻地（隐藏） 3=钻出中（整树模型+zuanchu） 4=夹击中（整树模型+jiaren） */
	private static final EntityDataAccessor<Integer> BURROW_STATE =
			SynchedEntityData.defineId(StrangeTreeEntity.class, EntityDataSerializers.INT);

	/** 钻地/钻出/夹击动画状态（保留，客户端用） */
	public final AnimationState burrowState = new AnimationState();

	/** 动画开始时刻（客户端 tickCount，-1=未开始） */
	public int burrowStartAge = -1;

	/** 客户端上一帧看到的钻地状态（用于状态切换时重置动画起点） */
	private int prevBurrowState = 0;

	public int getBurrowState() {
		return this.entityData.get(BURROW_STATE);
	}

	public void setBurrowState(int state) {
		this.entityData.set(BURROW_STATE, state);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BURROW_STATE, 0);
	}

	/** 持久化钻地状态：退出重进后树保持潜伏（state 2），不会变回脚形态站立 */
	@Override
	public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("McanomalyarchivesBurrowState", this.getBurrowState());
		tag.putBoolean("McanomalyarchivesNoPhysics", this.noPhysics);
	}

	@Override
	public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("McanomalyarchivesBurrowState")) {
			this.setBurrowState(tag.getInt("McanomalyarchivesBurrowState"));
		}
		this.noPhysics = tag.getBoolean("McanomalyarchivesNoPhysics");
		// 读档后同步客户端渲染所需字段（invisible 原版已持久化，这里确保 state 2 不播动画）
		this.prevBurrowState = this.getBurrowState();
		this.burrowStartAge = -1;
	}

	@Override
	public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
		// 整树模型远大于碰撞盒：扩大剔除盒，避免离远时模型被裁剪看不见
		return this.getBoundingBox().inflate(16.0, 30.0, 16.0);
	}

	@Override
	public boolean shouldRender(double camX, double camY, double camZ) {
		// 原版硬性 128 格渲染上限：放宽到 256 格，远处也能看到钻地动画
		double dx = this.getX() - camX;
		double dy = this.getY() - camY;
		double dz = this.getZ() - camZ;
		return dx * dx + dy * dy + dz * dz < 65536.0D;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			int state = this.getBurrowState();
			// 状态切换（如 1→3→4）时重置动画起点，避免用上一段动画的时间算新动画
			if (state != this.prevBurrowState) {
				this.prevBurrowState = state;
				this.burrowStartAge = -1;
			}
			// 动画态：1=钻地中 3=钻出中 4=夹击中 —— 记录动画起点
			if (state == 1 || state == 3 || state == 4) {
				if (this.burrowStartAge < 0) this.burrowStartAge = this.tickCount;
			} else {
				this.burrowStartAge = -1;
			}
			this.burrowState.animateWhen(state == 1 || state == 3 || state == 4, this.tickCount);
		}
	}

	public StrangeTreeEntity(EntityType<StrangeTreeEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(true);
		setPersistenceRequired();
		this.moveControl = new FlyingMoveControl(this, 10, true);
	}

	@Override
	protected PathNavigation createNavigation(Level world) {
		return new FlyingPathNavigation(this, world);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
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
	public boolean hurt(DamageSource damagesource, float amount) {
		if (damagesource.is(DamageTypes.IN_FIRE))
			return false;
		if (damagesource.getDirectEntity() instanceof AbstractArrow)
			return false;
		if (damagesource.getDirectEntity() instanceof Player)
			return false;
		if (damagesource.getDirectEntity() instanceof ThrownPotion || damagesource.getDirectEntity() instanceof AreaEffectCloud || damagesource.typeHolder().is(NeoForgeMod.POISON_DAMAGE))
			return false;
		if (damagesource.is(DamageTypes.FALL))
			return false;
		if (damagesource.is(DamageTypes.IN_WALL))
			return false; // 窒息免疫：怪树实体常年在树基座/地下，不能窒息死
		if (damagesource.is(DamageTypes.CACTUS))
			return false;
		if (damagesource.is(DamageTypes.DROWN))
			return false;
		if (damagesource.is(DamageTypes.LIGHTNING_BOLT))
			return false;
		if (damagesource.is(DamageTypes.EXPLOSION) || damagesource.is(DamageTypes.PLAYER_EXPLOSION))
			return false;
		if (damagesource.is(DamageTypes.TRIDENT))
			return false;
		if (damagesource.is(DamageTypes.FALLING_ANVIL))
			return false;
		if (damagesource.is(DamageTypes.DRAGON_BREATH))
			return false;
		if (damagesource.is(DamageTypes.WITHER) || damagesource.is(DamageTypes.WITHER_SKULL))
			return false;
		return super.hurt(damagesource, amount);
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();
		Level world = this.level();
		Entity entity = this;
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entityIn) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		return true;
	}

	@Override
	public boolean canBeCollidedWith() {
		return true;
	}

	@Override
	public void travel(Vec3 dir) {
		this.travelFlying(dir);
	}

	private void travelFlying(Vec3 dir) {
		if (this.isInWater()) {
			this.moveRelative(0.02F, dir);
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
		} else if (this.isInLava()) {
			this.moveRelative(0.02F, dir);
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
		} else {
			this.moveRelative((float) this.getAttributeValue(Attributes.FLYING_SPEED), dir);
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.91));
		}
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
	}

	public static AttributeSupplier.Builder createAttributes() {
		AttributeSupplier.Builder builder = Mob.createMobAttributes();
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3);
		builder = builder.add(Attributes.MAX_HEALTH, 10);
		builder = builder.add(Attributes.ARMOR, 0);
		builder = builder.add(Attributes.ATTACK_DAMAGE, 3);
		builder = builder.add(Attributes.FOLLOW_RANGE, 16);
		builder = builder.add(Attributes.STEP_HEIGHT, 0.6);
		builder = builder.add(Attributes.FLYING_SPEED, 0.3);
		return builder;
	}
}