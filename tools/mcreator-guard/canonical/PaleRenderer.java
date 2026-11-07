package net.mcreator.mcanomalyarchives.client.renderer;

import net.mcreator.mcanomalyarchives.entity.PaleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Stable renderer name used by MCreator's regenerated entity registry. */
public final class PaleRenderer extends AnomalyWildlifeRenderer<PaleEntity> {
    public PaleRenderer(EntityRendererProvider.Context context) {
        super(context, "pale", 0.5f);
    }
}
