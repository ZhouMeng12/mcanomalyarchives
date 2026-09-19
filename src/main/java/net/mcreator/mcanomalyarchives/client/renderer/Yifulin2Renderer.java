package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;

import net.mcreator.mcanomalyarchives.entity.Yifulin2Entity;

public class Yifulin2Renderer extends HumanoidMobRenderer<Yifulin2Entity, HumanoidModel<Yifulin2Entity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/grayyifulin.png");

	public Yifulin2Renderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<Yifulin2Entity>(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
		this.addLayer(new HumanoidArmorLayer(this, new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_SLIM_INNER_ARMOR)), new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_SLIM_OUTER_ARMOR)), context.getModelManager()));
	}

	@Override
	public ResourceLocation getTextureLocation(Yifulin2Entity entity) {
		return entityTexture;
	}
}