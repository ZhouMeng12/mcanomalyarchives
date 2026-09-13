package net.mcreator.mcanomalyarchives.codex;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.network.CodexSyncPacket;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 见闻录：解锁时的反馈 + 状态同步。
 *
 * 解锁一条档案时：
 * 1. 把最新位掩码同步给客户端（已打开界面的话会即时刷新）；
 * 2. 播放一声"翻页"音效；
 * 3. 在聊天栏给一行提示（带编号与名称，未实装的条目不会走到这里）。
 *
 * 【工程层归属】codex 包（非 MCreator 生成区）。
 */
public final class CodexNotifier {

	private CodexNotifier() {
	}

	/** 解锁回调（由 AnomalyCodex.unlock 调用，服务端） */
	public static void onUnlocked(ServerPlayer player, int index) {
		AnomalyCodex.Entry entry = AnomalyCodex.get(index);
		PacketDistributor.sendToPlayer(player, new CodexSyncPacket(AnomalyCodex.getMask(player), false));
		player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.displayClientMessage(Component.translatable("codex." + McanomalyarchivesMod.MODID + ".unlocked",
				entry.id(), Component.translatable(entry.nameKey())), false);
	}

	/** 打开界面时调用：只同步状态，不弹提示 */
	public static void syncTo(ServerPlayer player, boolean openScreen) {
		PacketDistributor.sendToPlayer(player, new CodexSyncPacket(AnomalyCodex.getMask(player), openScreen));
	}
}
