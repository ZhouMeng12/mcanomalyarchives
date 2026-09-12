package net.mcreator.mcanomalyarchives.effect;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.mcreator.mcanomalyarchives.entity.ControllableMonster;

/**
 * 礼物效果函数式接口。
 * 赠送物品给 NPC 时触发，由各实体注册 {@code Item → GiftEffect} 映射。
 */
@FunctionalInterface
public interface GiftEffect {
	/**
	 * @param player 赠送者
	 * @param npc    被赠送的 NPC
	 * @param gift   赠送的物品副本（已消耗）
	 */
	void apply(Player player, ControllableMonster npc, ItemStack gift);
}
