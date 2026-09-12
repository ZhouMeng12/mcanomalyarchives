package net.mcreator.mcanomalyarchives.client.event;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

/**
 * 注册 ISTER 需要使用的额外 BakedModel，以及刷怪蛋的动态着色。
 *
 * 1.21.1 中只有"物品默认模型"（与物品注册名同名）会被自动烘焙。
 * purplehand_gui、detecter_gui、detecter_world 不是物品名，需手动注册到 ModelManager，
 * 否则 ISTER 中 getModel() 会返回 missing model（紫黑块）。
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class ExtraModelRegistration {

	@SubscribeEvent
	public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
		event.register(ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "item/purplehand_gui")
		));
		event.register(ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "item/detecter_gui")
		));
		event.register(ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "item/detecter_world")
		));
		event.register(ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "item/detecter_head")
		));
	}

	/**
	 * 禁用刷怪蛋的动态着色，使自定义贴图以原色显示。
	 * SpawnEggItem 默认会通过 ItemColors 给模型层着色，
	 * 这里用 -1（白色）覆盖，阻止着色。
	 */
	@SubscribeEvent
	public static void onItemColors(RegisterColorHandlersEvent.Item event) {
		// 仅自定义贴图的刷怪蛋禁用自动着色（安布拉/斯万/紫怪）
		event.register(
			(stack, tintIndex) -> -1,
			McanomalyarchivesModItems.ANBULA_SPAWN_EGG.get(),
			McanomalyarchivesModItems.SVAN_SPAWN_EGG.get(),
			McanomalyarchivesModItems.PURPLE_MONSTER_SPAWN_EGG.get()
		);
	}
}
