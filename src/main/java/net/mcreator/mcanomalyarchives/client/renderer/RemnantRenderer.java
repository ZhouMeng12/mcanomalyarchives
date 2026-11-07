package net.mcreator.mcanomalyarchives.client.renderer;

import net.mcreator.mcanomalyarchives.entity.RemnantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Stable renderer name used by MCreator's regenerated entity registry. */
public final class RemnantRenderer extends AnomalyWildlifeRenderer<RemnantEntity> {
    public RemnantRenderer(EntityRendererProvider.Context context) {
        super(context, "remnant", 0.4f);
    }
}
