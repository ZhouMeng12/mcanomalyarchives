package net.mcreator.mcanomalyarchives.mixin;

import net.neoforged.neoforge.fluids.CauldronFluidContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 NeoForge 21.1.190 在 resource reload 时
 * RegistrationEvents.init() 重复触发 CauldronFluidContent.init()
 * 导致 "Duplicate cauldron registration for minecraft:cauldron" 崩溃。
 *
 * 拦截 IllegalArgumentException 的抛出点，静默返回。
 */
@Mixin(CauldronFluidContent.class)
public class CauldronFluidContentMixin {

	@Inject(
		method = "register",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/IllegalArgumentException;<init>(Ljava/lang/String;)V"
		),
		cancellable = true
	)
	private static void ignoreDuplicateCauldron(CallbackInfo ci) {
		ci.cancel();
	}
}
