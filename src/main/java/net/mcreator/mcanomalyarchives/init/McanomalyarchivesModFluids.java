/*
 * MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;

import net.mcreator.mcanomalyarchives.fluid.CloudWaterFluid;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class McanomalyarchivesModFluids {
	public static final DeferredRegister<Fluid> REGISTRY = DeferredRegister.create(BuiltInRegistries.FLUID, McanomalyarchivesMod.MODID);
	public static final DeferredHolder<Fluid, FlowingFluid> CLOUD_WATER = REGISTRY.register("cloud_water", () -> new CloudWaterFluid.Source());
	public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_CLOUD_WATER = REGISTRY.register("flowing_cloud_water", () -> new CloudWaterFluid.Flowing());

	@EventBusSubscriber(Dist.CLIENT)
	public static class FluidsClientSideHandler {
		@SubscribeEvent
		public static void clientSetup(FMLClientSetupEvent event) {
			ItemBlockRenderTypes.setRenderLayer(CLOUD_WATER.get(), RenderType.translucent());
			ItemBlockRenderTypes.setRenderLayer(FLOWING_CLOUD_WATER.get(), RenderType.translucent());
		}
	}
}