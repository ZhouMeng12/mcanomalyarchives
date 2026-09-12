/*
 * MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.fluids.FluidType;

import net.mcreator.mcanomalyarchives.fluid.types.CloudWaterFluidType;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class McanomalyarchivesModFluidTypes {
	public static final DeferredRegister<FluidType> REGISTRY = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, McanomalyarchivesMod.MODID);
	public static final DeferredHolder<FluidType, FluidType> CLOUD_WATER_TYPE = REGISTRY.register("cloud_water", () -> new CloudWaterFluidType());
}