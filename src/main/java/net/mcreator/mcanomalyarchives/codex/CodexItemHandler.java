package net.mcreator.mcanomalyarchives.codex;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 见闻录：右键打开界面。
 *
 * 承载物品（见闻录）是 MCreator 元素，这里只监听"右键它"这件事，
 * 因此 MCreator 无论怎么重新生成那个物品类，都不影响打开逻辑
 * （只要注册名还是 mcanomalyarchives:anomaly_codex）。
 *
 * 【工程层归属】codex 包（非 MCreator 生成区）。
 */
public final class CodexItemHandler {

	public static void init() {
		NeoForge.EVENT_BUS.register(CodexItemHandler.class);
	}

	@SubscribeEvent
	public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		if (!event.getItemStack().is(McanomalyarchivesModItems.ANOMALY_CODEX.get()))
			return;
		event.setCanceled(true);
		// 服务端先读权威状态，再连同"打开界面"的请求一起发给客户端
		CodexNotifier.syncTo(player, true);
	}
}
