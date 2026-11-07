package net.mcreator.mcanomalyarchives.client.renderer;

import net.mcreator.mcanomalyarchives.entity.FoldEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Stable renderer name used by MCreator's regenerated entity registry. */
public final class FoldRenderer extends AnomalyWildlifeRenderer<FoldEntity> {
    public FoldRenderer(EntityRendererProvider.Context context) {
        super(context, "fold", 0.4f);
    }
}
