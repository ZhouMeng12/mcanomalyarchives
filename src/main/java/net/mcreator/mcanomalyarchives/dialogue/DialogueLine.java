package net.mcreator.mcanomalyarchives.dialogue;

import javax.annotation.Nullable;

/**
 * 单条对话气泡。
 * image 路径约定：相对 assets/<命名空间>/textures/ 的路径，不带 .png 后缀，
 * 例如 "mcanomalyarchives:item/orange" → textures/item/orange.png。
 */
public record DialogueLine(String text, @Nullable String image, int durationTicks, int color, boolean showTail) {

	/** 纯文字气泡，时长按字数自动估算 */
	public static DialogueLine text(String text) {
		int duration = Math.min(220, Math.max(60, text.length() * 4 + 40));
		return new DialogueLine(text, null, duration, 0xFF1E1E1E, true);
	}

	/** 带图片的气泡 */
	public static DialogueLine withImage(String text, String image) {
		int duration = Math.min(280, Math.max(80, text.length() * 4 + 60));
		return new DialogueLine(text, image, duration, 0xFF1E1E1E, true);
	}
}
