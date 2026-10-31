package net.mcreator.mcanomalyarchives.anomaly.fishing;

import net.mcreator.mcanomalyarchives.mixin.FishingHookAccessor;
import net.mcreator.mcanomalyarchives.runtime.ServerTasks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;

/** 异常钓竿的抛竿会话；物品生成文件只负责调用这个入口。 */
public final class StrangeRodCasting {
	private StrangeRodCasting() { }

	public static void onCast(ServerPlayer player) {
		FishingHook hook = player.fishing;
		if (hook == null) return;
		Level level = player.level();
		// 绑定本次抛出的钩子；重抛、离线或换维度后，旧回调不能串到新钩子。
		ServerTasks.scheduleForPlayer(player, 5, () -> {
			if (player.fishing == hook && hook.isAlive() && hook.level() == level && player.level() == level) {
				double factor = Math.min(hook.distanceTo(player) / 15.0, 1.0);
				int randomExtra = 200 + hook.getRandom().nextInt(400);
				int biteTime = 40 + (int) (factor * randomExtra);
				((FishingHookAccessor) hook).mcanomalyarchives$setTimeUntilLured(biteTime);
			}
		});
	}
}
