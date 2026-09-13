package net.mcreator.mcanomalyarchives.codex;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 见闻录：周期性扫描 + 死亡重生时的数据搬运。
 *
 * 扫描不是每 tick 做的（12 条 × 每玩家 × 每 tick 太浪费），而是每 {@link #SCAN_INTERVAL} tick
 * 对在线玩家各扫一次：附近有该异常实体、或背包里有对应物品 → 解锁对应档案。
 *
 * 【工程层归属】codex 包（非 MCreator 生成区）。
 */
public final class CodexScanner {

	/** 扫描间隔：2 秒。解锁是"记录"而不是"反应"，慢一点完全够用 */
	private static final int SCAN_INTERVAL = 40;

	public static void init() {
		NeoForge.EVENT_BUS.register(CodexScanner.class);
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (event.getServer().getTickCount() % SCAN_INTERVAL != 0)
			return;
		// 剧情脚本热重载：文件被改过就重新解析（详见 dialogue/StoryLoader）
		net.mcreator.mcanomalyarchives.dialogue.StoryLoader.tick(event.getServer().overworld().getGameTime());
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			// 创造/旁观也算"见过"：作者调试与旁观玩家同样应该能记录档案
			AnomalyCodex.scan(player);
		}
	}

	/** 死亡重生会把玩家实体换掉，档案位掩码要手动搬过去 */
	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		if (event.getOriginal() instanceof ServerPlayer oldPlayer && event.getEntity() instanceof ServerPlayer newPlayer) {
			AnomalyCodex.copyOnRespawn(oldPlayer, newPlayer);
		}
	}
}
