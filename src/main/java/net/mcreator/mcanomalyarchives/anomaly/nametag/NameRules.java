package net.mcreator.mcanomalyarchives.anomaly.nametag;

import java.util.Locale;
import java.util.Set;

/**
 * 覆盖表：不参与注册表查找的"高概念词"，优先级最高。
 *
 * 【设定依据】正片 BV1YiGt6vEbK：
 * - 【旁白 6:10-6:31】D187 被命名成「无」→ 当场消失，且"没有人记得起该人员的所有信息和回忆"，
 *   回放监控发现"所有人都在和空气做交互"——**认知性抹除个体存在**。
 * - 【旁白 6:00-6:10】木棍被命名成一串毫无规律的字符 → "立即变成了一个和 UO-002 同源的乱码物品"。
 * - 【旁白 6:31-7:47】兔子被命名成「地球」→ 时空扭曲、发出刺眼黄光，
 *   靠 UO-007 的负值唱片回溯才兜住（**需要专门的兜底机制，尚未实装**）。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameRules {

	/** 覆盖表命中后的处理方式。 */
	public enum Special {
		/** 不在覆盖表里，走正常的注册表解析 */
		NONE,
		/** 「无」：认知性抹除 */
		ERASE,
		/** 「地球」：极限命名（正片靠负值唱片兜底，本模组尚未实装） */
		EARTH,
		/** 乱码名字：与 UO-002 同源的乱码物品 */
		GARBAGE
	}

	private static final Set<String> ERASE = Set.of("无", "空", "無", "nothing", "void", "null", "none", "empty");

	private static final Set<String> EARTH = Set.of("地球", "earth", "the earth", "theearth");

	/** 出现这些字符、且不在索引里的名字，判为"乱码"。 */
	private static final String SYMBOLS = "!@#$%^&*()_+={}[]|\\/<>?~`";

	private NameRules() {
	}

	public static Special special(String rawName) {
		String name = NameResolver.normalize(rawName);
		if (name.isEmpty()) {
			return Special.ERASE;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		if (ERASE.contains(lower) || ERASE.contains(name)) {
			return Special.ERASE;
		}
		if (EARTH.contains(lower) || EARTH.contains(name)) {
			return Special.EARTH;
		}
		if (looksLikeGarbage(name)) {
			return Special.GARBAGE;
		}
		return Special.NONE;
	}

	/**
	 * 乱码判定：又含有标点符号、又找不到任何注册表对应物的短字符串。
	 * 判定放在解析失败之后（调用方先确认 {@link NameResolver#resolve} 返回 null）。
	 */
	private static boolean looksLikeGarbage(String name) {
		if (name.length() < 3) {
			return false;
		}
		int symbols = 0;
		for (int i = 0; i < name.length(); i++) {
			if (SYMBOLS.indexOf(name.charAt(i)) >= 0) {
				symbols++;
			}
		}
		return symbols >= 2;
	}
}
