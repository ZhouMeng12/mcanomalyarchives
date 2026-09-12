package net.mcreator.mcanomalyarchives.init;

import com.mojang.serialization.Codec;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

public class McanomalyarchivesModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, McanomalyarchivesMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SERIAL_NUMBER =
        REGISTRY.register("serial_number", () ->
            DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .build()
        );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
        REGISTRY.register("energy", () ->
            DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .build()
        );

    public static final int MAX_ENERGY = 60000;
    public static final int ENERGY_CONSUME_PER_TICK = 1;

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> ATTACK_TIME =
        REGISTRY.register("attack_time", () ->
            DataComponentType.<Long>builder()
                .persistent(Codec.LONG)
                .build()
        );
}
