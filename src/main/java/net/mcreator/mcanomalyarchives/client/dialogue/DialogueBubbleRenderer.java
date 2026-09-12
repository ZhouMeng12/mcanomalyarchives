package net.mcreator.mcanomalyarchives.client.dialogue;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.mcreator.mcanomalyarchives.client.clouding.CloudingVisualState;

import org.joml.Vector3f;

/**
 * 头顶文字气泡渲染器（屏幕空间投影）。
 * 位置：每帧用实体的插值渲染坐标 + 当前相机做点积投影，与画面完全同步。
 * 淡入淡出：毫秒时间戳驱动，与 tick 无关，平滑无跳变。
 */
public class DialogueBubbleRenderer {

	private static final float MAX_DISTANCE = 24.0f;
	private static final int IMAGE_SIZE = 40;
	private static final int PADDING = 6;
	private static final long FADE_MS = 500;

	public static void init() {
		if (FMLEnvironment.dist.isClient()) {
			NeoForge.EVENT_BUS.register(new DialogueBubbleRenderer());
		}
	}

	@SubscribeEvent
	public void onRenderGui(RenderGuiEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.options.hideGui) return;
		long nowMs = System.currentTimeMillis();
		float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
		GuiGraphics graphics = event.getGuiGraphics();

		for (DialogueClientState.ActiveBubble bubble : DialogueClientState.bubbles()) {
			if (nowMs >= bubble.expireAtMs) {
				DialogueClientState.clearBubble(bubble.entityId);
				continue;
			}
			Entity entity = mc.level.getEntity(bubble.entityId);
			if (entity == null) {
				DialogueClientState.clearBubble(bubble.entityId);
				continue;
			}
			if (entity.distanceToSqr(mc.player) > MAX_DISTANCE * MAX_DISTANCE) {
				continue;
			}
			// 插值渲染坐标（而非 tick 坐标），气泡才能和实体画面位置完全同步
			Vec3 anchor = entity.getPosition(partialTick).add(0, entity.getBbHeight() + 0.6, 0);
			int[] screen = projectToScreen(mc, graphics, anchor);
			if (screen == null) continue;
			drawBubble(graphics, mc, bubble, entity, screen[0], screen[1], nowMs);
		}
	}

	/** 退出世界时清空气泡与灰白标记，避免跨存档残留 */
	@SubscribeEvent
	public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		DialogueClientState.clearAll();
		CloudingVisualState.clearAll();
	}

	/**
	 * 世界坐标 → 屏幕坐标（相机空间点积投影）。
	 * 把锚点相对相机的位置分解到相机的右/上/前三个轴上，再用垂直 FOV 做透视除法。
	 */
	private int[] projectToScreen(Minecraft mc, GuiGraphics graphics, Vec3 anchor) {
		Camera camera = mc.gameRenderer.getMainCamera();
		Vec3 d = anchor.subtract(camera.getPosition());
		Vector3f look = camera.getLookVector();
		Vector3f up = camera.getUpVector();
		Vector3f left = camera.getLeftVector();

		// 相机空间坐标：right = -left
		double cx = -d.x * left.x - d.y * left.y - d.z * left.z;
		double cy = d.x * up.x + d.y * up.y + d.z * up.z;
		double cz = d.x * look.x + d.y * look.y + d.z * look.z;
		if (cz <= 0.1) return null; // 在相机背后（或紧贴相机）

		// 焦距（像素）：垂直 FOV 一半的正切换算
		double fovRad = Math.toRadians(mc.options.fov().get());
		double focal = (graphics.guiHeight() * 0.5) / Math.tan(fovRad * 0.5);

		int sx = (int) Math.round(graphics.guiWidth() * 0.5 + (cx / cz) * focal);
		int sy = (int) Math.round(graphics.guiHeight() * 0.5 - (cy / cz) * focal);

		// 明显出屏（留 25% 余量）就跳过
		if (sx < -graphics.guiWidth() * 0.25 || sx > graphics.guiWidth() * 1.25
				|| sy < -graphics.guiHeight() * 0.25 || sy > graphics.guiHeight() * 1.25) {
			return null;
		}
		return new int[] { sx, sy };
	}

	private void drawBubble(GuiGraphics graphics, Minecraft mc, DialogueClientState.ActiveBubble bubble, Entity entity, int cx, int cy, long nowMs) {
		// 基于毫秒的平滑淡入淡出
		float ageMs = nowMs - bubble.spawnAtMs;
		float fadeIn = Math.min(1.0f, ageMs / FADE_MS);
		float fadeOut = Math.min(1.0f, (bubble.expireAtMs - nowMs) / FADE_MS);
		int alpha = (int) (255 * Math.min(fadeIn, fadeOut));
		if (alpha <= 0) return;

		// 前 400ms 弹入缩放（0.75 → 1.0，围绕头顶锚点）
		float scale = 0.75f + 0.25f * Math.min(1.0f, ageMs / 400f);

		Font font = mc.font;
		int imageSize = bubble.image != null ? IMAGE_SIZE : 0;
		int lineHeight = font.lineHeight + 1;
		int textHeight = bubble.lines.size() * lineHeight;
		int nameHeight = bubble.hasName ? font.lineHeight : 0;
		// 无自定义名时按实体类型翻译（zh_cn 中文名）
		String displayName = bubble.hasName ? bubble.speakerName : Component.translatable(entity.getType().getDescriptionId()).getString();

		int innerWidth = Math.max(60, bubble.textWidth);
		if (imageSize > 0) innerWidth += imageSize + 4;
		int width = innerWidth + PADDING * 2;
		int height = Math.max(textHeight, imageSize) + nameHeight + PADDING * 2;

		int x = cx - width / 2;
		int y = cy - height;
		x = Math.max(2, Math.min(x, graphics.guiWidth() - width - 2));
		y = Math.max(2, Math.min(y, graphics.guiHeight() - height - 2));

		int bgColor = withAlpha(0xFFFFFFFF, (int) (alpha * 0.92f));
		int borderColor = withAlpha(0xFF6E6E6E, alpha);

		// 弹入缩放：以头顶锚点为原点整体缩放
		graphics.pose().pushPose();
		graphics.pose().translate(cx, cy, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.pose().translate(-cx, -cy, 0);

		// 底 + 描边
		graphics.fill(x, y, x + width, y + height, bgColor);
		graphics.fill(x, y, x + width, y + 1, borderColor);
		graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
		graphics.fill(x, y, x + 1, y + height, borderColor);
		graphics.fill(x + width - 1, y, x + width, y + height, borderColor);

		// 指向说话人的小三角（像素金字塔，向下）
		int tailStart = y + height;
		int tailEnd = Math.min(cy + 4, graphics.guiHeight() - 1);
		if (tailEnd > tailStart) {
			int tcx = Math.max(x + 4, Math.min(cx, x + width - 4));
			int rows = Math.min(tailEnd - tailStart, 5);
			for (int i = 0; i < rows; i++) {
				int half = Math.max(1, 3 - i / 2);
				graphics.fill(tcx - half, tailStart + i, tcx + half + 1, tailStart + i + 1, bgColor);
			}
		}

		// 内容
		int textX = x + PADDING + (imageSize > 0 ? imageSize + 4 : 0);
		int textY = y + PADDING + nameHeight;
		if (bubble.hasName) {
			graphics.drawString(font, displayName, x + PADDING, y + PADDING, withAlpha(0xFF3A7BD5, alpha));
		}
		for (int i = 0; i < bubble.lines.size(); i++) {
			graphics.drawString(font, bubble.lines.get(i), textX, textY + i * lineHeight, withAlpha(bubble.color, alpha));
		}
		if (bubble.image != null) {
			graphics.blit(bubble.image, x + PADDING, y + PADDING + nameHeight,
					0, 0, imageSize, imageSize, imageSize, imageSize);
		}

		graphics.pose().popPose();
	}

	private static int withAlpha(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0x00FFFFFF);
	}
}
