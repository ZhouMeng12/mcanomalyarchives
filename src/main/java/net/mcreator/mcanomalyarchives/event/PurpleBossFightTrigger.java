package net.mcreator.mcanomalyarchives.event;

import net.minecraft.server.level.ServerPlayer;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;

/**
 * 紫怪 Boss 战触发扩展点（当前为占位）。
 * 后续在此接入真正的 Boss 战：生成 Boss 实体、窥视者转战斗模式、Boss 血条、技能、掉落等。
 */
public final class PurpleBossFightTrigger {

	/** Boss 战触发冷却：10 分钟 */
	public static final long BOSS_FIGHT_COOLDOWN_TICKS = 12000;

	private PurpleBossFightTrigger() {
	}

	/**
	 * 尝试触发 Boss 战。成功返回 true 并记录冷却。
	 * 目前只做校验 + 日志占位，不生成 Boss。
	 */
	public static boolean tryTriggerBossFight(ServerPlayer player) {
		if (player.gameMode.isCreative() || player.isSpectator()) return false;
		if (!player.getData(McanomalyarchivesModAttachments.PURPLE_ASSIMILATION_COMPLETED)) return false;
		long cooldown = player.getData(McanomalyarchivesModAttachments.PURPLE_BOSS_FIGHT_COOLDOWN);
		long now = player.level().getGameTime();
		if (cooldown > 0 && now - cooldown < BOSS_FIGHT_COOLDOWN_TICKS) return false;
		player.setData(McanomalyarchivesModAttachments.PURPLE_BOSS_FIGHT_COOLDOWN, now);
		McanomalyarchivesMod.LOGGER.info("[PurpleBoss] Boss fight triggered by player {} - TODO: spawn boss here", player.getName().getString());
		return true;
	}
}
