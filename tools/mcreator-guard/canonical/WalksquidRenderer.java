package net.mcreator.mcanomalyarchives.client.renderer;

import net.mcreator.mcanomalyarchives.entity.WalksquidEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Stable renderer name used by MCreator's regenerated entity registry. */
public final class WalksquidRenderer extends AnomalyWildlifeRenderer<WalksquidEntity> {
    public WalksquidRenderer(EntityRendererProvider.Context context) {
        super(context, "walksquid", 0.4f);
    }
}
