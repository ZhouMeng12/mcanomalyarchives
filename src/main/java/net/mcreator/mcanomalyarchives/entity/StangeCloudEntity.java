package net.mcreator.mcanomalyarchives.entity;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.common.NeoForgeMod;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;

public class StangeCloudEntity extends Monster {
	
	// 固定高度飞行相关字段
	private double fixedYLevel = 60.0D;
	private double xSpeedFactor = 0.2D;
	private double zSpeedFactor = 0.2D;
	private boolean enableRandomDirectionChange = true;
	private int directionChangeInterval = 6000;
	
	// 云伤害相关字段
	private double cloudDamageLength = 15.0D;
	private double cloudDamageWidth = 7.0D;
	private double cloudDamageRadiusY = 3.0D;
	private float cloudDamagePerTick = 1.0f;
	private int damageInterval = 10;
	private int damageTimer = 0;
	
	// 移动方向相关字段
	private double currentXDir = 1.0D;
	private double currentZDir = 0.0D;
	private int directionTimer = 0;
	private boolean isFirstSpawn = true;

	public StangeCloudEntity(EntityType<StangeCloudEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(false);
		setPersistenceRequired();
		this.moveControl = new FlyingMoveControl(this, 10, true);
	}

	@Override
	protected PathNavigation createNavigation(Level world) {
		return new FlyingPathNavigation(this, world);
	}

	@Override
	protected void registerGoals() {
		// 伪云使用自定义方向移动，不注册导航类 AI Goal（避免与 setPos 冲突导致抽搐）
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean shouldDespawnInPeaceful() {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public void checkDespawn() {
		// 完全禁用消失逻辑
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
		if (damagesource.is(DamageTypes.IN_WALL))
			return false;
		return super.hurt(damagesource, amount);
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
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

	@Override
	public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("FirstSpawn", isFirstSpawn);
		tag.putDouble("FixedYLevel", fixedYLevel);
	}

	@Override
	public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("FirstSpawn")) {
			isFirstSpawn = tag.getBoolean("FirstSpawn");
		}
		if (tag.contains("FixedYLevel")) {
			fixedYLevel = tag.getDouble("FixedYLevel");
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		this.setNoGravity(true);
		
		// 初始化固定飞行高度
		if (isFirstSpawn) {
			fixedYLevel = this.getY();
			isFirstSpawn = false;
		}
		
		// 计算修正后的 Y
		double currentY = this.getY();
		double yDiff = fixedYLevel - currentY;
		double newY = (Math.abs(yDiff) > 0.1D) ? currentY + yDiff * 0.05D : currentY;
		
		// 更新方向
		if (enableRandomDirectionChange) {
			directionTimer++;
			if (directionTimer >= directionChangeInterval) {
				directionTimer = 0;
				changeRandomDirection();
			}
		}
		
		// 计算水平移动
		double baseSpeed = this.getAttributeValue(Attributes.FLYING_SPEED);
		double newX = this.getX() + baseSpeed * xSpeedFactor * currentXDir;
		double newZ = this.getZ() + baseSpeed * zSpeedFactor * currentZDir;
		
		// 单次 setPos 避免双倍位置更新抽搐
		this.setPos(newX, newY, newZ);
		
		// 更新朝向
		if (currentXDir != 0) {
			this.setYRot(currentXDir > 0 ? 0f : 180f);
		} else if (currentZDir != 0) {
			this.setYRot(currentZDir > 0 ? 90f : -90f);
		}
		
		applyCloudDamage();
		convertLavaAndWaterToCloudBlocks();
	}
	
	private void changeRandomDirection() {
		RandomSource random = this.getRandom();
		
		if (random.nextBoolean()) {
			currentXDir = random.nextBoolean() ? 1.0D : -1.0D;
			currentZDir = 0.0D;
		} else {
			currentXDir = 0.0D;
			currentZDir = random.nextBoolean() ? 1.0D : -1.0D;
		}
	}
	
	private void applyCloudDamage() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		
		damageTimer++;
		if (damageTimer < damageInterval) {
			return;
		}
		
		damageTimer = 0;
		
		Vec3 cloudPos = this.position();
		
		double damageRadiusX;
		double damageRadiusZ;
		
		if (currentXDir != 0) {
			damageRadiusX = cloudDamageLength;
			damageRadiusZ = cloudDamageWidth;
		} else {
			damageRadiusX = cloudDamageWidth;
			damageRadiusZ = cloudDamageLength;
		}
		
		for (Entity entity : serverLevel.getEntitiesOfClass(Entity.class, 
				new net.minecraft.world.phys.AABB(
					cloudPos.x - damageRadiusX, cloudPos.y - cloudDamageRadiusY, cloudPos.z - damageRadiusZ,
					cloudPos.x + damageRadiusX, cloudPos.y + cloudDamageRadiusY, cloudPos.z + damageRadiusZ
				))) {
			
			if (entity == this || entity instanceof StangeCloudEntity || entity instanceof net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity) {
				continue;
			}
			
			DamageSource damageSource = serverLevel.damageSources().inWall();
			entity.hurt(damageSource, cloudDamagePerTick);
		}
	}
	
	private void convertLavaAndWaterToCloudBlocks() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		
		Vec3 cloudPos = this.position();
		
		double checkRadiusX;
		double checkRadiusZ;
		
		if (currentXDir != 0) {
			checkRadiusX = cloudDamageLength;
			checkRadiusZ = cloudDamageWidth;
		} else {
			checkRadiusX = cloudDamageWidth;
			checkRadiusZ = cloudDamageLength;
		}
		
		boolean anyConverted = false;
		
		for (double x = cloudPos.x - checkRadiusX; x <= cloudPos.x + checkRadiusX; x++) {
			for (double y = cloudPos.y - cloudDamageRadiusY; y <= cloudPos.y + cloudDamageRadiusY; y++) {
				for (double z = cloudPos.z - checkRadiusZ; z <= cloudPos.z + checkRadiusZ; z++) {
					BlockPos pos = new BlockPos((int)x, (int)y, (int)z);
					BlockState state = serverLevel.getBlockState(pos);
					
					if (state.is(Blocks.LAVA)) {
						serverLevel.setBlock(pos, McanomalyarchivesModBlocks.CLOUD_STONE.get().defaultBlockState(), 3);
						anyConverted = true;
					} else if (state.is(Blocks.WATER)) {
						serverLevel.setBlock(pos, McanomalyarchivesModBlocks.CLOUD_WATER.get().defaultBlockState(), 3);
						anyConverted = true;
					}
				}
			}
		}
		
		if (anyConverted) {
			serverLevel.playSound(null, cloudPos.x, cloudPos.y, cloudPos.z, 
				SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	public static void init(RegisterSpawnPlacementsEvent event) {
		event.register(McanomalyarchivesModEntities.STANGE_CLOUD.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
				(entityType, world, reason, pos, random) -> {
					if (!(world instanceof ServerLevel serverWorld)) {
						return false;
					}
					
					ResourceKey<Level> dimensionKey = serverWorld.dimension();
					// 维度 id 是 cloud_dem（不是 clouddem）：写错的后果是云之维度里一只伪云都不生成
					ResourceLocation cloudDemLocation = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "cloud_dem");
					ResourceKey<Level> cloudDemKey = ResourceKey.create(Registries.DIMENSION, cloudDemLocation);
					
					// 放宽高度：地面 60~150 即可（配合玩家附近高空生成，见 StangeCloudSpawnHandler）
					boolean isInHeightRange = pos.getY() >= 60 && pos.getY() < 150;
					
					if (dimensionKey.equals(cloudDemKey)) {
						// 云之维度：高概率生成，高度要求
						return isInHeightRange && random.nextFloat() < 0.5f;
					} else if (dimensionKey.equals(Level.OVERWORLD)) {
						// 主世界：低概率生成
						return isInHeightRange && random.nextFloat() < 0.05f;
					}
					
					// 其他维度不生成
					return false;
				}, RegisterSpawnPlacementsEvent.Operation.REPLACE);
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