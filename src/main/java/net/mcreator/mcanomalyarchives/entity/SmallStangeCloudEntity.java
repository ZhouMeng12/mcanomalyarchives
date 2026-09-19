package net.mcreator.mcanomalyarchives.entity;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;

/**
 * 小伪云（UO-001 衍生个体）—— 从云室传送门里飘出来的那一小片云。
 *
 * <p>作者 2026-09-16 定的规格：「等比缩小，短的横边是 2 格，效果和正常的伪云一样」。
 * 尺寸推导（详见 {@code docs/design/uo-001-small-cloud.md}）：
 * 大伪云用同一个模型，短横边 0.75 格 × 缩放 20 = 15 格；
 * 这里要 2 格 → 缩放 = 20 × (2/15) = {@link #RENDER_SCALE}，
 * 于是长 4.0、高 0.667、短 2.0 格。
 *
 * <p><b>与大伪云的关系</b>：同一套模型与贴图、同一套免疫与"不是生物而是一种现象"的设定，
 * 但它是**碎片**——飘 60 秒后自己散掉（大伪云永不消失）。
 *
 * <p><b>确定性</b>：大伪云在 {@code aiStep} 里用 {@code getRandom()} 换向，客户端与服务端各掷各的骰子，
 * 慢速漂移下看不出来但确实会互相拉。这里改成**由 UUID 与 tickCount 推导**的伪随机，
 * 两端算出来完全一样，不需要额外的网络包。
 */
public class SmallStangeCloudEntity extends Monster {

	// ==================== 尺寸（推导见类注释） ====================

	/** 渲染缩放：0.75 格（模型短横边）× 2.6667 = 2.0 格。与大伪云的 ×20 是同一个模型。 */
	public static final float RENDER_SCALE = 2.6667f;
	/** 视觉长边（格）：模型 1.5 格 × 缩放 */
	public static final float VISUAL_LENGTH = 4.0f;
	/** 视觉高度（格）：模型 0.25 格 × 缩放 */
	public static final float VISUAL_HEIGHT = 0.667f;
	/** 视觉短横边（格）：作者指定的 2 格 */
	public static final float VISUAL_SHORT_EDGE = 2.0f;

	// ==================== 出场：被门"吐"出来 ====================

	/** 出场阶段时长：约 2 秒 */
	private static final int LAUNCH_TICKS = 40;
	/** 出场初速（格/tick） */
	private static final double LAUNCH_SPEED = 0.06;
	/** 出场速度衰减 */
	private static final double LAUNCH_DECAY = 0.92;
	/** 慢到这个速度以下就交给巡航 */
	private static final double LAUNCH_MIN_SPEED = 0.005;

	// ==================== 巡航 ====================

	/** 水平速度系数（乘在 FLYING_SPEED 上）：大伪云是 0.2 */
	private static final double DRIFT_SPEED_FACTOR = 0.15;
	/**
	 * 过门冷却（tick）：原版在传送时 {@code setPortalCooldown()} 用的就是这个值。
	 * 取 1400（= 寿命 1200 再加 200）→ **一只云一生只过一次门**，
	 * 不会在两侧门之间反复弹（作者 2026-09-18 要的"用冷却防反复出入门"）。
	 * （这里写字面量而不是 LIFETIME_TICKS + 200：静态字段不能前向引用。）
	 */
	private static final int PORTAL_COOLDOWN_TICKS = 1400;

	/**
	 * 出生后的"不许过门"宽限期（tick）。
	 *
	 * <p>5 秒 —— 云速 0.045/tick，5 秒走 4.5 格，足以让 4 格宽的碰撞箱离开它出生的那扇门。
	 */
	public static final int PORTAL_GRACE_TICKS = 100;

	// ==================== 收场 ====================

	/** 寿命：60 秒（作者 2026-09-16 定），到点散掉 */
	public static final int LIFETIME_TICKS = 1200;
	/** 散掉时放几颗云粒子 */
	private static final int DISSOLVE_PARTICLES = 12;

	// ==================== 对周围的影响：大伪云的 2/15 ====================

	/** 伤害盒水平半径（长边方向）：大伪云 15 → 2.0 */
	private static final double EFFECT_LENGTH = 2.0;
	/** 伤害盒水平半径（短边方向）：大伪云 7 → 0.933 */
	private static final double EFFECT_WIDTH = 0.933;
	/** 伤害盒垂直半径：大伪云 3 → 0.4 */
	private static final double EFFECT_RADIUS_Y = 0.4;
	/** 单次伤害：与大伪云一致（作者："效果和正常的伪云一样"，只缩范围） */
	private static final float DAMAGE_PER_HIT = 1.0f;
	/** 伤害间隔（tick）：与大伪云一致 */
	private static final int DAMAGE_INTERVAL = 10;

	/** 固定高度巡航的基准高度（出生时锁定） */
	protected double fixedYLevel = 60.0D;
	/** 出场阶段结束、进入巡航 */
	private boolean cruising = false;

	/** 子类（黑吸伪云）要看这个标记 */
	protected boolean isCruising() {
		return cruising;
	}

	/**
	 * 跳过"被门吐出来"的出场阶段，一出生就进入水平巡航吗？
	 *
	 * <p>黑吸伪云覆盖成 true：作者 2026-09-18「黑吸云移动也要像伪云一样水平移动」——
	 * 出场阶段带初速衰减、看起来像被弹出来，而黑吸云要的是跟伪云同款的水平匀速漂移。
	 */
	protected boolean skipLaunchPhase() {
		return false;
	}
	private int launchTick = 0;
	private double launchSpeed = 0.0D;
	/** 水平方向（永远是轴对齐的单位向量，与大伪云同一套约定） */
	protected Vec3 heading = Vec3.ZERO;
	/** 出生时锁高度用的标记 */
	private boolean isFirstSpawn = true;
	private int damageTimer = 0;

	public SmallStangeCloudEntity(EntityType<? extends SmallStangeCloudEntity> type, Level world) {
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
		// 与大伪云一样：位置全靠 aiStep 自己算，不注册导航类 AI Goal（否则会和 setPos 打架、抽搐）
	}

	// ==================== 永不自然消失（寿命由自己管） ====================

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
		// 完全禁用原版消失逻辑；到寿命由 dissolve() 自己收场
	}

	// ==================== 免疫（与大伪云一致） ====================

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
		if (damagesource.getDirectEntity() instanceof AbstractArrow)
			return false;
		if (damagesource.getDirectEntity() instanceof Player)
			return false;
		if (damagesource.getDirectEntity() instanceof ThrownPotion || damagesource.getDirectEntity() instanceof AreaEffectCloud)
			return false;
		if (damagesource.is(DamageTypes.IN_FIRE) || damagesource.is(DamageTypes.ON_FIRE) || damagesource.is(DamageTypes.LAVA))
			return false;
		if (damagesource.is(DamageTypes.FALL) || damagesource.is(DamageTypes.CACTUS) || damagesource.is(DamageTypes.DROWN))
			return false;
		if (damagesource.is(DamageTypes.LIGHTNING_BOLT) || damagesource.is(DamageTypes.TRIDENT) || damagesource.is(DamageTypes.FALLING_ANVIL))
			return false;
		if (damagesource.is(DamageTypes.EXPLOSION) || damagesource.is(DamageTypes.PLAYER_EXPLOSION))
			return false;
		if (damagesource.is(DamageTypes.DRAGON_BREATH) || damagesource.is(DamageTypes.WITHER) || damagesource.is(DamageTypes.WITHER_SKULL))
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
	protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
	}

	@Override
	public void setNoGravity(boolean ignored) {
		super.setNoGravity(true);
	}

	// ==================== 运动 ====================

	/**
	 * 朝向 ↔ 水平方向的对应（与大伪云同一套约定：沿 X 走用 yaw 0/180，沿 Z 走用 yaw ±90）。
	 *
	 * <p>之所以要"从朝向反推方向"：出场方向是生成时由 {@code SmallCloudEmitter} 按门口法线定的，
	 * 而实体朝向会随生成包同步到客户端，于是**两端不需要额外的网络包就能算出同一个方向**。
	 */
	public static float yawFor(double dx, double dz) {
		if (dx > 0.0D)
			return 0f;
		if (dx < 0.0D)
			return 180f;
		if (dz > 0.0D)
			return 90f;
		if (dz < 0.0D)
			return -90f;
		return 0f;
	}

	/** {@link #yawFor} 的逆：由朝向还原轴对齐的水平方向。 */
	public static Vec3 headingFromYaw(float yaw) {
		float y = net.minecraft.util.Mth.wrapDegrees(yaw);
		if (y > -45f && y <= 45f)
			return new Vec3(1.0D, 0.0D, 0.0D);
		if (y > 45f && y <= 135f)
			return new Vec3(0.0D, 0.0D, 1.0D);
		if (y > 135f || y <= -135f)
			return new Vec3(-1.0D, 0.0D, 0.0D);
		return new Vec3(0.0D, 0.0D, -1.0D);
	}

	@Override
	public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("FirstSpawn", isFirstSpawn);
		tag.putDouble("FixedYLevel", fixedYLevel);
		tag.putBoolean("Cruising", cruising);
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
		if (tag.contains("Cruising")) {
			// 存档里的云直接进入巡航，不用重演"被门吐出来"
			cruising = tag.getBoolean("Cruising");
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		this.setNoGravity(true);

		if (isFirstSpawn) {
			fixedYLevel = this.getY();
			isFirstSpawn = false;
		}
		if (heading == Vec3.ZERO) {
			heading = headingFromYaw(this.getYRot());
		}
		if (!cruising && skipLaunchPhase()) {
			cruising = true;
		}

		double newX = this.getX();
		double newZ = this.getZ();

		if (!cruising) {
			// ── 出场：沿门口法线被吐出来，速度衰减 ──
			launchSpeed = launchTick == 0 ? LAUNCH_SPEED : launchSpeed * LAUNCH_DECAY;
			newX += heading.x * launchSpeed;
			newZ += heading.z * launchSpeed;
			launchTick++;
			if (launchTick >= LAUNCH_TICKS || launchSpeed < LAUNCH_MIN_SPEED) {
				cruising = true;
			}
		} else {
			// ── 巡航：**永远不换向**，就一直沿着出场那个轴平移（作者 2026-09-18 定）──
			double speed = this.getAttributeValue(Attributes.FLYING_SPEED) * DRIFT_SPEED_FACTOR;
			newX += heading.x * speed;
			newZ += heading.z * speed;
		}

		// 高度恒定：这是"碎片"，只沿 X 或 Z 一个轴平移（作者 2026-09-18：不要上下飘）。
		// 与大伪云一样单次 setPos，避免位置被更新两遍导致抽搐。
		this.setPos(newX, fixedYLevel, newZ);

		tickCloudEffects();
		dissolveWhenExpired();
	}

	/**
	 * 还在"被门吐出来"的阶段吗（出场前 40 tick）。
	 *
	 * <p>两处用到它：子类黑吸伪云用它决定"别一出生就干活"；通行 mixin 用它挡住
	 * **刚出生那一下的过门** —— 云是在门口生成的，4×4 的碰撞箱一开始就压在门方块上，
	 * 不放行的话它会被原版传送逻辑当场吸走（2026-09-18 的"云刚出来就传回去了"）。
	 */
	public boolean isLaunching() {
		return !cruising;
	}

	/**
	 * 每 tick 的"对周围的影响"。默认就是伪云那一套：伤害盒 + 把水与岩浆染成云水云石。
	 *
	 * <p>黑吸伪云（子类）把它整个换掉：不伤害、不染水，改成"碰到方块就吞一口然后消散"。
	 * 之所以留成钩子而不是在父类里 if：两种云的"影响"是互斥的两套，覆盖比分支清楚。
	 */
	/**
	 * 已经活够"不许过门"的宽限期了吗？
	 *
	 * <p>云是在门口生成的、碰撞箱又有 4 格宽，地一时间必然压在门方块上；
	 * 不放行的话原版传送逻辑会把刚出门的云当场吸到另一边（2026-09-18 的"云刚出来就传回去了"）。
	 * 5 秒之后它已经离开门 4 格以上，这时"自己飘进传送门"才是作者要的行为。
	 */
	public boolean mayUsePortalNow() {
		return this.tickCount >= PORTAL_GRACE_TICKS;
	}

	protected void tickCloudEffects() {
		// 普通小伪云碰到虞美人会"吸走"那朵花的异常、自己变红（作者 2026-09-18 定）
		if (canAbsorbPoppy() && this.level() instanceof ServerLevel serverLevel) {
			BlockPos poppy = findCornPoppy(serverLevel);
			if (poppy != null) {
				absorbPoppyAndTurnRed(serverLevel, poppy);
				return;
			}
		}
		applyCloudDamage();
		convertLavaAndWaterToCloudBlocks();
	}

	/**
	 * 会吸虞美人吗？只有**普通小伪云**会。
	 *
	 * <p>黑吸伪云整个覆盖了 {@link #tickCloudEffects()}（它不吸花）；红伪云覆盖这个方法返回 false ——
	 * 否则它碰到下一朵虞美人就会再变一次红，变成没完没了的复制。
	 */
	protected boolean canAbsorbPoppy() {
		return true;
	}

	/** 身体范围内有没有虞美人（花没有碰撞箱，所以按方块类型查，不看碰撞）。 */
	protected BlockPos findCornPoppy(ServerLevel level) {
		AABB body = bodyBox();
		for (BlockPos pos : BlockPos.betweenClosed(
				BlockPos.containing(body.minX, body.minY, body.minZ),
				BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
			if (level.getBlockState(pos).is(McanomalyarchivesModBlocks.CORN_POPPY.get())) {
				return pos.immutable();
			}
		}
		return null;
	}

	/**
	 * 吸走虞美人：那朵异常虞美人**变回普通虞美人**（原版罂粟），
	 * 云自己变成 {@link RedStangeCloudEntity}（红伪云），运动状态原样交接。
	 */
	protected void absorbPoppyAndTurnRed(ServerLevel level, BlockPos poppyPos) {
		level.setBlock(poppyPos, Blocks.POPPY.defaultBlockState(), 3);
		level.sendParticles(ParticleTypes.CRIMSON_SPORE,
				poppyPos.getX() + 0.5D, poppyPos.getY() + 0.5D, poppyPos.getZ() + 0.5D, 12, 0.3D, 0.3D, 0.3D, 0.01D);
		level.playSound(null, poppyPos, SoundEvents.GRASS_PLACE, SoundSource.NEUTRAL, 0.8F, 1.4F);
		RedStangeCloudEntity red = new RedStangeCloudEntity(McanomalyarchivesModEntities.RED_STANGE_CLOUD.get(), level);
		handOffMotionTo(red);
		level.addFreshEntity(red);
		this.discard();
	}

	/**
	 * 把运动状态原样交给"接替者"（小伪云变红伪云时用）：
	 * 位置、朝向、锁定高度、方向、有没有走完出场阶段，还有已经活了多久（寿命接着算）。
	 */
	protected void handOffMotionTo(SmallStangeCloudEntity next) {
		next.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
		next.setYHeadRot(this.getYHeadRot());
		next.fixedYLevel = this.fixedYLevel;
		next.heading = this.heading;
		next.cruising = this.cruising;
		next.launchTick = this.launchTick;
		next.launchSpeed = this.launchSpeed;
		next.isFirstSpawn = false;
		next.tickCount = this.tickCount;
	}

	/** 云体实际占的那块空间：长边沿运动方向、短边横着，垂直方向对齐视觉（悬在原点下方）。 */
	protected AABB bodyBox() {
		double alongX = Math.abs(heading.x) > 0.5D ? VISUAL_LENGTH / 2.0D : VISUAL_SHORT_EDGE / 2.0D;
		double alongZ = Math.abs(heading.z) > 0.5D ? VISUAL_LENGTH / 2.0D : VISUAL_SHORT_EDGE / 2.0D;
		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();
		return new AABB(x - alongX, y - VISUAL_HEIGHT, z - alongZ, x + alongX, y, z + alongZ);
	}

	/**
	 * 过门冷却：原版 {@code Entity.setPortalCooldown()} 会把冷却设成这个值，
	 * 这里拉到比寿命还长 → 一只小伪云一生只过一次门（防止在两个门之间反复弹）。
	 */
	@Override
	public int getDimensionChangingDelay() {
		return PORTAL_COOLDOWN_TICKS;
	}

	// ==================== 对周围的影响（大伪云的等比缩小版） ====================

	/** 命中的盒子：长边沿当前运动方向，短边横着，与大伪云同一套摆法。 */
	protected net.minecraft.world.phys.AABB effectBox() {
		double alongX = Math.abs(heading.x) > 0.5D ? EFFECT_LENGTH : EFFECT_WIDTH;
		double alongZ = Math.abs(heading.z) > 0.5D ? EFFECT_LENGTH : EFFECT_WIDTH;
		return new net.minecraft.world.phys.AABB(
				this.getX() - alongX, this.getY() - EFFECT_RADIUS_Y, this.getZ() - alongZ,
				this.getX() + alongX, this.getY() + EFFECT_RADIUS_Y, this.getZ() + alongZ);
	}

	private void applyCloudDamage() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (++damageTimer < DAMAGE_INTERVAL) {
			return;
		}
		damageTimer = 0;
		DamageSource source = serverLevel.damageSources().inWall();
		for (Entity entity : serverLevel.getEntitiesOfClass(Entity.class, effectBox())) {
			// 大伪云与小伪云互相排除（门吐出来的小云不该被旁边的大云每 tick 打）
			if (entity instanceof StangeCloudEntity || entity instanceof SmallStangeCloudEntity) {
				continue;
			}
			entity.hurt(source, DAMAGE_PER_HIT);
		}
	}

	/** 与大伪云同样的效果：范围内的水与岩浆被染成云水 / 云石。 */
	private void convertLavaAndWaterToCloudBlocks() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		net.minecraft.world.phys.AABB box = effectBox();
		boolean anyConverted = false;
		for (BlockPos pos : BlockPos.betweenClosed(
				BlockPos.containing(box.minX, box.minY, box.minZ),
				BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			BlockState state = serverLevel.getBlockState(pos);
			if (state.is(Blocks.LAVA)) {
				serverLevel.setBlock(pos, McanomalyarchivesModBlocks.CLOUD_STONE.get().defaultBlockState(), 3);
				anyConverted = true;
			} else if (state.is(Blocks.WATER)) {
				serverLevel.setBlock(pos, McanomalyarchivesModBlocks.CLOUD_WATER.get().defaultBlockState(), 3);
				anyConverted = true;
			}
		}
		if (anyConverted) {
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.2F);
		}
	}

	// ==================== 收场 ====================

	/** 到寿命就散掉：放一圈云粒子然后 discard（服务端做，客户端跟随消失）。 */
	private void dissolveWhenExpired() {
		if (this.tickCount < LIFETIME_TICKS) {
			return;
		}
		if (this.level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.CLOUD,
					this.getX(), this.getY(), this.getZ(),
					DISSOLVE_PARTICLES, VISUAL_SHORT_EDGE, VISUAL_HEIGHT, VISUAL_SHORT_EDGE, 0.02D);
			serverLevel.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.WOOL_BREAK,
					net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 0.8F);
			this.discard();
		}
	}

	// ==================== 注册 ====================

	/**
	 * 生成规则：**永不自然生成**（元素里 {@code spawnThisMob=false}，连 biome_modifier 都不产出）。
	 * 这里仍然注册一条"一律失败"的规则，作为万一被塞进生成表时的双保险
	 * —— 大伪云当年就是因为 30 格宽的生成 AABB 横跨邻接区块炸过世界生成。
	 */
	public static void init(RegisterSpawnPlacementsEvent event) {
		event.register(net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities.SMALL_STANGE_CLOUD.get(),
				SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
				(entityType, world, reason, pos, random) -> false, RegisterSpawnPlacementsEvent.Operation.REPLACE);
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
