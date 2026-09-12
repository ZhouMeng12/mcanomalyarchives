package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.SlimeModel;

import net.mcreator.mcanomalyarchives.entity.SittingEnityEntity;

public class SittingEnityRenderer extends MobRenderer<SittingEnityEntity, SlimeModel<SittingEnityEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/touming.png");

	public SittingEnityRenderer(EntityRendererProvider.Context context) {
		super(context, new SlimeModel<SittingEnityEntity>(context.bakeLayer(ModelLayers.SLIME)), 0f);
	}

	@Override
	public ResourceLocation getTextureLocation(SittingEnityEntity entity) {
		return entityTexture;
	}
}