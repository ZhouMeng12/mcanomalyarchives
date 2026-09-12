/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.Registries;

import net.mcreator.mcanomalyarchives.potion.SadMobEffect;
import net.mcreator.mcanomalyarchives.potion.CloudingMobEffect;
import net.mcreator.mcanomalyarchives.potion.AddicteMobEffect;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class McanomalyarchivesModMobEffects {
	public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(Registries.MOB_EFFECT, McanomalyarchivesMod.MODID);
	public static final DeferredHolder<MobEffect, MobEffect> CLOUDING = REGISTRY.register("clouding", () -> new CloudingMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> ADDICTE = REGISTRY.register("addicte", () -> new AddicteMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> SAD = REGISTRY.register("sad", () -> new SadMobEffect());
}