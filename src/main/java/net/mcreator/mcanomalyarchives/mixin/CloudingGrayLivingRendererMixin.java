package net.mcreator.mcanomalyarchives.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import net.mcreator.mcanomalyarchives.client.clouding.CloudingGrayTextures;
import net.mcreator.mcanomalyarchives.client.clouding.CloudingVisualState;

/**
 * 云化一级：把生物本体贴图换成运行时生成的灰白变体。
 * 注入点选在 LivingEntityRenderer.getRenderType 取贴图的位置——
 * 一条 mixin 覆盖原版生物与模组全部 HumanoidMobRenderer / MobRenderer。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CloudingGrayLivingRendererMixin {

	@ModifyExpressionValue(
			method = "getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;"))
	private ResourceLocation mcanomalyarchives$grayOnClouding(ResourceLocation original, LivingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		return (CloudingVisualState.isGray(entity) || CloudingGrayTextures.shouldGray(entity))
				? CloudingGrayTextures.gray(original) : original;
	}
}
