package net.mcreator.mcanomalyarchives.item;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BucketItem;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModFluids;

public class CloudWaterItem extends BucketItem {
	public CloudWaterItem() {
		super(McanomalyarchivesModFluids.CLOUD_WATER.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)

		);
	}
}