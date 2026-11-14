package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.mcreator.mcanomalyarchives.anomaly.rewind.RewindService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RewindPlayerPacketMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = {"handleMovePlayer", "handleMoveVehicle", "handleContainerClick", "handleContainerButtonClick",
            "handleSetCreativeModeSlot", "handlePlayerAction", "handleUseItem", "handleUseItemOn", "handleInteract", "handlePlaceRecipe", "handlePickItem"},
            at = @At("HEAD"), cancellable = true)
    private void rewind$hold(CallbackInfo ci) {
        // These handlers may initially enter on Netty; only consult server-owned state on its thread.
        if (player.getServer().isSameThread() && RewindService.locked(player)) ci.cancel();
    }
}
