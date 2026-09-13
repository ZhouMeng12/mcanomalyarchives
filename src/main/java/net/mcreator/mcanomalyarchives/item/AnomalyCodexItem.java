package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 见闻录（图鉴）承载物品。
 *
 * 这是 MCreator 元素 elements/AnomalyCodex.mod.json 生成的文件，保持最小实现：
 * 右键打开界面的逻辑放在非生成区的 {@code codex/CodexItemHandler} 里，
 * 这样 MCreator 重新生成这个类也不会影响功能。
 */
public class AnomalyCodexItem extends Item {
	public AnomalyCodexItem() {
		super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	}
}
