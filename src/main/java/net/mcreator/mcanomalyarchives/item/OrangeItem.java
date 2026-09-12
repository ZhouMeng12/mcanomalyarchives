package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;

public class OrangeItem extends Item {
	public OrangeItem() {
		super(new Item.Properties().food((new FoodProperties.Builder()).nutrition(4).saturationModifier(0.3f).build()));
	}
}