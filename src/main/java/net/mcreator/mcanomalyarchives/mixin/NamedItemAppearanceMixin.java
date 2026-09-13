package net.mcreator.mcanomalyarchives.mixin;

import net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 百变命名牌：让"被命名的物品"画成它**原本的样子**。
 *
 * 【为什么需要这一手】作者要求被命名的东西"只有性质变、外观不变"。但"能合成钻石装备"
 * 这类性质在 Minecraft 里由**物品本体**决定（配方按 Item 匹配，数据组件救不了），
 * 所以本体必须真的换成目标物品——**于是外观就得画回来**。
 *
 * 1.21.1 里所有物品模型的解析都收敛到 {@code ItemRenderer.getModel}：
 * 手上的、丢在地上的、物品展示框里的、GUI 格子里的一律走它
 * （{@code renderStatic} 与 {@code render} 都调用它）。所以在 HEAD 处拦截，
 * 用一个"源物品"的临时 ItemStack 去问一次模型即可。临时 stack 没有标记，
 * 递归进来会在第一行直接返回，不会无限递归。
 *
 * 客户端 mixin，注册见 {@code mcanomalyarchives.mixins.json} 的 client 段。
 */
@Mixin(ItemRenderer.class)
public abstract class NamedItemAppearanceMixin {

	@Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
	private void mcanomalyarchives$drawSourceAppearance(ItemStack stack, Level level, LivingEntity entity, int seed,
			CallbackInfoReturnable<BakedModel> cir) {
		ResourceLocation appearance = NamedState.appearanceOf(stack);
		if (appearance == null) {
			return;
		}
		Item source = BuiltInRegistries.ITEM.get(appearance);
		if (source == Items.AIR || source == stack.getItem()) {
			// 取不到源物品，或者本体就和要画的一样，交给原版
			return;
		}
		ItemStack fake = new ItemStack(source);
		cir.setReturnValue(((ItemRenderer) (Object) this).getModel(fake, level, entity, seed));
	}
}
