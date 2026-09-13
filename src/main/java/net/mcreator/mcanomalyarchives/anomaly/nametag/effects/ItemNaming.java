package net.mcreator.mcanomalyarchives.anomaly.nametag.effects;

import net.mcreator.mcanomalyarchives.anomaly.nametag.MaterialUnits;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagCosts;
import net.mcreator.mcanomalyarchives.anomaly.nametag.ResolvedName;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * 物品被命名：**完全转换**。
 *
 * 【作者定的规则（2026-09-13）】
 * <ol>
 *   <li><b>只有性质变，外观不变</b>——石头命名成"钻石"，看上去还得是石头。</li>
 *   <li><b>但性质必须是名字所指事物的全部性质</b>——能合成钻石装备、能进信标、
 *       <b>而且不能再当方块放下去</b>。</li>
 *   <li><b>工具/护甲耐久用源物品原本的耐久</b>；源物品没有耐久就一律
 *       {@link NameTagCosts#DURABILITY_FALLBACK} 点。</li>
 * </ol>
 *
 * 【为什么必须真的换掉物品本体】在 Minecraft 里"能不能合成"由 <b>Item</b> 决定
 * （配方按 Item 匹配，数据组件救不了），"能不能放置"也由本体决定（是不是 {@code BlockItem}）。
 * 只搬组件的话，石头永远进不了钻石的配方。所以本体换成目标物品，
 * **外观那一条交给客户端把它画回源物品的模型**（{@code mixin/NamedItemAppearanceMixin}）。
 * 这样"有名字所指事物的全部性质"是自动成立的——耐久、能否放置、能否合成、
 * 能否当燃料、能不能吃，一条都不用枚举。
 *
 * 【代价】不再是"耐久 = 名字字数"，而是<b>质料守恒</b>（见 {@link MaterialUnits}）：
 * 撑得住才换得成，撑不住就是爆炸 + 等量残渣（正片木棍→钻石块）。
 */
public final class ItemNaming {

	/*
	 * 曾经打算"只搬白名单组件、绝不复制整个物品"，原因是要避开这些会复制内容的组件：
	 * MAX_STACK_SIZE（64 个木棍变成 64 个钻石）、CONTAINER / BUNDLE_CONTENTS（潜影盒内容物）、
	 * BLOCK_ENTITY_DATA / BLOCK_STATE（放置后可能崩）、CHARGED_PROJECTILES、RECIPES、
	 * WRITABLE_BOOK_CONTENT（白送内容）。
	 *
	 * 现在改为"整体换成目标物品"，这些顾虑自然消失：产物就是一个货真价实的目标物品，
	 * 它的默认组件是原版自己定义的，不是从别处搬来的。
	 */

	private ItemNaming() {
	}

	/**
	 * 把 {@code source} 命名成 {@code name} 所指的物品。
	 *
	 * @param source  被命名的物品（决定外观与耐久）
	 * @param name    解析出来的名字
	 * @param rawName 玩家输入的原文（当显示名用）
	 * @return 转换后的物品；名字没有"物品形态"（水/岩浆/火这类方块）时返回空
	 */
	public static ItemStack convert(ItemStack source, ResolvedName name, String rawName) {
		ItemStack out = MaterialUnits.itemFormOf(name);
		if (out.isEmpty()) {
			return ItemStack.EMPTY;
		}
		out.setCount(1);
		applyDurability(source, out);
		NamedState.applyConverted(out, name, rawName, BuiltInRegistries.ITEM.getKey(source.getItem()));
		return out;
	}

	/**
	 * 耐久：只有目标本身有耐久才需要动它。
	 *
	 * "工具/护甲耐久用原本的耐久，如果原物品没有耐久一律 100 点"——
	 * 所以取<b>源物品</b>的耐久上限；源物品本身不可损坏（石头、木棍…）就给 100。
	 */
	private static void applyDurability(ItemStack source, ItemStack out) {
		if (!out.isDamageableItem()) {
			return;
		}
		int durability = source.isDamageableItem() ? source.getMaxDamage() : NameTagCosts.DURABILITY_FALLBACK;
		out.set(DataComponents.MAX_DAMAGE, durability);
		out.set(DataComponents.DAMAGE, 0);
	}
}
