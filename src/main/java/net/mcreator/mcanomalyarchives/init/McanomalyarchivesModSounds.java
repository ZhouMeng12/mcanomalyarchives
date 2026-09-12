/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class McanomalyarchivesModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, McanomalyarchivesMod.MODID);
	public static final DeferredHolder<SoundEvent, SoundEvent> ANBULA_BERSERK = REGISTRY.register("anbula_berserk", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "anbula_berserk")));
}