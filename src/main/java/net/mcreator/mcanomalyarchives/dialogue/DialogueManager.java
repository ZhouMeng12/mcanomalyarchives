package net.mcreator.mcanomalyarchives.dialogue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.neoforged.neoforge.network.PacketDistributor;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.network.DialogueBubblePacket;
import net.mcreator.mcanomalyarchives.network.ClearDialogueBubblePacket;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端对话管理：把台词按顺序变成实体头顶的气泡。
 * 仅限服务端调用；客户端渲染见 DialogueClientState / DialogueBubbleRenderer。
 */
public final class DialogueManager {

	private DialogueManager() {
	}

	/** 每实体对话会话号：新对话会打断旧对话未播完的台词 */
	private static final Map<UUID, Integer> SESSIONS = new HashMap<>();

	/** 每实体上次开口的游戏时间（防连点刷屏，约 0.5 秒冷却） */
	private static final Map<UUID, Long> LAST_SPEAK = new HashMap<>();

	private static final int SPEAK_COOLDOWN_TICKS = 10;

	/** 给追踪该实体的所有玩家广播对话（多条台词按顺序播放） */
	public static void speak(ServerLevel level, Entity speaker, DialogueLine... lines) {
		if (level == null || speaker == null || lines == null || lines.length == 0) return;
		speakInternal(level, speaker, null, Arrays.asList(lines));
	}

	/** 只给指定玩家播放对话 */
	public static void speakTo(ServerPlayer target, Entity speaker, DialogueLine... lines) {
		if (target == null || speaker == null || lines == null || lines.length == 0) return;
		if (!(speaker.level() instanceof ServerLevel level)) return;
		speakInternal(level, speaker, target, Arrays.asList(lines));
	}

	/** 打断实体的当前对话并清空气泡 */
	public static void interrupt(Entity speaker) {
		if (speaker == null) return;
		SESSIONS.merge(speaker.getUUID(), 1, Integer::sum);
		if (speaker.level() instanceof ServerLevel level) {
			sendClear(level, speaker, null);
		}
	}

	private static void speakInternal(ServerLevel level, Entity speaker, ServerPlayer target, List<DialogueLine> lines) {
		// 实体已移除：清理记录，避免 Map 无限增长
		if (speaker.isRemoved()) {
			SESSIONS.remove(speaker.getUUID());
			LAST_SPEAK.remove(speaker.getUUID());
			return;
		}
		long now = level.getGameTime();
		if (now - LAST_SPEAK.getOrDefault(speaker.getUUID(), now - SPEAK_COOLDOWN_TICKS - 1) < SPEAK_COOLDOWN_TICKS) {
			return;
		}
		LAST_SPEAK.put(speaker.getUUID(), now);
		int session = SESSIONS.merge(speaker.getUUID(), 1, Integer::sum);
		playLine(level, speaker, target, lines, 0, session);
	}

	private static void playLine(ServerLevel level, Entity speaker, ServerPlayer target, List<DialogueLine> lines, int index, int session) {
		if (speaker.isRemoved() || SESSIONS.getOrDefault(speaker.getUUID(), -1) != session) {
			return;
		}
		if (index >= lines.size()) {
			sendClear(level, speaker, target);
			return;
		}
		DialogueLine line = lines.get(index);
		if (line == null || line.text() == null || line.text().isEmpty()) {
			playLine(level, speaker, target, lines, index + 1, session);
			return;
		}
		DialogueBubblePacket packet = new DialogueBubblePacket(
				speaker.getId(),
				// 有自定义名用自定义名；否则发空串，由客户端按实体类型翻译（zh_cn 中文名）
				speaker.hasCustomName() ? speaker.getCustomName().getString() : "",
				line.text(),
				line.image() == null ? "" : line.image(),
				Math.max(20, line.durationTicks()),
				line.color());
		if (target != null) {
			PacketDistributor.sendToPlayer(target, packet);
		} else {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(speaker, packet);
		}
		int duration = Math.max(20, line.durationTicks());
		McanomalyarchivesMod.queueServerWork(duration, () -> playLine(level, speaker, target, lines, index + 1, session));
	}

	private static void sendClear(ServerLevel level, Entity speaker, ServerPlayer target) {
		ClearDialogueBubblePacket clear = new ClearDialogueBubblePacket(speaker.getId());
		if (target != null) {
			PacketDistributor.sendToPlayer(target, clear);
		} else {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(speaker, clear);
		}
	}
}
