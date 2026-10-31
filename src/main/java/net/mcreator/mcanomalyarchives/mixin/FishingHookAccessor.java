package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 精确访问 1.21.1 的诱鱼等待计时，避免按整数当前值猜测字段。 */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("timeUntilLured")
	void mcanomalyarchives$setTimeUntilLured(int ticks);
}
