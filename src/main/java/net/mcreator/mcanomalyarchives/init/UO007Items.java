package net.mcreator.mcanomalyarchives.init;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.item.NegativeAppleItem;
import net.mcreator.mcanomalyarchives.item.NegativeDiamondPickaxeItem;

/** Small, standalone registry for UO-007 items so MCreator regeneration leaves them intact. */
public final class UO007Items {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(McanomalyarchivesMod.MODID);
	public static final DeferredItem<Item> NEGATIVE_APPLE = REGISTRY.register("negative_apple", NegativeAppleItem::new);
	public static final DeferredItem<Item> NEGATIVE_DIAMOND_PICKAXE = REGISTRY.register("negative_diamond_pickaxe", NegativeDiamondPickaxeItem::new);

	private UO007Items() { }
}
