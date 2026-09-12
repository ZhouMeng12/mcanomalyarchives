package net.mcreator.mcanomalyarchives.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

@Mixin(Player.class)
public abstract class PlayerSlowSwingMixin {

	@Inject(method = "getArmSwingAnimationEnd", at = @At("HEAD"), cancellable = true)
	public void getArmSwingAnimationEnd(CallbackInfoReturnable<Integer> cir) {
		Player player = (Player) (Object) this;
		if (!player.level().isClientSide()) return;
		if (net.mcreator.mcanomalyarchives.event.PurpleDogPettingClientHandler.isPetting(player.getUUID())) {
			cir.setReturnValue(40);
		} else if (player.getMainHandItem().getItem() instanceof net.mcreator.mcanomalyarchives.item.PurplehandItem) {
			cir.setReturnValue(0);
		}
	}
}
