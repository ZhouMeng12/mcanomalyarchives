package net.mcreator.mcanomalyarchives.anomaly.nametag.effects;

import net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagCosts;
import net.mcreator.mcanomalyarchives.anomaly.nametag.ResolvedName;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * 物品被命名时的"内核转让"。
 *
 * 【为什么这条路最干净】1.21 把物品行为全部搬进了数据组件。已对着反编译源码核实，
 * 三处关键逻辑全部只读 {@code DataComponents.TOOL}：
 * <ul>
 *   <li>{@code Item.getDestroySpeed} → 挖掘速度</li>
 *   <li>{@code Item.isCorrectToolForDrops} → 挖掘等级（决定能不能挖出掉落物）</li>
 *   <li>{@code Item.mineBlock} → 每挖一格扣 {@code Tool.damagePerBlock()} 点耐久</li>
 * </ul>
 * 而 {@code Tool} 是纯 record（{@code rules / defaultMiningSpeed / damagePerBlock}），
 * 所以"木铲获得下界合金镐的采集能力"真的就是一行 set —— 模型、贴图、名字、物品类型一个字都不用改，
 * 精确复刻正片的"外观不变、只换内核"（【旁白 2:00-2:06】）。
 *
 * 【耐久 = 名字字符数】正片：木铲被命名成"下界合金镐"后"仅仅经过五次使用后，木铲便损毁"
 * （【旁白 2:34-2:37】）。我们形式化成耐久上限 = 名字字符数（"下界合金镐"5 字 → 5 次）。
 * 注意 {@code ItemStack.isDamageableItem()} 要求同时具备 MAX_DAMAGE 与 DAMAGE 两个组件
 * （已核实源码），所以两个都要设，否则挖方块不掉耐久。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class ItemNaming {

	/** 允许转让的组件白名单。 */
	private static final Set<DataComponentType<?>> TRANSFERRED = Set.of(
			DataComponents.TOOL,
			DataComponents.ATTRIBUTE_MODIFIERS,
			DataComponents.FOOD);
	/*
	 * 1.21.1 没有 ENCHANTABLE 组件（"可附魔等级"是 1.21.2+ 才拆出来的），
	 * 所以"可附魔性"这一项在 1.21.1 上拿不到，只能放弃转让。
	 */

	/*
	 * 黑名单（绝不复制）——都写在注释里当"为什么"，代码里靠白名单天然排除：
	 * MAX_STACK_SIZE  会把 64 个木棍变成 64 个"钻石"，等于复制物品
	 * CONTAINER / BUNDLE_CONTENTS  潜影盒、收纳袋的内容物会被复制
	 * BLOCK_ENTITY_DATA / BLOCK_STATE  方块物品带 NBT，放置后可能直接崩
	 * CHARGED_PROJECTILES / RECIPES / WRITABLE_BOOK_CONTENT  直接白送内容
	 */

	private ItemNaming() {
	}

	/**
	 * 同类（物品 → 物品名）：把源物品的"内核"搬过来，外壳不动。
	 *
	 * @param target 左槽里的目标物品（会被复制，不改原物）
	 * @param source 名字指向的物品
	 * @param rawName 玩家输入的原文本（决定耐久）
	 */
	public static ItemStack transfer(ItemStack target, ResolvedName source, String rawName) {
		ItemStack out = target.copy();
		ItemStack model = new ItemStack(BuiltInRegistries.ITEM.get(source.id()));

		int copied = 0;
		for (DataComponentType<?> type : TRANSFERRED) {
			if (copyComponent(out, model, type)) {
				copied++;
			}
		}
		if (copied == 0) {
			// 源物品没有任何可转让的内核（例如"钻石"这种纯材料）→ 视为解析不到有效内核
			return ItemStack.EMPTY;
		}

		applyCost(out, rawName);
		NamedState.apply(out, source, rawName, NameTagCosts.durabilityFor(rawName));
		return out;
	}

	/** 只设代价与标记（跨类转化的产物用）。 */
	public static void applyCost(ItemStack out, String rawName) {
		int durability = NameTagCosts.durabilityFor(rawName);
		out.set(DataComponents.MAX_DAMAGE, durability);
		out.set(DataComponents.DAMAGE, 0);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static boolean copyComponent(ItemStack target, ItemStack source, DataComponentType<?> type) {
		Object value = source.get((DataComponentType) type);
		if (value == null) {
			return false;
		}
		target.set((DataComponentType) type, value);
		return true;
	}
}
