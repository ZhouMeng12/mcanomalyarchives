package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.SheepFurLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Sheep;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * UO-012 幸运粉羊渲染：完全沿用原版 Sheep 渲染。
 * 粉色由 SheepFurLayer 依实体 DyeColor.PINK 自动染色。
 */
@OnlyIn(Dist.CLIENT)
public class PinkSheepRenderer extends MobRenderer<Sheep, SheepModel<Sheep>> {

	private static final ResourceLocation SHEEP_LOCATION =
		ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep.png");

	public PinkSheepRenderer(EntityRendererProvider.Context context) {
		super(context, new SheepModel<>(context.bakeLayer(ModelLayers.SHEEP)), 0.7F);
		this.addLayer(new SheepFurLayer(this, context.getModelSet()));
	}

	@Override
	public ResourceLocation getTextureLocation(Sheep entity) {
		return SHEEP_LOCATION;
	}
}
