package net.mcreator.mcanomalyarchives.entity;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModSounds;
import net.mcreator.mcanomalyarchives.entity.ai.PickupWeaponGoal;
import net.mcreator.mcanomalyarchives.entity.ai.SwitchWeaponGoal;
import net.mcreator.mcanomalyarchives.entity.ai.AnbulaRangedAttackGoal;
import net.mcreator.mcanomalyarchives.entity.ai.AnbulaTaczGoal;
import net.mcreator.mcanomalyarchives.entity.ai.AnbulaBlockGoal;
import net.mcreator.mcanomalyarchives.entity.ai.AnbulaChargeGoal;
import net.mcreator.mcanomalyarchives.entity.ai.AnbulaMeleeGoal;
import net.mcreator.mcanomalyarchives.entity.ai.AnbulaDodgeGoal;
import net.mcreator.mcanomalyarchives.entity.ai.WatchCornPoppyGoal;
import net.mcreator.mcanomalyarchives.entity.ai.StayGoal;
import net.mcreator.mcanomalyarchives.entity.ai.FollowPlayerGoal;
import net.mcreator.mcanomalyarchives.entity.ai.ControlledWanderGoal;
import net.mcreator.mcanomalyarchives.entity.ai.AnbulaOwnerHurtByTargetGoal;
import net.mcreator.mcanomalyarchives.entity.ai.AnbulaOwnerHurtTargetGoal;
import net.mcreator.mcanomalyarchives.compat.TaczCompat;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.world.entity.AnimationState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnbulaEntity extends ControllableMonster implements GeoAnimatable {
	private static final Logger LOGGER = LoggerFactory.getLogger(AnbulaEntity.class);
	private static final int BERSERK_COOLDOWN = 1200;
	private static final int BERSERK_ATTACK_RANGE = 16;
	private static final int MAX_WEAPONS = 4;

	private int berserkTicks = 0;
	private int berserkCooldown = 0;
	private boolean isBerserk = false;
	/** 进入狂暴前的移动状态，狂暴结束后恢复（保持跟随等） */
	private int preBerserkState = STATE_WANDER;
	/** 忠诚主人 UUID（金苹果驯服后永久保存，可空） */
	@javax.annotation.Nullable
	private UUID ownerUUID;
	private boolean soundPlayed = false;
	private int threatTicker = 0;

	// ===== 武器背包 =====
	private final List<ItemStack> weaponInventory = new ArrayList<>(MAX_WEAPONS);
	private final List<ItemStack> ammoInventory = new ArrayList<>(MAX_WEAPONS * 2); // 弹药独立背包（供 TACZ reload 消费）
	private final AmmoItemHandler ammoHandler = new AmmoItemHandler();
	private int activeWeaponSlot = -1;

	/** 供 TACZ canReload() → findAndExtractInventoryAmmo() 使用的 IItemHandler */
	private class AmmoItemHandler implements IItemHandler {
		@Override
		public int getSlots() {
			return ammoInventory.size();
		}
		@Override
		public @NotNull ItemStack getStackInSlot(int slot) {
			if (slot < 0 || slot >= ammoInventory.size()) return ItemStack.EMPTY;
			return ammoInventory.get(slot);
		}
		@Override
		public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
			if (slot < 0 || slot >= ammoInventory.size() || stack.isEmpty()) return stack;
			ItemStack existing = ammoInventory.get(slot);
			int limit = stack.getMaxStackSize();
			if (!existing.isEmpty()) {
				if (existing.getItem() != stack.getItem()) return stack;
				limit -= existing.getCount();
			}
			if (limit <= 0) return stack;
			int toInsert = Math.min(stack.getCount(), limit);
			if (!simulate) {
				if (existing.isEmpty()) {
					ammoInventory.set(slot, stack.copyWithCount(toInsert));
				} else {
					existing.grow(toInsert);
				}
			}
			if (toInsert >= stack.getCount()) return ItemStack.EMPTY;
			ItemStack remainder = stack.copy();
			remainder.shrink(toInsert);
			return remainder;
		}
		@Override
		public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (slot < 0 || slot >= ammoInventory.size()) return ItemStack.EMPTY;
			ItemStack stack = ammoInventory.get(slot);
			if (stack.isEmpty()) return ItemStack.EMPTY;
			int toExtract = Math.min(amount, stack.getCount());
			ItemStack result = stack.copyWithCount(toExtract);
			if (!simulate) {
				stack.shrink(toExtract);
				if (stack.isEmpty()) ammoInventory.set(slot, ItemStack.EMPTY);
			}
			return result;
		}
		@Override
		public int getSlotLimit(int slot) {
			return 64;
		}
		@Override
		public boolean isItemValid(int slot, @NotNull ItemStack stack) {
			return true;
		}
	}

	/** 公开弹药处理器供 RegisterCapabilitiesEvent 使用 */
	public IItemHandler getAmmoHandler() {
		return ammoHandler;
	}

	private AnbulaMeleeGoal meleeGoal;

	private long lastBowActionTime = 0;

	// ===== 晕厥状态配置参数 =====
	private static final float FAINT_HEALTH_THRESHOLD = 0.2f;
	private static final int FAINT_RESISTANCE_AMPLIFIER = 1;
	private static final int FAINT_REGEN_AMPLIFIER = 0;
	private static final int FAINT_MAX_DURATION = 1200;

	// ===== 晕厥状态变量 =====
	private static final EntityDataAccessor<Boolean> DATA_FAINT = SynchedEntityData.defineId(AnbulaEntity.class, EntityDataSerializers.BOOLEAN);
	private int faintTicks = 0;

	// ===== 原生动画 =====
	public final AnimationState animationState = new AnimationState();

	// ===== GeckoLib 动画缓存 =====
	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

	public boolean isFaint() {
		return this.entityData.get(DATA_FAINT);
	}

	private void setFaint(boolean faint) {
		this.entityData.set(DATA_FAINT, faint);
	}

	public boolean isBerserk() {
		return isBerserk;
	}

	@Override
	public void checkDespawn() {
		// 驯服后的安布拉永不自动消失
		if (hasOwner()) {
			return;
		}
		super.checkDespawn();
	}

	@javax.annotation.Nullable
	public UUID getOwnerUUID() {
		return ownerUUID;
	}

	public void setOwnerUUID(@javax.annotation.Nullable UUID uuid) {
		this.ownerUUID = uuid;
	}

	public boolean hasOwner() {
		return ownerUUID != null;
	}

	public boolean isOwner(LivingEntity entity) {
		return ownerUUID != null && entity != null && ownerUUID.equals(entity.getUUID());
	}

	// ===== 武器背包公开方法 =====
	public List<ItemStack> getWeaponInventory() {
		return weaponInventory;
	}

	public int getActiveWeaponSlot() {
		return activeWeaponSlot;
	}

	public void setActiveWeaponSlot(int slot) {
		this.activeWeaponSlot = slot;
		syncActiveWeapon();
	}

	// ===== 武器识别 =====

	public static boolean isMeleeWeapon(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) ||
			   stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.HOES);
	}

	public static boolean isBow(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return stack.is(ItemTags.BOW_ENCHANTABLE) && !stack.is(ItemTags.CROSSBOW_ENCHANTABLE);
	}

	public static boolean isCrossbow(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return stack.is(ItemTags.CROSSBOW_ENCHANTABLE);
	}

	public static boolean isShield(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
	}

	public static boolean isWeapon(ItemStack stack) {
		return isMeleeWeapon(stack) || isBow(stack) || isCrossbow(stack) || isShield(stack) || TaczCompat.isGun(stack);
	}

	public boolean hasRangedWeapon() {
		for (ItemStack stack : weaponInventory) {
			if (isBow(stack) || isCrossbow(stack) || TaczCompat.isGun(stack)) return true;
		}
		return false;
	}

	public boolean hasMeleeWeapon() {
		for (ItemStack stack : weaponInventory) {
			if (isMeleeWeapon(stack)) return true;
		}
		return false;
	}

	public boolean hasShield() {
		for (ItemStack stack : weaponInventory) {
			if (isShield(stack)) return true;
		}
		return false;
	}

	public boolean isHoldingRanged() {
		return isBow(this.getMainHandItem()) || isCrossbow(this.getMainHandItem()) || TaczCompat.isGun(this.getMainHandItem());
	}

	// ===== 武器操作 =====

	public boolean addWeapon(ItemStack stack) {
		if (stack.isEmpty() || !isWeapon(stack)) return false;
		if (weaponInventory.size() >= MAX_WEAPONS) return false;
		weaponInventory.add(stack.copy());
		if (activeWeaponSlot == -1) {
			activeWeaponSlot = 0;
		}
		syncActiveWeapon();
		return true;
	}

	/** 仅在狂暴模式才同步武器到主手，非狂暴模式清空主手 */
	private void syncActiveWeapon() {
		if (isBerserk && activeWeaponSlot >= 0 && activeWeaponSlot < weaponInventory.size()) {
			this.setItemSlot(EquipmentSlot.MAINHAND, weaponInventory.get(activeWeaponSlot));
		} else {
			this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	public void dropAllWeapons() {
		if (this.level() instanceof ServerLevel) {
			for (ItemStack stack : weaponInventory) {
				if (!stack.isEmpty()) {
					this.spawnAtLocation(stack.copy());
				}
			}
		}
		weaponInventory.clear();
		activeWeaponSlot = -1;
		this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	/** 找到近战武器中攻击力最高的索引 */
	public int findBestMeleeSlot() {
		int bestSlot = -1;
		double bestDamage = -1;
		for (int i = 0; i < weaponInventory.size(); i++) {
			ItemStack stack = weaponInventory.get(i);
			if (isMeleeWeapon(stack)) {
				double dmg = getAttackDamage(stack);
				if (dmg > bestDamage) {
					bestDamage = dmg;
					bestSlot = i;
				}
			}
		}
		return bestSlot;
	}

	/** 获取近战武器的攻击力属性值 */
	public static double getAttackDamage(ItemStack stack) {
		double total = 0;
		for (var entry : stack.getAttributeModifiers().modifiers()) {
			if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
				total += entry.modifier().amount();
			}
		}
		return total;
	}

	/** 找到第一个远程武器的索引（优先 TACZ 枪械 > 弓 > 弩） */
	public int findRangedSlot() {
		// 优先 TACZ 枪械
		for (int i = 0; i < weaponInventory.size(); i++) {
			if (TaczCompat.isGun(weaponInventory.get(i))) return i;
		}
		for (int i = 0; i < weaponInventory.size(); i++) {
			if (isBow(weaponInventory.get(i))) return i;
		}
		for (int i = 0; i < weaponInventory.size(); i++) {
			if (isCrossbow(weaponInventory.get(i))) return i;
		}
		return -1;
	}

	/** 找到盾牌的索引 */
	public int findShieldSlot() {
		for (int i = 0; i < weaponInventory.size(); i++) {
			if (isShield(weaponInventory.get(i))) return i;
		}
		return -1;
	}

	// 兼容旧方法
	private boolean hasWeapon() {
		return !weaponInventory.isEmpty();
	}

	@Override
	protected boolean canInteract() {
		return !isBerserk && !isFaint();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FAINT, false);
	}

	public AnbulaEntity(EntityType<AnbulaEntity> type, Level world) {
		super(type, world);
		this.xpReward = 5;
		setNoAi(false);
		equipDefaultGear();
	}

	@Override
	public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
			net.minecraft.world.level.ServerLevelAccessor level,
			net.minecraft.world.DifficultyInstance difficulty,
			net.minecraft.world.entity.MobSpawnType spawnType,
			@org.jetbrains.annotations.Nullable net.minecraft.world.entity.SpawnGroupData spawnGroupData) {
		spawnGroupData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
		equipDefaultGear();
		return spawnGroupData;
	}

	private void equipDefaultGear() {
		// 盔甲不渲染但保留属性防护
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
		this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
		this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS));
		this.setItemSlot(EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
		this.setDropChance(EquipmentSlot.HEAD, 0.0F);
		this.setDropChance(EquipmentSlot.CHEST, 0.0F);
		this.setDropChance(EquipmentSlot.LEGS, 0.0F);
		this.setDropChance(EquipmentSlot.FEET, 0.0F);
		// 武器仅在狂暴模式显示，初始清空
		this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		// 武器背包：钻石剑(近战) + AK47(有TACZ时)/弓(无TACZ时)，可随意切换
		if (weaponInventory.isEmpty()) {
			weaponInventory.add(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
			if (TaczCompat.isAvailable()) {
				ItemStack akStack = TaczCompat.createGunStack("tacz:ak47");
				if (!akStack.isEmpty()) {
					int maxAmmo = TaczCompat.getMaxAmmo(akStack);
					TaczCompat.setAmmo(akStack, maxAmmo > 0 ? maxAmmo : 30);
					weaponInventory.add(akStack);
					addAmmoForGun(akStack);
				} else {
					weaponInventory.add(new ItemStack(net.minecraft.world.item.Items.BOW));
				}
			} else {
				weaponInventory.add(new ItemStack(net.minecraft.world.item.Items.BOW));
			}
			activeWeaponSlot = TaczCompat.isAvailable() ? 1 : 0; // 有 TACZ 时默认持枪
		}
	}

	/** 向弹药背包添加 7.62x39 弹药（4 组） */
	private void addAmmoForGun(@SuppressWarnings("unused") ItemStack gunStack) {
		// 尝试多种 ID 格式（TACZ 版本差异）
		String[] ammoIds = {"tacz:762x39", "tacz:762_39", "tacz:762_39mm"};
		for (String id : ammoIds) {
			net.minecraft.world.item.Item ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
			if (ammoItem != net.minecraft.world.item.Items.AIR) {
				for (int i = 0; i < 4; i++) {
					ammoInventory.add(new ItemStack(ammoItem, 64));
				}
				return;
			}
		}
	}

	// ===== GeckoLib 动画控制器 =====
	private boolean swinging = false;

	// ===== 冲锋动画标记 =====
	public boolean chargeSprinting = false;
	public boolean chargeLeaping = false;

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "mainController", 5, this::mainPredicate));
		controllers.add(new AnimationController<>(this, "attackController", 3, this::attackPredicate));
		controllers.add(new AnimationController<>(this, "faintController", 0, this::faintPredicate));
		controllers.add(new AnimationController<>(this, "chargeController", 10, this::chargePredicate));
	}

	private PlayState mainPredicate(software.bernie.geckolib.animation.AnimationState<AnbulaEntity> test) {
		if (isFaint()) return PlayState.STOP;
		if (chargeSprinting || chargeLeaping) return PlayState.STOP;
		if (test.isMoving()) {
			test.setAnimation(RawAnimation.begin().thenLoop("animation.anbula.walk"));
		} else {
			test.setAnimation(RawAnimation.begin().thenLoop("animation.anbula.idle"));
		}
		return PlayState.CONTINUE;
	}

	private PlayState attackPredicate(software.bernie.geckolib.animation.AnimationState<AnbulaEntity> test) {
		if (isFaint()) return PlayState.STOP;
		if (this.swinging) {
			test.setAnimation(RawAnimation.begin().thenPlay("animation.anbula.attack"));
			this.swinging = false;
		}
		return PlayState.CONTINUE;
	}

	private PlayState faintPredicate(software.bernie.geckolib.animation.AnimationState<AnbulaEntity> test) {
		if (isFaint()) {
			test.setAnimation(RawAnimation.begin().thenPlay("animation.anbula.facedown"));
			return PlayState.CONTINUE;
		}
		return PlayState.STOP;
	}

	private PlayState chargePredicate(software.bernie.geckolib.animation.AnimationState<AnbulaEntity> test) {
		if (isFaint()) return PlayState.STOP;
		if (this.chargeLeaping) {
			test.setAnimation(RawAnimation.begin().thenPlay("animation.anbula.leap"));
			return PlayState.CONTINUE;
		}
		if (this.chargeSprinting) {
			test.setAnimation(RawAnimation.begin().thenPlay("animation.anbula.charge"));
			return PlayState.CONTINUE;
		}
		return PlayState.STOP;
	}

	@Override
	public void swing(net.minecraft.world.InteractionHand hand, boolean updateSelf) {
		super.swing(hand, updateSelf);
		if (!this.level().isClientSide()) {
			this.swinging = true;
		}
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.animatableInstanceCache;
	}

	@Override
	public double getTick(Object entity) {
		return this.tickCount;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new AnbulaChargeGoal(this));
		this.goalSelector.addGoal(2, new AnbulaDodgeGoal(this));
		this.goalSelector.addGoal(3, new AnbulaTaczGoal(this));
		this.goalSelector.addGoal(3, new AnbulaRangedAttackGoal(this, 1.0, 20, 15.0F));
		this.meleeGoal = new AnbulaMeleeGoal(this, 1.2, true) {
			@Override
			protected boolean canPerformAttack(LivingEntity entity) {
				return this.isTimeToAttack() && this.mob.distanceToSqr(entity) < (this.mob.getBbWidth() * this.mob.getBbWidth() + entity.getBbWidth()) && this.mob.getSensing().hasLineOfSight(entity);
			}
		};
		this.goalSelector.addGoal(4, this.meleeGoal);
		this.goalSelector.addGoal(5, new SwitchWeaponGoal(this));
		this.goalSelector.addGoal(6, new AnbulaBlockGoal(this));
		this.goalSelector.addGoal(7, new PickupWeaponGoal(this));
		this.goalSelector.addGoal(8, new WatchCornPoppyGoal(this));
		this.goalSelector.addGoal(9, new StayGoal(this));
		this.goalSelector.addGoal(10, new FollowPlayerGoal(this));
		this.goalSelector.addGoal(11, new ControlledWanderGoal(this, 1));
		this.goalSelector.addGoal(12, new MoveTowardsTargetGoal(this, 1.0, 10));
		this.goalSelector.addGoal(13, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(0, new AnbulaOwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(1, new AnbulaOwnerHurtTargetGoal(this));

		this.targetSelector.addGoal(2, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				if (!isBerserk() || isFaint()) return false;
				// 忠诚主人攻击不索敌
				if (this.mob.getLastHurtByMob() instanceof net.minecraft.world.entity.player.Player player && isOwner(player)) {
					return false;
				}
				return super.canUse();
			}
		});

		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 5, false, false, null) {
			@Override
			public boolean canUse() {
				if (!isBerserk() || isFaint()) return false;
				return super.canUse();
			}

			@Override
			protected void findTarget() {
				super.findTarget();
				if (this.target instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().instabuild) {
					this.target = null;
				}
				// 忠诚时排除主人
				if (this.target instanceof net.minecraft.world.entity.player.Player player && isOwner(player)) {
					this.target = null;
				}
			}
		});
	}

	@Override
	public boolean canHoldItem(ItemStack stack) {
		return !isFaint() && isWeapon(stack);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.level().isClientSide() || hand != InteractionHand.MAIN_HAND) {
			return super.mobInteract(player, hand);
		}

		// 非 Shift 右键 + 金苹果：完全回血并驯服为忠诚（永久）
		if (!player.isShiftKeyDown() && player.getItemInHand(hand).is(Items.GOLDEN_APPLE)) {
			if (!player.getAbilities().instabuild) {
				player.getItemInHand(hand).shrink(1);
			}
			this.setHealth(this.getMaxHealth());
			this.setOwnerUUID(player.getUUID());
			// 驯服后立即清除对主人的敌意锁定（避免狂暴中攻击刚驯服的主人）
			if (this.getTarget() != null && this.getTarget().getUUID().equals(player.getUUID())) {
				this.setTarget(null);
			}
			if (this.level() instanceof ServerLevel serverLevel) {
				for (int i = 0; i < 8; i++) {
					double x = this.getX() + (this.getRandom().nextDouble() - 0.5) * 0.8;
					double y = this.getEyeY() + 0.3 + this.getRandom().nextDouble() * 0.6;
					double z = this.getZ() + (this.getRandom().nextDouble() - 0.5) * 0.8;
					serverLevel.sendParticles(ParticleTypes.HEART, x, y, z, 1, 0, 0.15, 0, 0.05);
				}
			}
			this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_BURP, this.getSoundSource(), 1.0f, 1.0f);
			return InteractionResult.SUCCESS;
		}

		return super.mobInteract(player, hand);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		// 受击时进入狂暴模式（忠诚主人攻击除外；创造模式玩家不触发也不锁定）
		if (!isBerserk() && !isFaint() && source.getEntity() instanceof LivingEntity attacker && !isOwner(attacker)) {
			boolean creativeAttacker = attacker instanceof Player p && p.getAbilities().instabuild;
			if (!creativeAttacker) {
				enterBerserk();
				this.setTarget(attacker);
			}
		}
		return super.hurt(source, amount);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hurt = super.doHurtTarget(target);
		if (hurt && meleeGoal != null) {
			meleeGoal.onAttackHit();
		}
		return hurt;
	}

	@Override
	public void tick() {
		super.tick();

		// 客户端动画状态管理
		if (this.level().isClientSide()) {
			if (this.isFaint()) {
				this.animationState.startIfStopped(this.tickCount);
			} else {
				this.animationState.stop();
			}
			return;
		}

		if (isFaint()) {
			faintTicks++;
			if (!this.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
				this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, FAINT_RESISTANCE_AMPLIFIER, false, true));
			}
			this.setDeltaMovement(0, 0, 0);
			if (faintTicks == 1 || faintTicks % 20 == 0) {
				AABB current = this.getBoundingBox();
				double cx = (current.minX + current.maxX) / 2.0;
				double cy = current.minY;
				double cz = (current.minZ + current.maxZ) / 2.0;
				this.setBoundingBox(new AABB(cx - 0.9, cy, cz - 0.9, cx + 0.9, cy + 0.6, cz + 0.9));
			}
			if (faintTicks % 100 == 0) {
				this.heal(1);
			}
			if (this.getHealth() >= this.getMaxHealth() || faintTicks >= FAINT_MAX_DURATION) {
				exitFaint();
			}
			return;
		}

		if (this.getHealth() / this.getMaxHealth() < FAINT_HEALTH_THRESHOLD) {
			enterFaint();
			return;
		}

		if (berserkCooldown > 0) {
			berserkCooldown--;
		}

		if (isBerserk) {
			berserkTicks++;
			applyBerserkEffects();
			// 创造模式玩家不会被狂暴锁定（中途切创造也立即解除锁定）
			if (this.getTarget() instanceof Player creative && creative.getAbilities().instabuild) {
				this.setTarget(null);
			}
			// 周围没有可攻击目标时自动退出狂暴
			if (berserkTicks % 10 == 0 && !hasAttackableTargetNearby()) {
				exitBerserk();
			}
		} else {
			evaluateThreats();
		}
	}

	private void evaluateThreats() {
		if (isBerserk() || isFaint() || berserkCooldown > 0) return;

		threatTicker++;
		if (threatTicker < 20) return;
		threatTicker = 0;

		AABB scanBox = this.getBoundingBox().inflate(12.0);
		List<LivingEntity> threats = this.level().getEntitiesOfClass(LivingEntity.class, scanBox,
				e -> e != this && e.isAlive() && isMajorThreat(e));

		if (!threats.isEmpty()) {
			enterBerserk();
			LivingEntity closest = threats.stream()
					.min((a, b) -> Double.compare(this.distanceToSqr(a), this.distanceToSqr(b)))
					.orElse(null);
			if (closest != null) {
				this.setTarget(closest);
			}
		}
	}

	/** 16 格内是否存在可攻击目标（狂暴持续判定） */
	private boolean hasAttackableTargetNearby() {
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && this.distanceToSqr(target) <= 16.0 * 16.0) return true;
		AABB box = this.getBoundingBox().inflate(16.0);
		return !this.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != this && e.isAlive() && (e instanceof Enemy || isMajorThreat(e))).isEmpty();
	}

	private static boolean isMajorThreat(LivingEntity entity) {
		return entity instanceof net.minecraft.world.entity.animal.IronGolem
				|| entity instanceof net.minecraft.world.entity.monster.warden.Warden
				|| entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
				|| entity instanceof net.minecraft.world.entity.monster.ElderGuardian
				|| entity instanceof net.minecraft.world.entity.monster.Ravager;
	}

	private void enterFaint() {
		if (isBerserk) {
			removeBerserkEffects();
			isBerserk = false;
			berserkTicks = 0;
			berserkCooldown = BERSERK_COOLDOWN;
			stopBerserkSound();
		}
		setFaint(true);
		faintTicks = 0;
		this.setTarget(null);
		this.getNavigation().stop();
		// setNoAi(true) 不会回调运行中 goal 的 stop()，需手动复位冲锋标志，
		// 否则晕厥/苏醒后 charge 动画会残留播放
		this.chargeSprinting = false;
		this.chargeLeaping = false;
		this.setNoAi(true);
		this.setDeltaMovement(0, 0, 0);
		syncActiveWeapon(); // 非狂暴收起主手，武器保留在背包
	}

	private void exitFaint() {
		setFaint(false);
		faintTicks = 0;
		this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		this.setNoAi(false);
	}

	/** 进入狂暴模式（主人目标 AI 也会调用，故公开） */
	public void enterBerserk() {
		if (isFaint()) {
			exitFaint();
		}
		if (!isBerserk) {
			preBerserkState = getMobState(); // 保存狂暴前的状态，结束后恢复
		}
		isBerserk = true;
		berserkTicks = 0;
		soundPlayed = false;
		setMobState(STATE_WANDER);
		syncActiveWeapon(); // 狂暴模式显示武器
		applyBerserkEffects();
		playBerserkSound();
	}

	private void exitBerserk() {
		isBerserk = false;
		berserkTicks = 0;
		berserkCooldown = BERSERK_COOLDOWN;
		soundPlayed = false;
		setMobState(preBerserkState); // 恢复狂暴前的移动状态（跟随/停留/闲逛）
		removeBerserkEffects();
		this.setTarget(null);
		stopBerserkSound();
		syncActiveWeapon(); // 收起主手武器（保留在背包，不掉落）
	}

	private void stopBerserkSound() {
		if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			try {
				ResourceLocation soundLocation = McanomalyarchivesModSounds.ANBULA_BERSERK.getId();
				ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(soundLocation, this.getSoundSource());
				for (ServerPlayer player : serverLevel.getPlayers(p -> p.distanceToSqr(this) <= 256.0)) {
					player.connection.send(packet);
				}
			} catch (Exception e) {
				LOGGER.warn("Failed to stop berserk sound", e);
			}
		}
	}

	private void applyBerserkEffects() {
		if (!this.hasEffect(MobEffects.MOVEMENT_SPEED)) {
			this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 3, false, true));
		}
		if (!this.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
			this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 1, false, true));
		}
		if (!this.hasEffect(MobEffects.DAMAGE_BOOST)) {
			this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, -1, 1, false, true));
		}
	}

	private void removeBerserkEffects() {
		this.removeEffect(MobEffects.MOVEMENT_SPEED);
		this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		this.removeEffect(MobEffects.DAMAGE_BOOST);
	}

	private void playBerserkSound() {
		if (!soundPlayed) {
			soundPlayed = true;
			SoundEvent soundEvent;
			try {
				soundEvent = McanomalyarchivesModSounds.ANBULA_BERSERK.get();
			} catch (Exception e) {
				soundEvent = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.generic.hurt"));
				if (soundEvent == null) {
					return;
				}
			}
			this.level().playSound(null, this.blockPosition(),
				soundEvent,
				this.getSoundSource(), 1.0f, 1.0f);
		}
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity entity) {
		return super.getPassengerRidingPosition(entity).add(0, -0.35F, 0);
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
	public void die(DamageSource source) {
		if (!this.level().isClientSide()) {
			dropAllWeapons();
			if (isBerserk) {
				isBerserk = false;
				berserkTicks = 0;
				removeBerserkEffects();
			}
		}
		stopBerserkSound();
		super.die(source);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("IsFaint", isFaint() ? 1 : 0);
		tag.putInt("FaintTicks", faintTicks);
		tag.putInt("ActiveWeaponSlot", activeWeaponSlot);
		ListTag listTag = new ListTag();
		var registries = this.level().registryAccess();
		for (ItemStack stack : weaponInventory) {
			listTag.add(stack.save(registries, new CompoundTag()));
		}
		tag.put("WeaponInventory", listTag);
		ListTag ammoTag = new ListTag();
		for (ItemStack stack : ammoInventory) {
			ammoTag.add(stack.save(registries, new CompoundTag()));
		}
		tag.put("AmmoInventory", ammoTag);

		if (ownerUUID != null) {
			tag.putLong("AnbulaOwnerMost", ownerUUID.getMostSignificantBits());
			tag.putLong("AnbulaOwnerLeast", ownerUUID.getLeastSignificantBits());
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		int faintVal = tag.getInt("IsFaint");
		if (faintVal == 1) {
			setFaint(true);
			faintTicks = tag.getInt("FaintTicks");
		} else {
			setFaint(false);
			faintTicks = 0;
		}

		weaponInventory.clear();
		ListTag listTag = tag.getList("WeaponInventory", Tag.TAG_COMPOUND);
		var registries = this.level().registryAccess();
		for (int i = 0; i < listTag.size(); i++) {
			ItemStack stack = ItemStack.parseOptional(registries, listTag.getCompound(i));
			if (!stack.isEmpty()) {
				weaponInventory.add(stack);
			}
		}
		activeWeaponSlot = tag.contains("ActiveWeaponSlot") ? tag.getInt("ActiveWeaponSlot") : -1;
		if (activeWeaponSlot >= 0 && activeWeaponSlot < weaponInventory.size()) {
			syncActiveWeapon();
		}

		ammoInventory.clear();
		if (tag.contains("AmmoInventory")) {
			ListTag ammoTag = tag.getList("AmmoInventory", Tag.TAG_COMPOUND);
			for (int i = 0; i < ammoTag.size(); i++) {
				ItemStack stack = ItemStack.parseOptional(registries, ammoTag.getCompound(i));
				if (!stack.isEmpty()) {
					ammoInventory.add(stack);
				}
			}
		}

		if (tag.contains("AnbulaOwnerMost") && tag.contains("AnbulaOwnerLeast")) {
			ownerUUID = new UUID(tag.getLong("AnbulaOwnerMost"), tag.getLong("AnbulaOwnerLeast"));
		} else {
			ownerUUID = null;
		}
	}

	public static void init(RegisterSpawnPlacementsEvent event) {
		event.register(McanomalyarchivesModEntities.ANBULA.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(entityType, world, reason, pos, random) -> (world.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && world.getRawBrightness(pos, 0) > 8), RegisterSpawnPlacementsEvent.Operation.REPLACE);
	}

	public static AttributeSupplier.Builder createAttributes() {
		AttributeSupplier.Builder builder = Mob.createMobAttributes();
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3);
		builder = builder.add(Attributes.MAX_HEALTH, 100);
		builder = builder.add(Attributes.ARMOR, 0);
		builder = builder.add(Attributes.ATTACK_DAMAGE, 7);
		builder = builder.add(Attributes.FOLLOW_RANGE, 32);
		builder = builder.add(Attributes.STEP_HEIGHT, 0.6);
		builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
		return builder;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		if (isFaint()) return false;
		if (isOwner(target)) return false;
		if (isBerserk()) {
			return target.isAlive() && target != this && !(target instanceof AnbulaEntity);
		}
		return false;
	}
}
