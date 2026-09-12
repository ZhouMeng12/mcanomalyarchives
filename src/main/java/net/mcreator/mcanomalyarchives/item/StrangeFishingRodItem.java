package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import java.lang.reflect.Field;

public class StrangeFishingRodItem extends FishingRodItem {

	private static final TagKey<Item> REPAIR_ITEMS_TAG = TagKey.create(Registries.ITEM,
			ResourceLocation.parse("mcanomalyarchives:strange_fishing_rod_repair_items"));

	private static Field biteTimeField = null;
	private static boolean fieldLookupDone = false;

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
		ItemStack stack = player.getItemInHand(hand);
		FishingHook existingHook = player.fishing;

		if (existingHook == null) {
			// 投钩：正常执行
			InteractionResultHolder<ItemStack> result = super.use(level, player, hand);

			// 延迟设置咬钩时间（等待浮钩飞行到目标位置）
			if (result.getResult().consumesAction() && !level.isClientSide) {
				net.mcreator.mcanomalyarchives.McanomalyarchivesMod.queueServerWork(5, () -> {
					FishingHook hook = player.fishing;
					if (hook != null && hook.isAlive()) {
						double distance = hook.distanceTo(player);
						int biteTime = calculateBiteTime(distance);
						setHookBiteTime(hook, biteTime);
					}
				});
			}
			return result;
		} else {
			// 收杆：正常执行，战利品由 ItemFishedEvent 处理器接管
			return super.use(level, player, hand);
		}
	}

	private static int calculateBiteTime(double distance) {
		// 距离上限 15 格（第五档），咬钩时间成比例
		double factor = Math.min(distance / 15.0, 1.0);
		int randomExtra = 200 + (int) (Math.random() * 400); // 200~600
		return 40 + (int) (factor * randomExtra);
	}

	private static void setHookBiteTime(FishingHook hook, int biteTime) {
		try {
			// 尝试用已发现的字段
			if (biteTimeField != null) {
				biteTimeField.setInt(hook, biteTime);
				return;
			}

			// 反射遍历所有 int 字段，找到值在合理范围内的计时字段
			for (Field field : FishingHook.class.getDeclaredFields()) {
				if (field.getType() == int.class) {
					field.setAccessible(true);
					int current = field.getInt(hook);
					// 咬钩计时字段通常在 10-1200 ticks 范围
					if (current > 10 && current < 1200) {
						field.setInt(hook, biteTime);
						biteTimeField = field;
						if (!fieldLookupDone) {
							System.out.println("[StrangeFishingRod] Found bite time field: " + field.getName() + " (value was: " + current + ")");
							fieldLookupDone = true;
						}
						return;
					}
				}
			}
		} catch (Exception e) {
			// 反射失败则使用默认咬钩时间
		}
	}
}
