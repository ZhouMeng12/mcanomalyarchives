package net.mcreator.mcanomalyarchives.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerLevel;

import net.mcreator.mcanomalyarchives.entity.AnbulaEntity;
import net.mcreator.mcanomalyarchives.compat.TaczCompat;

import java.util.EnumSet;
import java.util.function.Predicate;
import java.util.List;

public class PickupWeaponGoal extends Goal {
	private final Monster mob;
	private final Level level;
	private ItemEntity targetWeapon;
	private final double speed;
	private final PathNavigation navigation;
	private final Predicate<ItemEntity> weaponSelector;
	private static final int SEARCH_RANGE = 16;
	private static final double PICKUP_RANGE = 1.5;
	private static final int REPATH_INTERVAL = 20;
	private int ticksSinceLastRepath;

	public PickupWeaponGoal(Monster mob) {
		this(mob, 1.0);
	}

	public PickupWeaponGoal(Monster mob, double speed) {
		this.mob = mob;
		this.level = mob.level();
		this.speed = speed;
		this.navigation = mob.getNavigation();
		this.ticksSinceLastRepath = 0;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));

		this.weaponSelector = itemEntity -> {
			ItemStack stack = itemEntity.getItem();
			return AnbulaEntity.isWeapon(stack);
		};
	}

	@Override
	public boolean canUse() {
		if (mob instanceof AnbulaEntity anbula && anbula.isFaint()) {
			return false;
		}

		targetWeapon = findNearestWeapon();
		return targetWeapon != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (targetWeapon == null || !targetWeapon.isAlive()) {
			return false;
		}

		if (mob instanceof AnbulaEntity anbula && anbula.isFaint()) {
			return false;
		}

		double dist = mob.distanceToSqr(targetWeapon);
		return dist > PICKUP_RANGE * PICKUP_RANGE;
	}

	@Override
	public void start() {
		if (targetWeapon != null) {
			navigation.setMaxVisitedNodesMultiplier(2.0f);
			// 立即开始寻路，避免等 tick() 内的 REPATH_INTERVAL(20) 才首次移动（原实现有 1 秒空窗）
			moveToWeapon();
		}
	}

	@Override
	public void tick() {
		if (targetWeapon == null || !targetWeapon.isAlive()) {
			return;
		}

		mob.getLookControl().setLookAt(targetWeapon.position());

		double dist = mob.distanceToSqr(targetWeapon);
		if (dist <= PICKUP_RANGE * PICKUP_RANGE) {
			if (mob.isAlive() && !mob.isRemoved()) {
				ItemStack stack = targetWeapon.getItem().copy();
				mob.onItemPickup(targetWeapon);

				if (mob instanceof AnbulaEntity anbula) {
					if (!tryAddOrUpgrade(anbula, stack)) {
						// 无法加入背包，不拾取
						targetWeapon = null;
						return;
					}
				} else {
					// 非安布拉实体，直接装备主手
					mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
				}

				mob.take(targetWeapon, 1);
				targetWeapon.discard();
			}
			targetWeapon = null;
			return;
		}

		ticksSinceLastRepath++;
		if (ticksSinceLastRepath >= REPATH_INTERVAL) {
			ticksSinceLastRepath = 0;
			moveToWeapon();
		}
	}

	/**
	 * 尝试将武器加入安布拉的武器背包。
	 * 如果背包未满，直接加入。如果已满，尝试升级替换。
	 * @return true 如果成功加入/替换
	 */
	private boolean tryAddOrUpgrade(AnbulaEntity anbula, ItemStack newWeapon) {
		var inventory = anbula.getWeaponInventory();

		// 背包未满，直接加入
		if (inventory.size() < 4) {
			return anbula.addWeapon(newWeapon);
		}

		// 背包已满，尝试升级替换
		return tryUpgradeReplace(anbula, newWeapon);
	}

	/**
	 * 寻找背包中同类型但属性更低的武器，用新武器替换。
	 */
	private boolean tryUpgradeReplace(AnbulaEntity anbula, ItemStack newWeapon) {
		var inventory = anbula.getWeaponInventory();
		int worstIndex = -1;
		double worstScore = Double.MAX_VALUE;

		for (int i = 0; i < inventory.size(); i++) {
			ItemStack existing = inventory.get(i);

			// 必须是同类型武器
			if (!isSameType(newWeapon, existing)) continue;

			double existingScore = getWeaponScore(existing);
			double newScore = getWeaponScore(newWeapon);

			// 新武器比现有的差，跳过
			if (newScore <= existingScore) continue;

			// 找同类型中最差的
			if (existingScore < worstScore) {
				worstScore = existingScore;
				worstIndex = i;
			}
		}

		if (worstIndex >= 0) {
			// 丢弃旧武器，放入新武器
			ItemStack oldWeapon = inventory.get(worstIndex);
			if (anbula.level() instanceof ServerLevel) {
			anbula.spawnAtLocation(oldWeapon.copy());
		}
			inventory.set(worstIndex, newWeapon.copy());
			// 如果当前激活的槽位被替换，保持不变；否则切换到新武器
			if (anbula.getActiveWeaponSlot() == worstIndex) {
				anbula.setActiveWeaponSlot(worstIndex); // 触发 sync
			}
			return true;
		}

		return false; // 没有可替换的
	}

	private boolean isSameType(ItemStack a, ItemStack b) {
		// TACZ 枪械之间可以替换
		if (TaczCompat.isGun(a) && TaczCompat.isGun(b)) return true;
		if (AnbulaEntity.isMeleeWeapon(a) && AnbulaEntity.isMeleeWeapon(b)) {
			// 进一步细分：剑 vs 斧等，同子标签才可替换
			boolean aSword = a.is(ItemTags.SWORDS);
			boolean bSword = b.is(ItemTags.SWORDS);
			if (aSword && bSword) return true;

			boolean aAxe = a.is(ItemTags.AXES);
			boolean bAxe = b.is(ItemTags.AXES);
			if (aAxe && bAxe) return true;

			boolean aPickaxe = a.is(ItemTags.PICKAXES);
			boolean bPickaxe = b.is(ItemTags.PICKAXES);
			if (aPickaxe && bPickaxe) return true;

			boolean aHoe = a.is(ItemTags.HOES);
			boolean bHoe = b.is(ItemTags.HOES);
			if (aHoe && bHoe) return true;

			return false;
		}
		if (AnbulaEntity.isBow(a) && AnbulaEntity.isBow(b)) return true;
		if (AnbulaEntity.isCrossbow(a) && AnbulaEntity.isCrossbow(b)) return true;
		if (AnbulaEntity.isShield(a) && AnbulaEntity.isShield(b)) return true;
		return false;
	}

	private double getWeaponScore(ItemStack stack) {
		if (AnbulaEntity.isMeleeWeapon(stack)) {
			return AnbulaEntity.getAttackDamage(stack);
		}
		// TACZ 枪械：弹药越多分数越高（满弹>残弹）
		if (TaczCompat.isGun(stack)) {
			return 10.0 + TaczCompat.getAmmoCount(stack);
		}
		// 弓、弩、盾牌 — 同类型之间无属性差异，分数相同（仅比较存在性）
		return 1.0;
	}

	@Override
	public void stop() {
		targetWeapon = null;
		navigation.setMaxVisitedNodesMultiplier(1.0f);
		navigation.stop();
	}

	private ItemEntity findNearestWeapon() {
		BlockPos mobPos = mob.blockPosition();
		ItemEntity closest = null;
		double closestDist = Double.MAX_VALUE;

		List<ItemEntity> nearbyItems = level.getEntitiesOfClass(ItemEntity.class,
			new net.minecraft.world.phys.AABB(
				mobPos.getX() - SEARCH_RANGE,
				mobPos.getY() - SEARCH_RANGE / 2,
				mobPos.getZ() - SEARCH_RANGE,
				mobPos.getX() + SEARCH_RANGE,
				mobPos.getY() + SEARCH_RANGE / 2,
				mobPos.getZ() + SEARCH_RANGE
			));

		for (ItemEntity item : nearbyItems) {
			if (weaponSelector.test(item)) {
				double dist = mob.distanceToSqr(item);
				if (dist < closestDist) {
					closestDist = dist;
					closest = item;
				}
			}
		}

		return closest;
	}

	private void moveToWeapon() {
		if (targetWeapon == null) return;

		Path path = navigation.createPath(targetWeapon, 0);
		if (path != null && path.canReach()) {
			navigation.moveTo(path, speed);
		} else {
			navigation.moveTo(targetWeapon.getX(), targetWeapon.getY(), targetWeapon.getZ(), speed);
		}
	}
}
