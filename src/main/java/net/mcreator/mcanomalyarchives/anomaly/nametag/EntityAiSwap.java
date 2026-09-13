package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 命名瞬间把生物的**行为目标整套换掉** —— 这是"变成实体就瞬间换 AI，但模型/材质不变"的实现。
 *
 * 【为什么这样做】真要"换成那个生物的类"就得把实体本体换掉，那外观也跟着变了；
 * 而作者要求**模型/材质不变**。所以反过来：**保留本体、换掉它的 AI**。
 * 原版大多数生物的行为就是 {@code goalSelector} 里那一串 Goal（外加 targetSelector 里的攻击目标），
 * 而 {@code Mob.goalSelector} / {@code targetSelector} 都是 public final 的，可以直接清空重装。
 *
 * 【保真度】装上目标生物同一套 Goal 之后：
 * <ul>
 *   <li>牛被命名成"鸡" → 真的会像鸡一样乱窜（PanicGoal 1.4）、被种子吸引、怕玩家、随机游荡；</li>
 *   <li>牛被命名成"僵尸" → 会追着玩家打。</li>
 * </ul>
 * 换不来的部分：写在 {@code aiStep}/{@code customServerAiStep} 里的特有能力（鸡的下蛋计时器、
 * 羊的吃草），以及掉落表、繁殖产物——这些仍然是原生物的。
 * 下蛋由 {@link NameTagTicker} 补（正片里那头牛就是靠这个下"牛蛋"的）；
 * 掉落表要跟名字走就得换本体，那是方案 B，作者选了先做 A。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class EntityAiSwap {

	/**
	 * 原版被动动物的目标模板。参数抄自各家的 {@code registerGoals}：
	 * <pre>
	 * 0 FloatGoal → 1 PanicGoal(panic) → 2 BreedGoal(1.0) → 3 TemptGoal(tempt, 食物)
	 * → 4 FollowParentGoal(speed) → 5 WaterAvoidingRandomStrollGoal(stroll)
	 * → 6 LookAtPlayerGoal(Player, 6) → 7 RandomLookAroundGoal
	 * </pre>
	 */
	private record Template(double panic, double tempt, TagKey<Item> food, double followParent, double stroll) {
	}

	private static final Template DEFAULT_ANIMAL = new Template(1.25, 1.1, null, 1.1, 1.0);

	private static final Map<String, Template> PASSIVE = new HashMap<>();

	static {
		PASSIVE.put("minecraft:chicken", new Template(1.4, 1.0, ItemTags.CHICKEN_FOOD, 1.1, 1.0));
		PASSIVE.put("minecraft:cow", new Template(2.0, 1.25, ItemTags.COW_FOOD, 1.25, 1.0));
		PASSIVE.put("minecraft:mooshroom", new Template(2.0, 1.25, ItemTags.COW_FOOD, 1.25, 1.0));
		PASSIVE.put("minecraft:sheep", new Template(1.25, 1.1, ItemTags.SHEEP_FOOD, 1.1, 1.0));
		PASSIVE.put("minecraft:pig", new Template(1.25, 1.2, ItemTags.PIG_FOOD, 1.1, 1.0));
		PASSIVE.put("minecraft:rabbit", new Template(2.2, 1.0, ItemTags.RABBIT_FOOD, 1.1, 0.6));
		PASSIVE.put("minecraft:goat", new Template(1.25, 1.1, ItemTags.GOAT_FOOD, 1.1, 1.0));
		PASSIVE.put("minecraft:llama", new Template(1.25, 1.1, null, 1.1, 0.7));
		PASSIVE.put("minecraft:horse", new Template(1.2, 1.2, null, 1.2, 0.7));
		PASSIVE.put("minecraft:donkey", new Template(1.2, 1.2, null, 1.2, 0.7));
		PASSIVE.put("minecraft:turtle", new Template(1.0, 1.1, null, 1.1, 1.0));
		PASSIVE.put("minecraft:fox", new Template(1.6, 1.2, ItemTags.FOX_FOOD, 1.1, 1.0));
		PASSIVE.put("minecraft:cat", new Template(1.5, 1.0, null, 1.1, 0.8));
		PASSIVE.put("minecraft:ocelot", new Template(1.5, 1.0, null, 1.1, 0.8));
		PASSIVE.put("minecraft:wolf", new Template(1.5, 1.0, null, 1.1, 1.0));
		PASSIVE.put("minecraft:parrot", new Template(1.0, 1.0, null, 1.1, 1.0));
		PASSIVE.put("minecraft:axolotl", new Template(1.0, 1.0, null, 1.1, 1.0));
		PASSIVE.put("minecraft:bee", new Template(1.0, 1.0, null, 1.1, 1.0));
	}

	/** 会主动打人的那些：换上"追着玩家打"的进攻组。 */
	private static final Set<String> HOSTILE = Set.of(
			"minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
			"minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton", "minecraft:spider",
			"minecraft:cave_spider", "minecraft:creeper", "minecraft:enderman", "minecraft:witch",
			"minecraft:pillager", "minecraft:vindicator", "minecraft:zombified_piglin", "minecraft:piglin",
			"minecraft:blaze", "minecraft:magma_cube", "minecraft:slime", "minecraft:silverfish",
			"minecraft:endermite", "minecraft:phantom", "minecraft:guardian", "minecraft:evoker");

	private EntityAiSwap() {
	}

	/**
	 * 原地把这只生物的行为目标换成名字所指生物的。
	 *
	 * 只支持 {@link PathfinderMob}（原版绝大多数生物都是）：史莱姆、恶魂这类不走寻路目标的
	 * 另有一套移动逻辑，Goal 装上去也没用，所以调用方要自己判类型。
	 *
	 * @return 是否换成功
	 */
	public static boolean swap(PathfinderMob mob, ResolvedName target) {
		if (target == null || target.kind() != ResolvedName.Kind.ENTITY) {
			return false;
		}
		String id = target.id().toString();
		// 清空原来的全部行为与攻击目标（这就是"失去它自己的 AI"）
		mob.goalSelector.removeAllGoals(goal -> true);
		mob.targetSelector.removeAllGoals(goal -> true);
		if (HOSTILE.contains(id)) {
			installHostile(mob);
		} else {
			installPassive(mob, PASSIVE.getOrDefault(id, DEFAULT_ANIMAL));
		}
		return true;
	}

	private static void installPassive(PathfinderMob mob, Template template) {
		GoalSelector goals = mob.goalSelector;
		goals.addGoal(0, new FloatGoal(mob));
		goals.addGoal(1, new PanicGoal(mob, template.panic()));
		if (mob instanceof Animal animal) {
			goals.addGoal(2, new net.minecraft.world.entity.ai.goal.BreedGoal(animal, 1.0));
		}
		if (template.food() != null) {
			Predicate<ItemStack> food = stack -> stack.is(template.food());
			goals.addGoal(3, new TemptGoal(mob, template.tempt(), food, false));
		}
		if (mob instanceof Animal animal) {
			goals.addGoal(4, new net.minecraft.world.entity.ai.goal.FollowParentGoal(animal, template.followParent()));
		}
		goals.addGoal(5, new WaterAvoidingRandomStrollGoal(mob, template.stroll()));
		goals.addGoal(6, new LookAtPlayerGoal(mob, Player.class, 6.0f));
		goals.addGoal(7, new RandomLookAroundGoal(mob));
	}

	private static void installHostile(PathfinderMob mob) {
		GoalSelector goals = mob.goalSelector;
		goals.addGoal(0, new FloatGoal(mob));
		goals.addGoal(2, new MeleeAttackGoal(mob, 1.0, false));
		goals.addGoal(7, new WaterAvoidingRandomStrollGoal(mob, 1.0));
		goals.addGoal(8, new LookAtPlayerGoal(mob, Player.class, 8.0f));
		goals.addGoal(8, new RandomLookAroundGoal(mob));
		mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob));
		mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
	}

	/** 名字所指的生物在不在我们支持的表里（供提示/自检用）。 */
	public static boolean knows(ResolvedName target) {
		if (target == null || target.kind() != ResolvedName.Kind.ENTITY) {
			return false;
		}
		String id = target.id().toString();
		return PASSIVE.containsKey(id) || HOSTILE.contains(id);
	}
}
