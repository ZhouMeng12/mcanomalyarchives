package net.mcreator.mcanomalyarchives.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

import net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity;
import net.mcreator.mcanomalyarchives.procedures.CloudDemChuanSongMenTongGuoTiaoJianProcedure;

/**
 * 让小伪云**自己也能穿过云室传送门**。
 *
 * <p>云室门的通行条件是 {@code CloudDemChuanSongMenTongGuoTiaoJianProcedure}：
 * 能跨维度 **且身上带云化效果**（那是给玩家用的，得先喝云水）。小伪云本来就是云，
 * 不该要求它先去喝一瓶云水，所以在这里给条件开个后门。
 *
 * <p>为什么注入这个"过程类"而不是给实体挂云化效果：云化效果自带一套表现
 * （失去 AI、灰白贴图、水平飘移），挂在云身上会把它变成一坨灰的；
 * 而注入条件是零副作用的一行判断（跟 {@code CloudPortalEmitMixin} 一样都写手写区）。
 *
 * <p>防反复出入门靠实体自己的 {@code getDimensionChangingDelay()}（见 SmallStangeCloudEntity）：
 * 原版在传送时 {@code setPortalCooldown()} 用的就是那个值，小伪云把它拉到比寿命还长，
 * 于是一只云一生只能过门一次。
 */
@Mixin(CloudDemChuanSongMenTongGuoTiaoJianProcedure.class)
public abstract class CloudPortalPassMixin {

	@Inject(method = "execute", at = @At("HEAD"), cancellable = true)
	private static void mcanomalyarchives$smallCloudMayPass(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof SmallStangeCloudEntity) {
			cir.setReturnValue(true);
		}
	}
}
