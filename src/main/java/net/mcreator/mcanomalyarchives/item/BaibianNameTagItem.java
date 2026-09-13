package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 百变命名牌（UO-008）承载物品。
 *
 * 这是 MCreator 元素 elements/BaibianNameTag.mod.json 生成的文件，保持最小实现：
 * 全部玩法逻辑放在非生成区的 {@code anomaly/nametag/*} 里（名字解析、铁砧命名、实体行为覆盖层、
 * 质料守恒结算），这样 MCreator 重新生成这个类也不会影响功能。
 *
 * 正片依据（BV1YiGt6vEbK）：深褐色，区别于普通命名牌（【旁白 4.9-7.5s】），
 * 出自矿井内的矿车宝藏（【旁白 0:56-1:04】）。
 */
public class BaibianNameTagItem extends Item {
	public BaibianNameTagItem() {
		super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	}
}
