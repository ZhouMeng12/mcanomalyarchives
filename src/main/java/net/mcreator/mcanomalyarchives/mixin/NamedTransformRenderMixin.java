package net.mcreator.mcanomalyarchives.mixin;

import net.mcreator.mcanomalyarchives.client.NamedMorphClient;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 命名牌：把"正在变成方块"的生物，**用自己的模型 + 目标方块的贴图再画一遍**，
 * 达成作者要的"两张贴图的真正混合"。
 *
 * <pre>
 *   第一遍：原版那一遍（生物自己的贴图）—— 完全不动，一个像素都不改
 *   第二遍：同一个模型、换成目标方块贴图、顶点色 alpha 随进度 0 → 255
 * </pre>
 *
 * 两遍叠起来就是"这块材质一点点盖满它的身体"。顶点色本来就带 alpha
 * （{@code VertexConsumer.setColor(int)} 按 ARGB 拆），配合
 * {@code RenderType.entityTranslucent} 的混合即可，**不需要自定义 shader**，
 * 也不需要把原版那一遍改成半透明（那会牵扯渲染类型与排序，风险大得多）。
 *
 * 时机选在 {@code model.renderToBuffer(...)} 调用**之后**：此时姿势栈里还留着这只生物的
 * 位移/旋转，模型也已经 {@code setupAnim} 过，所以直接再画一遍，位置与姿态与第一遍完全一致。
 *
 * 【方法描述符必须写全】`LivingEntityRenderer` 里有一个 `render(Entity, ...)` 的桥接方法，
 * 只写方法名会被 Mixin 判为"重载歧义"。描述符是对着
 * {@code javap -s -p net.minecraft.client.renderer.entity.LivingEntityRenderer} 抄的。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NamedTransformRenderMixin {

	private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
	private static final String RENDER_TO_BUFFER =
			"Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

	/** 原版渲染用的就是这个模型（{@code protected M model}）。 */
	@Shadow
	protected EntityModel<?> model;

	@Inject(method = RENDER, at = @At(value = "INVOKE", target = RENDER_TO_BUFFER, shift = At.Shift.AFTER))
	private void mcanomalyarchives$overlayTargetTexture(LivingEntity entity, float entityYaw, float partialTick,
			PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
		ResourceLocation blend = NamedMorphClient.blendTexture(entity);
		if (blend == null) {
			return;
		}
		int alpha = NamedMorphClient.targetAlpha(entity);
		if (alpha <= 0) {
			return;
		}
		VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(blend));
		this.model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFF | alpha << 24);
	}
}
