package net.mcreator.mcanomalyarchives.client.purple;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** No stored lock: removing the actor immediately releases the camera and input. */
@EventBusSubscriber(modid = "mcanomalyarchives", value = Dist.CLIENT)
public final class PurpleExecutionCamera {
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) return;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PurpleMonsterEntity actor) || actor.executionTarget() != mc.player.getId()
                    || actor.executionTicks() >= 36 || mc.player.distanceToSqr(actor) > 256) continue;
            var delta = actor.getEyePosition().subtract(mc.player.getEyePosition());
            float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
            float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
            mc.player.setYRot(mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * .25f);
            mc.player.setXRot(Mth.lerp(.25f, mc.player.getXRot(), pitch));
            mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false); mc.options.keyRight.setDown(false);
            mc.player.xxa = 0; mc.player.zza = 0;
            return;
        }
    }
}
