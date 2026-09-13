package net.mcreator.mcanomalyarchives.dialogue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 剧情系统的日志出口（写脚本的人要靠它排查"为什么这段没播"）。
 *
 * 【工程层归属】dialogue 包（非 MCreator 生成区）。
 */
public final class DialogueLog {

	private static final Logger LOGGER = LoggerFactory.getLogger("mcanomalyarchives/story");

	private DialogueLog() {
	}

	public static void info(String message) {
		LOGGER.info("[剧情] {}", message);
	}

	public static void warn(String source, String message) {
		LOGGER.warn("[剧情] {}: {}", source, message);
	}

	public static void error(String source, String message, Throwable t) {
		LOGGER.error("[剧情] {}: {}", source, message, t);
	}
}
