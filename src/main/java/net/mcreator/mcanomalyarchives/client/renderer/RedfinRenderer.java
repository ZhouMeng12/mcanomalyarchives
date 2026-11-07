package net.mcreator.mcanomalyarchives.client.renderer;

import net.mcreator.mcanomalyarchives.entity.RedfinEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Stable renderer name used by MCreator's regenerated entity registry. */
public final class RedfinRenderer extends AnomalyWildlifeRenderer<RedfinEntity> {
    public RedfinRenderer(EntityRendererProvider.Context context) {
        super(context, "redfin", 0.25f);
    }
}
