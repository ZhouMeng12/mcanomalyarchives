package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.item.Item;

/** Deliberately not in the vanilla arrow tag: ordinary bows cannot fire this ammunition. */
public final class NegativeArrowItem extends Item implements NegativeCountItem {
    public NegativeArrowItem() {
        super(new Properties().stacksTo(64));
    }
}
