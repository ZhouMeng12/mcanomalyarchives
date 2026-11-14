package net.mcreator.mcanomalyarchives.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Inert collectible representing the reverse-colored, corrupted material found in UO-002. */
public final class UO002GarbledItem extends Item {
	private static final int VARIANT_COUNT = 24;
	private static final String NAME_GLYPHS = "ΩЖλЖΞΠЖあカヲ漢噪错位异变乱码虚无界面箱子水瓶木棍煙花骸骼";

	/** Constructor expected by MCreator's generated deferred registration. */
	public UO002GarbledItem() {
		super(new Properties());
	}

	/** Gives each newly transformed stack one of the prebuilt random three-item collages. */
	public static ItemStack createRandomStack(int count) {
		ItemStack stack = new ItemStack(net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems.UO002_GARBLED.get(), count);
		int variant = ThreadLocalRandom.current().nextInt(1, VARIANT_COUNT + 1);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant));
		return stack;
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("错位异变乱码虚无界面").withStyle(ChatFormatting.OBFUSCATED);
	}

	/** Ten rapidly changing Unicode glyphs shared by garbled item and entity names. */
	public static String randomizedName(long salt) {
		Random random = new Random(salt);
		StringBuilder name = new StringBuilder(10);
		for (int i = 0; i < 10; i++) {
			name.append(NAME_GLYPHS.charAt(random.nextInt(NAME_GLYPHS.length())));
		}
		return name.toString();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.mcanomalyarchives.uo002_garbled.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
