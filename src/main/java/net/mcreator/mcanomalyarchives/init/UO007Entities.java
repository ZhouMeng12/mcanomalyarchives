package net.mcreator.mcanomalyarchives.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.entity.NegativeReturnArrowEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class UO007Entities {
    public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, McanomalyarchivesMod.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<NegativeReturnArrowEntity>> RETURN_ARROW = REGISTRY.register(
        "negative_return_arrow", () -> EntityType.Builder.<NegativeReturnArrowEntity>of(NegativeReturnArrowEntity::new, MobCategory.MISC)
            .sized(.5f, .5f).clientTrackingRange(8).updateInterval(1)
            .build(McanomalyarchivesMod.MODID + ":negative_return_arrow"));

    private UO007Entities() { }
}
