package net.mcreator.mcanomalyarchives.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.UO007Items;

/** Displays the negative stack quantity in the inventory count overlay. */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NegativeAppleCountDecorator {
	private NegativeAppleCountDecorator() { }

	@SubscribeEvent
	public static void register(RegisterItemDecorationsEvent event) {
		event.register(UO007Items.NEGATIVE_APPLE.get(), new IItemDecorator() {
			@Override
			public boolean render(GuiGraphics graphics, Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
				String count = "-" + stack.getCount();
				graphics.drawString(font, count, x + 17 - font.width(count), y + 9, 0xFFFF5555, true);
				return false;
			}
		});
	}
}
