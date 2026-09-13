package net.mcreator.mcanomalyarchives.anomaly.nametag.effects;

import net.mcreator.mcanomalyarchives.anomaly.nametag.MaterialUnits;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagCosts;
import net.mcreator.mcanomalyarchives.anomaly.nametag.ResolvedName;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 把名字"贴"到实体上。
 *
 * 【核心取舍：不换实体，只做"可观测行为覆盖层"】
 * MC 的怪物行为写在类里（{@code Chicken.aiStep()} 里的产蛋计时器、{@code Cow.mobInteract()} 里的挤奶），
 * 外部无法把 {@code Chicken} 的行为装到一只 {@code Cow} 实例上——**"把牛真的变成鸡"在技术上不存在**。
 * 正片那种效果，在 MC 里只能覆盖"玩家能观测到的部分"：
 * <ul>
 *   <li>实体→实体：行为/产出/交互（挤奶拦截在 {@code NameTagHandler}）</li>
 *   <li>实体→物品：行为移植（正片猪→钻石镐：不顾一切寻找石头和矿石挖掘，挖掘不久后死亡）</li>
 *   <li>实体→方块：质料守恒结算——自己不够就从环境掠夺（正片羊→金块：历时数天、内部完全中空）</li>
 * </ul>
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class EntityNaming {

	private EntityNaming() {
	}

	/** 贴身份 + 保留原外观（原模型/贴图/尺寸/血量上限全部不动）。 */
	public static void apply(LivingEntity target, ResolvedName name, String rawName) {
		int lifespan = NameTagCosts.lifespanFor(rawName);
		NamedState.apply(target, name, rawName, lifespan);
		if (!target.hasCustomName()) {
			target.setCustomName(Component.literal(rawName));
			target.setCustomNameVisible(true);
		}
	}

	/** 名字就是它自己原本的类型（牛→"牛"）：身份没变，不该按"改写过的存在"计寿命。 */
	public static boolean isSelfIdentity(LivingEntity living) {
		ResolvedName target = NamedState.targetOf(living);
		return target != null && target.kind() == ResolvedName.Kind.ENTITY
				&& target.id().equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()));
	}

	/**
	 * 跨类（实体 ← 方块/物品名）：先做质料结算。
	 *
	 * ⚠️ 已废弃：作者 2026-09-13 把实体侧改成"慢慢变成"之后，生物不再按"够不够"当场结算
	 * ——材料不够就停在最后一步之前持续掠夺（{@link NameTagTicker#tickDrain} 那一支），**不爆炸**。
	 */
	@Deprecated
	public static MaterialUnits.Outcome settle(ServerLevel level, LivingEntity target, ResolvedName name) {
		int budget = MaterialUnits.budgetOf(target);
		int need = MaterialUnits.requirement(name);
		MaterialUnits.Outcome outcome = MaterialUnits.settle(budget, need);
		if (outcome == MaterialUnits.Outcome.DRAIN && !hasDrainable(level, target, name)) {
			return MaterialUnits.Outcome.EXPLODE;
		}
		return outcome;
	}

	/** @deprecated 见 {@link #settle}；现在只用 {@link #findDrainable}。 */
	@Deprecated
	public static boolean hasDrainable(ServerLevel level, LivingEntity target, ResolvedName name) {
		return findDrainable(level, target, name) != null;
	}

	/** 找一块可以被掠夺的方块：目标材料本身，或它的矿石/粗矿形态。 */
	public static BlockPos findDrainable(ServerLevel level, LivingEntity target, ResolvedName name) {
		java.util.Set<Block> wanted = MaterialUnits.drainSources(name);
		if (wanted.isEmpty()) {
			return null;
		}
		BlockPos origin = target.blockPosition();
		int r = NameTagCosts.DRAIN_RADIUS;
		int vy = NameTagCosts.DRAIN_VERTICAL;
		BlockPos min = origin.offset(-r, -vy, -r);
		BlockPos max = origin.offset(r, vy, r);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (wanted.contains(level.getBlockState(pos).getBlock())) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** 抽走一块材料（变成空气），返回是否成功。 */
	public static boolean drain(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		level.removeBlock(pos, false);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
		return true;
	}

	/**
	 * 掠夺完成 → 目标自己被替换成那个方块（正片：整个躯体完全被金子替换、内部结构完全中空）。
	 */
	public static void completeBlockConversion(ServerLevel level, LivingEntity target, ResolvedName name) {
		if (name.kind() == ResolvedName.Kind.BLOCK) {
			Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(name.id());
			BlockPos pos = target.blockPosition();
			target.discard();
			if (block != null && !block.defaultBlockState().isAir() && level.getBlockState(pos).canBeReplaced()) {
				level.setBlockAndUpdate(pos, block.defaultBlockState());
			}
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.6, 0.4, 0.02);
			level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.8f, 0.6f);
		}
	}

	/** 「土豆」：立刻丧失活性 → 掉落发芽的块茎 → 原地长出嫩芽（正片开场事故）。 */
	public static void toPotato(ServerLevel level, LivingEntity target) {
		BlockPos pos = target.blockPosition();
		target.discard();
		ItemStack drop = new ItemStack(Items.POTATO, 1 + level.random.nextInt(3));
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
		level.addFreshEntity(item);
		BlockPos soil = pos.below();
		if (level.getBlockState(soil).is(Blocks.GRASS_BLOCK) || level.getBlockState(soil).is(Blocks.DIRT)
				|| level.getBlockState(soil).is(Blocks.FARMLAND)) {
			level.setBlockAndUpdate(soil.above(),
					Blocks.POTATOES.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
		}
	}

	/** 「无」：认知性抹除——目标消失、无掉落、无音效、不留任何记录。 */
	public static void erase(ServerLevel level, LivingEntity target) {
		level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
				12, 0.3, 0.4, 0.3, 0.0);
		target.discard();
	}

	/**
	 * 变成方块的那一支：**失去 AI**。
	 *
	 * 作者要求"变成方块后应该失去 AI"，正片也是这么演的：
	 * 【旁白 3:55-4:06】羊被命名成「金块」后"**瞬间失去了所有生物活性、躯体僵硬**"。
	 *
	 * 用 {@code setNoAi(true)}：{@code Mob.isEffectiveAi()} 会变成 false，
	 * 于是整个 {@code serverAiStep()}（感知、目标选择、寻路、移动控制）全部停摆 —— 它就僵在原地，
	 * 但重力、碰撞、受伤这些物理还照常，所以站在坑边会掉下去，不会浮空。
	 */
	public static void loseVitality(LivingEntity living) {
		if (living instanceof Mob mob && !mob.isNoAi()) {
			mob.setNoAi(true);
			mob.getNavigation().stop();
			mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
			if (!living.level().isClientSide()) {
				living.level().playSound(null, living.blockPosition(), SoundEvents.SHEEP_HURT, SoundSource.NEUTRAL, 0.5f, 0.5f);
			}
		}
	}

	/**
	 * 实体 ← 材料名（钻石 / 金锭 / 熟牛排 …）：**完全转换**——它变成那个东西。
	 *
	 * 【为什么和"工具名"分开处理】正片里猪被命名成"钻石镐"之后是**获得挖矿行为、挖到死**，
	 * 而石头被命名成"钻石"该变成钻石、纸被命名成"书"该变成书。
	 * 区别在于名字指向的事物有没有"行为"：工具有，材料没有。
	 * 没有行为的名字，唯一说得通的结果就是"它就是那个东西本身"。
	 *
	 * 数量按质料守恒折算（牛 110 单位 ÷ 钻石 30 单位 = 3 颗），并夹在 1~8 之间。
	 *
	 * 【作者要求：变成物品后可以右键拿起、作为物品使用】
	 * 所以产物是一个**货真价实的物品实体**：没有拾取延迟、**永不过期**（它不是掉落物，
	 * 是那只生物变成的东西，不该自己消失），并且可以被右键拿走。
	 */
	public static void convertToMaterial(ServerLevel level, LivingEntity target, ResolvedName name) {
		ItemStack drop = materialYield(target, name);
		BlockPos pos = target.blockPosition();
		target.discard();
		if (!drop.isEmpty()) {
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
			item.setPickUpDelay(0);
			item.setUnlimitedLifetime();
			item.getPersistentData().putBoolean(NamedState.TAG_FROM_TRANSFORM, true);
			level.addFreshEntity(item);
		}
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
				16, 0.4, 0.5, 0.4, 0.02);
		level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.7f, 0.6f);
	}

	/** 一个实体按质料守恒能析出多少"那个东西"。 */
	public static ItemStack materialYield(LivingEntity source, ResolvedName name) {
		int budget = MaterialUnits.budgetOf(source);
		int need = MaterialUnits.requirement(name);
		int count = need <= 0 ? 1 : Math.max(1, Math.min(8, budget / need));
		return MaterialUnits.residueStack(name, count);
	}

	/**
	 * 转化完成：**原地替换成目标生物的真身**。
	 *
	 * 作者定的机制是"被命名的生物会慢慢变成对应的生物"，这里是它的最后一步。
	 * 换成真身之后，之前靠打补丁实现的东西全部自动正确——真鸡自己会下蛋、自己会鸡叫、
	 * 本来就没奶、掉落表也是鸡的，我们一行都不用写。
	 *
	 * **保留**：位置、朝向、玩家的自定义名、被命名标记（不可逆）。
	 * **不保留**：血量（新个体满血）、原生物的掉落 —— 它不是死了，是变成了别的。
	 * **清掉转化状态**：新个体不该继续"正在转化"。
	 */
	public static boolean replaceWith(ServerLevel level, LivingEntity source, ResourceLocation targetTypeId) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(targetTypeId);
		if (type == null) {
			return false;
		}
		if (!(type.create(level) instanceof LivingEntity replacement)) {
			return false;
		}
		replacement.moveTo(source.getX(), source.getY(), source.getZ(), source.getYRot(), source.getXRot());
		replacement.setYHeadRot(source.getYHeadRot());
		replacement.setYBodyRot(source.yBodyRot);

		// 继承标记（不可逆、目标、显示名），但把"正在转化"清掉——它已经变完了
		CompoundTag carried = source.getPersistentData().copy();
		carried.remove(NamedState.K_TRANSFORM_START);
		carried.remove(NamedState.K_TRANSFORM_DURATION);
		carried.remove(NamedState.K_TRANSFORM_STAGE);
		carried.remove(NamedState.K_PROGRESS);
		replacement.getPersistentData().merge(carried);

		if (source.hasCustomName()) {
			replacement.setCustomName(source.getCustomName());
			replacement.setCustomNameVisible(source.isCustomNameVisible());
		}

		// 它不是死了：discard 不触发死亡掉落，也不留尸体
		source.discard();
		level.addFreshEntity(replacement);

		level.sendParticles(ParticleTypes.END_ROD, replacement.getX(),
				replacement.getY() + replacement.getBbHeight() * 0.5, replacement.getZ(), 24, 0.4, 0.6, 0.4, 0.04);
		level.playSound(null, replacement.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.7f, 1.4f);
		return true;
	}

	// ===== 身份决定"能不能被利用"，以及产出什么 =====

	/** 对某个身份而言，手上这件东西能取走什么。 */
	public enum Harvest {
		NONE,
		MILK,
		WOOL,
		STEW
	}

	/**
	 * 正片：牛被命名成"鸡"之后"无法再挤出牛奶"——**能不能挤奶由名字决定，不由它原本是什么决定**。
	 * 反过来，牛被命名成"绵羊"就该能剪下羊毛。
	 */
	public static Harvest harvestFor(ResolvedName identity, net.minecraft.world.item.ItemStack tool) {
		if (identity == null || identity.kind() != ResolvedName.Kind.ENTITY) {
			return Harvest.NONE;
		}
		String id = identity.id().toString();
		boolean milkable = id.equals("minecraft:cow") || id.equals("minecraft:goat") || id.equals("minecraft:mooshroom");
		boolean shearable = id.equals("minecraft:sheep") || id.equals("minecraft:mooshroom");
		if (tool.is(net.minecraft.world.item.Items.BUCKET) && milkable) {
			return Harvest.MILK;
		}
		if (tool.is(net.minecraft.world.item.Items.SHEARS) && shearable) {
			return Harvest.WOOL;
		}
		if (tool.is(net.minecraft.world.item.Items.BOWL) && id.equals("minecraft:mooshroom")) {
			return Harvest.STEW;
		}
		return Harvest.NONE;
	}

	/**
	 * 让这次利用计入"按新身份行动"的次数。
	 *
	 * ⚠️ 已废弃：作者 2026-09-13 把实体侧改成"**慢慢变成**对应的生物"之后，
	 * "寿命耗尽就死"被**转化时间轴**取代（见 {@link NameTagTransform}）——
	 * 生物不再是被命名几次就猝死，而是走完转化、变成目标。保留此方法只为记录历史。
	 */
	@Deprecated
	public static void spendAction(ServerLevel level, LivingEntity living) {
		int remaining = NamedState.remainingOf(living) - 1;
		NamedState.setRemaining(living, remaining);
		if (remaining <= 0) {
			level.playSound(null, living.blockPosition(), SoundEvents.GENERIC_DEATH, SoundSource.NEUTRAL, 0.6f, 0.7f);
			living.hurt(living.damageSources().genericKill(), Float.MAX_VALUE);
		}
	}
}