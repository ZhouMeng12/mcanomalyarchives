package net.mcreator.mcanomalyarchives.anomaly.nametag.effects;

import net.mcreator.mcanomalyarchives.anomaly.nametag.MaterialUnits;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagCosts;
import net.mcreator.mcanomalyarchives.anomaly.nametag.ResolvedName;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
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
	 * 跨类（实体 ← 方块名）：先做质料结算。
	 *
	 * @return 结算结果；{@link MaterialUnits.Outcome#SUCCESS} 表示可以立刻转化
	 */
	public static MaterialUnits.Outcome settle(ServerLevel level, LivingEntity target, ResolvedName name) {
		int budget = MaterialUnits.budgetOf(target);
		int need = MaterialUnits.requirement(name);
		MaterialUnits.Outcome outcome = MaterialUnits.settle(budget, need);
		if (outcome == MaterialUnits.Outcome.DRAIN && !hasDrainable(level, target, name)) {
			return MaterialUnits.Outcome.EXPLODE;
		}
		return outcome;
	}

	/** 半径内是否存在可掠夺的"同族材料"（正片：羊抽走了收容所里含金设备的零件）。 */
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
}
