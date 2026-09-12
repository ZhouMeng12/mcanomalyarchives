package net.mcreator.mcanomalyarchives.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.client.renderer.entity.CatRenderer;

@Mixin(CatRenderer.class)
public abstract class CatRendererMixin {
	private static final ResourceLocation ANBULA_CAT_TEXTURE =
		ResourceLocation.parse("mcanomalyarchives:textures/entities/anbulacat.png");

	@Inject(method = "getTextureLocation", at = @At("HEAD"), cancellable = true)
	private void onGetTexture(Cat cat, CallbackInfoReturnable<ResourceLocation> cir) {
		if (cat.getCustomName() != null && "安布拉".equals(cat.getCustomName().getString())) {
			cir.setReturnValue(ANBULA_CAT_TEXTURE);
		}
	}
}
