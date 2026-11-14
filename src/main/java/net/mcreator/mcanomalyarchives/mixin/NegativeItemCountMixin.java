package net.mcreator.mcanomalyarchives.mixin;

import net.mcreator.mcanomalyarchives.item.NegativeCountItem;
import net.mcreator.mcanomalyarchives.anomaly.negative.NegativeItemValues;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Let vanilla draw the signed count, with its own font, shadow, placement and depth. */
@Mixin(GuiGraphics.class)
public abstract class NegativeItemCountMixin {
    @Inject(method="renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at=@At("HEAD"),cancellable=true)
    private void negative$count(Font font,ItemStack stack,int x,int y,String label,CallbackInfo ci) {
        if(stack.isEmpty()||!(stack.getItem() instanceof NegativeCountItem))return;
        String signed=NegativeItemValues.countLabel(stack.getCount());
        // Re-enter once with the native count-label argument; the matching label
        // ends recursion, and the original path draws exactly one count.
        if(signed.equals(label))return;
        ((GuiGraphics)(Object)this).renderItemDecorations(font,stack,x,y,signed);
        ci.cancel();
    }
}
