package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 被命名的生物会继承名字所指生物的**性质**（不只是行为）。
 *
 * 作者 2026-09-13："变生物的话掉落物也要变，别的性质（会被太阳点燃）也要有。"
 *
 * 行为目标（{@link EntityAiSwap}）能换来"怎么动"，但换不来写在别的类里的固有性质。
 * 这里用一张表把它们补上，由 {@code NameTagTicker} 与 {@code NameTagHandler} 逐条执行：
 * <ul>
 *   <li>{@link Trait#SUN_BURNS}：亡灵类——白天在阳光下会着火（原版写在 {@code Zombie.aiStep} 里）</li>
 *   <li>{@link Trait#INVERTED_POTION}：亡灵类——治疗药水伤害它、伤害药水治疗它</li>
 *   <li>{@link Trait#FIRE_IMMUNE}：免疫火焰与岩浆伤害</li>
 *   <li>{@link Trait#WATER_HURTS}：碰到水会受伤（烈焰人、末影人）</li>
 * </ul>
 *
 * 掉落物不在这里，走 {@link net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagHandler#onLivingDrops}：
 * 那里直接把名字所指生物的**战利品表**掷一遍。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class CreatureTraits {

	public enum Trait {
		/** 白天在阳光下会着火 */
		SUN_BURNS,
		/** 治疗药水伤害它、伤害药水治疗它 */
		INVERTED_POTION,
		/** 免疫火焰与岩浆 */
		FIRE_IMMUNE,
		/** 碰到水会受伤 */
		WATER_HURTS
	}

	private static final Set<String> UNDEAD = Set.of(
			"minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
			"minecraft:skeleton", "minecraft:stray", "minecraft:bogged", "minecraft:wither_skeleton",
			"minecraft:zombie_horse", "minecraft:skeleton_horse", "minecraft:zombified_piglin",
			"minecraft:zoglin", "minecraft:phantom", "minecraft:wither");

	private static final Set<String> FIREPROOF = Set.of(
			"minecraft:blaze", "minecraft:magma_cube", "minecraft:strider", "minecraft:zombified_piglin",
			"minecraft:zoglin", "minecraft:wither", "minecraft:wither_skeleton", "minecraft:ghast");

	/** 碰到水会掉血的（它们和亡灵不一样，不是着火而是直接受伤） */
	private static final Set<String> HYDROPHOBIC = Set.of("minecraft:blaze", "minecraft:enderman");

	private static final Map<String, Set<Trait>> TABLE = new HashMap<>();

	static {
		Set<Trait> undead = EnumSet.of(Trait.SUN_BURNS, Trait.INVERTED_POTION);
		for (String id : UNDEAD) {
			TABLE.put(id, undead);
		}
		for (String id : FIREPROOF) {
			TABLE.computeIfAbsent(id, key -> EnumSet.noneOf(Trait.class)).add(Trait.FIRE_IMMUNE);
		}
		for (String id : HYDROPHOBIC) {
			TABLE.computeIfAbsent(id, key -> EnumSet.noneOf(Trait.class)).add(Trait.WATER_HURTS);
		}
	}

	private CreatureTraits() {
	}

	/** 名字所指生物有哪些性质。 */
	public static Set<Trait> of(ResolvedName target) {
		if (target == null || target.kind() != ResolvedName.Kind.ENTITY) {
			return Set.of();
		}
		return TABLE.getOrDefault(target.id().toString(), Set.of());
	}

	/** 这只（被命名过的）生物是否具有某项性质。 */
	public static boolean has(LivingEntity entity, Trait trait) {
		return of(NamedState.targetOf(entity)).contains(trait);
	}

	public static boolean knows(ResolvedName target) {
		return !of(target).isEmpty();
	}
}
