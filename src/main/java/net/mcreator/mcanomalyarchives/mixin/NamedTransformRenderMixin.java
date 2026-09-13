package net.mcreator.mcanomalyarchives.mixin;

import net.mcreator.mcanomalyarchives.client.NamedMorphClient;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 命名牌：让"正在转化中"的生物**体型慢慢变、颜色慢慢变**。
 *
 * 三个注入点，全在 {@link LivingEntityRenderer}（1.21.1 的实体渲染都走它）：
 * <ol>
 *   <li>{@code render} 的 HEAD / RETURN：记下/还原"这一只该染成什么色"
 *       —— 用静态字段传值，避免依赖 MixinExtras 的局部变量捕获。</li>
 *   <li>{@code render} 里 {@code model.renderToBuffer(...)} 的最后一个参数（顶点色）：
 *       替换成我们算出来的颜色。顶点色是**乘算**，所以表现是"逐渐偏色/变暗"，
 *       这正是"材质慢慢变"。</li>
 *   <li>{@code scale} 的 TAIL：叠加一个从 1 到"目标高度比"的缩放，牛会一点点缩成鸡那么大。</li>
 * </ol>
 *
 * 全部只在 {@code NamedMorphClient} 返回非中性值时才动手；没有在转化的实体走原版路径，一个像素都不改。
 *
 * 【方法描述符必须写全】`LivingEntityRenderer` 里有一个 `render(Entity, ...)` 的桥接方法，
 * 只写方法名会被 Mixin 判为"重载歧义"。这里的描述符是对着
 * {@code javap -s -p net.minecraft.client.renderer.entity.LivingEntityRenderer} 抄的。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NamedTransformRenderMixin {

	private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
	private static final String SCALE = "scale(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V";
	private static final String RENDER_TO_BUFFER =
			"Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

	@Inject(method = RENDER, at = @At("HEAD"))
	private void mcanomalyarchives$beforeRender(LivingEntity entity, float entityYaw, float partialTick,
			PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
		NamedMorphClient.beginRender(entity);
	}

	@Inject(method = RENDER, at = @At("RETURN"))
	private void mcanomalyarchives$afterRender(LivingEntity entity, float entityYaw, float partialTick,
			PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
		NamedMorphClient.endRender();
	}

	@ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RENDER_TO_BUFFER), index = 4)
	private int mcanomalyarchives$tintModel(int original) {
		int tint = NamedMorphClient.currentTint();
		return tint == NamedMorphClient.NO_TINT ? original : tint;
	}

	@Inject(method = SCALE, at = @At("TAIL"))
	private void mcanomalyarchives$scaleModel(LivingEntity entity, PoseStack poseStack, float partialTick,
			CallbackInfo ci) {
		float scale = NamedMorphClient.scaleFor(entity);
		if (scale != 1.0f) {
			poseStack.scale(scale, scale, scale);
		}
	}
}
