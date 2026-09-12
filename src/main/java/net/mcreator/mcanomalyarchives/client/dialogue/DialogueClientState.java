package net.mcreator.mcanomalyarchives.client.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import net.mcreator.mcanomalyarchives.network.DialogueBubblePacket;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端对话气泡缓存：按实体 id 保存当前气泡，过期自动移除 */
public final class DialogueClientState {

	public static final int MAX_TEXT_WIDTH = 160;

	public static final class ActiveBubble {
		public final int entityId;
		public final String speakerName;
		public final String text;
		public final int color;
		/** 出生/过期时间戳（毫秒），淡入淡出与 tick 无关，保证平滑 */
		public final long spawnAtMs;
		public final long expireAtMs;
		/** 图片纹理与换行结果只在创建时计算一次，避免每帧重复分配 */
		@Nullable
		public final ResourceLocation image;
		public final List<FormattedCharSequence> lines;
		public final int textWidth;
		public final boolean hasName;

		public ActiveBubble(DialogueBubblePacket packet) {
			Minecraft mc = Minecraft.getInstance();
			this.entityId = packet.entityId();
			this.speakerName = packet.speakerName();
			this.text = packet.text();
			this.color = packet.color();
			this.spawnAtMs = System.currentTimeMillis();
			this.expireAtMs = this.spawnAtMs + Math.max(20, packet.durationTicks()) * 50L;
			this.image = parseImage(packet.imagePath());
			this.lines = mc.font.split(Component.literal(packet.text()), MAX_TEXT_WIDTH);
			int width = 0;
			for (FormattedCharSequence line : this.lines) {
				width = Math.max(width, mc.font.width(line));
			}
			this.textWidth = width;
			this.hasName = speakerName != null && !speakerName.isEmpty();
		}
	}

	private static final Map<Integer, ActiveBubble> BUBBLES = new ConcurrentHashMap<>();

	private DialogueClientState() {
	}

	public static void showBubble(DialogueBubblePacket packet) {
		BUBBLES.put(packet.entityId(), new ActiveBubble(packet));
	}

	public static void clearBubble(int entityId) {
		BUBBLES.remove(entityId);
	}

	public static void clearAll() {
		BUBBLES.clear();
	}

	public static Collection<ActiveBubble> bubbles() {
		return BUBBLES.values();
	}

	/** "mcanomalyarchives:item/orange" → ResourceLocation("mcanomalyarchives", "textures/item/orange.png") */
	@Nullable
	private static ResourceLocation parseImage(String path) {
		if (path == null || path.isEmpty()) return null;
		int colon = path.indexOf(':');
		if (colon <= 0) return null;
		String namespace = path.substring(0, colon);
		String rest = path.substring(colon + 1);
		if (!rest.startsWith("textures/")) rest = "textures/" + rest;
		if (!rest.endsWith(".png")) rest = rest + ".png";
		return ResourceLocation.fromNamespaceAndPath(namespace, rest);
	}
}
