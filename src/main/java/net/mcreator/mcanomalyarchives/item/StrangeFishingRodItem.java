package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.mcreator.mcanomalyarchives.anomaly.fishing.StrangeRodCasting;

public class StrangeFishingRodItem extends FishingRodItem {

	private static final TagKey<Item> REPAIR_ITEMS_TAG = TagKey.create(Registries.ITEM,
			ResourceLocation.parse("mcanomalyarchives:strange_fishing_rod_repair_items"));

	public StrangeFishingRodItem() {
		// 1.21.1: Item.Properties 没有 repairable(TagKey) 方法（1.21.2+ 才引入）
		// 改为通过覆盖 isValidRepairItem 实现相同功能
		super(new Item.Properties().durability(51));
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
		// 等价于 1.21.2+ 的 .repairable(TagKey) 行为：tag 中包含的物品可修复此钓竿
		return repairCandidate.is(REPAIR_ITEMS_TAG);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		boolean casting = player.fishing == null;
		InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
		if (casting && result.getResult().consumesAction() && player instanceof ServerPlayer serverPlayer) {
			StrangeRodCasting.onCast(serverPlayer);
		}
		return result;
	}
}
