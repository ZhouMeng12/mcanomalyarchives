package net.mcreator.mcanomalyarchives.client.renderer;

import net.mcreator.mcanomalyarchives.entity.BloomcowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Stable renderer name used by MCreator's regenerated entity registry. */
public final class BloomcowRenderer extends AnomalyWildlifeRenderer<BloomcowEntity> {
    public BloomcowRenderer(EntityRendererProvider.Context context) {
        super(context, "bloomcow", 0.45f);
    }
}
