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
    public static final DeferredItem<Item> NEGATIVE_DIRT = REGISTRY.register("negative_dirt", () -> new net.mcreator.mcanomalyarchives.item.NegativeBlockItem(0));
    public static final DeferredItem<Item> NEGATIVE_OAK_LOG = REGISTRY.register("negative_oak_log", () -> new net.mcreator.mcanomalyarchives.item.NegativeBlockItem(1));
    public static final DeferredItem<Item> NEGATIVE_OAK_PLANKS = REGISTRY.register("negative_oak_planks", () -> new net.mcreator.mcanomalyarchives.item.NegativeBlockItem(2));
	public static final DeferredItem<Item> NEGATIVE_APPLE = REGISTRY.register("negative_apple", NegativeAppleItem::new);
	public static final DeferredItem<Item> NEGATIVE_DIAMOND_PICKAXE = REGISTRY.register("negative_diamond_pickaxe", () -> new NegativeDiamondPickaxeItem());
    public static final DeferredItem<Item> NEGATIVE_WOODEN_PICKAXE = REGISTRY.register("negative_wooden_pickaxe", () -> new NegativeDiamondPickaxeItem(net.minecraft.world.item.Tiers.WOOD));
	public static final DeferredItem<Item> NEGATIVE_BOW = REGISTRY.register("negative_bow", net.mcreator.mcanomalyarchives.item.NegativeBowItem::new);
	public static final DeferredItem<Item> NEGATIVE_ARROW = REGISTRY.register("negative_arrow", net.mcreator.mcanomalyarchives.item.NegativeArrowItem::new);
	public static final DeferredItem<Item> NEGATIVE_MUSIC_DISC = REGISTRY.register("negative_music_disc", net.mcreator.mcanomalyarchives.item.NegativeMusicDiscItem::new);

	private UO007Items() { }
}
