package net.mcreator.mcanomalyarchives.mixin;

import net.mcreator.mcanomalyarchives.anomaly.nametag.NamedState;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 2026-10-24 起显示目标外观。稳定产物直接用真实模型，不稳定产物用无标记临时栈查询目标模型。 */
@Mixin(ItemRenderer.class)
public abstract class NamedItemAppearanceMixin {

	@Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
	private void mcanomalyarchives$drawTargetAppearance(ItemStack stack, Level level, LivingEntity entity, int seed,
			CallbackInfoReturnable<BakedModel> cir) {
		// 2026-10-24：改为目标外观；稳定产物本体已是目标，直接用原版模型。
        if (stack.is(net.minecraft.world.item.Items.NETHERITE_PICKAXE)
                && net.minecraft.resources.ResourceLocation.withDefaultNamespace("wooden_shovel").equals(NamedState.appearanceOf(stack))) {
            cir.setReturnValue(((ItemRenderer)(Object)this).getModel(new ItemStack(net.minecraft.world.item.Items.WOODEN_SHOVEL), level, entity, seed));
            return;
        }
		if (!NamedState.isUnstable(stack)) return;
		var target = NamedState.targetOf(stack);
		if (target == null) return;
		ItemStack fake = net.mcreator.mcanomalyarchives.anomaly.nametag.MaterialUnits.itemFormOf(target);
		if (fake.isEmpty() || fake.getItem() == stack.getItem()) return;
		cir.setReturnValue(((ItemRenderer) (Object) this).getModel(fake, level, entity, seed));
	}
}
