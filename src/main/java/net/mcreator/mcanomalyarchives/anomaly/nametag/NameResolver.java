package net.mcreator.mcanomalyarchives.anomaly.nametag;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 名字 → 注册表对象 的解析器。
 *
 * 【为什么需要一张静态索引】名字是玩家在铁砧输入框里打的文本，服务端拿到的是字符串，
 * 而服务端的 {@code Language} 只有 en_us（中文名在服务端根本解析不出来）。
 * 所以离线从客户端 jar 的语言文件里生成一张
 * "显示名 → item/block/entity:id" 的表，随模组一起发布（服务端直接加载，零网络代码）。
 * 生成脚本：{@code tools/nametag/gen_name_index.py}。
 *
 * 【未命中就是不生效】正片设定：只认"主流叫法"——观众实测把狗命名成"地蛋"（土豆的别称）
 * 没有任何效果（弹幕 240.6s）。这条正好天然解释了"为什么它不是万能许愿机"。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameResolver {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("mcanomalyarchives/nametag");

	private static final String ZH = "/data/mcanomalyarchives/name_index/zh_cn.json";
	private static final String EN = "/data/mcanomalyarchives/name_index/en_us.json";
	private static final String ALIAS_ZH = "/data/mcanomalyarchives/name_index/alias_zh_cn.json";
	private static final String ALIAS_EN = "/data/mcanomalyarchives/name_index/alias_en_us.json";

	/** 显示名 → 候选列表（顺序即优先顺序，本模组条目排在原版前面）。 */
	private static Map<String, List<ResolvedName>> index;
	/** 英文名的小写形式也认（玩家手打英文时大小写随意）。 */
	private static Map<String, List<ResolvedName>> lowerIndex;
	/**
	 * 口语别名表。原版 Java 版的官方译名和玩家习惯叫法对不上的地方：
	 * stone 官方是"石头"（"原石"是基岩版叫法）、sheep 官方是"绵羊"（不是"羊"）、
	 * wolf 官方是"狼"（不是"狗"）。索引里查不到时落到这里，避免把玩家的正常输入判成"查无此名"。
	 */
	private static Map<String, List<ResolvedName>> aliases;
	private static Map<String, List<ResolvedName>> lowerAliases;

	private NameResolver() {
	}

	private static synchronized void ensureLoaded() {
		if (index != null) {
			return;
		}
		Map<String, List<ResolvedName>> built = new HashMap<>();
		loadInto(built, ZH, true);
		loadInto(built, EN, true);
		Map<String, List<ResolvedName>> aliasMap = new HashMap<>();
		loadInto(aliasMap, ALIAS_ZH, false);
		loadInto(aliasMap, ALIAS_EN, false);
		lowerIndex = lowerKeys(built);
		lowerAliases = lowerKeys(aliasMap);
		aliases = aliasMap;
		index = built;
	}

	private static Map<String, List<ResolvedName>> lowerKeys(Map<String, List<ResolvedName>> source) {
		Map<String, List<ResolvedName>> lower = new HashMap<>();
		for (Map.Entry<String, List<ResolvedName>> e : source.entrySet()) {
			lower.putIfAbsent(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
		}
		return lower;
	}

	private static void loadInto(Map<String, List<ResolvedName>> into, String resource, boolean required) {
		try (InputStream in = NameResolver.class.getResourceAsStream(resource)) {
			if (in == null) {
				if (required) {
					LOGGER.warn("[nametag] 名字索引缺失：{}", resource);
				} else {
					LOGGER.info("[nametag] 没有别名表 {}（可选）", resource);
				}
				return;
			}
			JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return;
			}
			JsonObject obj = root.getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				String display = e.getKey();
				if (display.startsWith("__") || !e.getValue().isJsonArray()) {
					continue;
				}
				List<ResolvedName> list = new ArrayList<>();
				for (JsonElement tokenEl : e.getValue().getAsJsonArray()) {
					ResolvedName resolved = ResolvedName.parse(tokenEl.getAsString());
					if (resolved != null) {
						list.add(resolved);
					}
				}
				if (!list.isEmpty()) {
					into.putIfAbsent(display, list);
				}
			}
		} catch (Exception ex) {
			LOGGER.error("[nametag] 读取名字索引失败：{}", resource, ex);
		}
	}

	/**
	 * 把玩家输入的名字解析成一个目标。
	 *
	 * @param rawName  玩家在铁砧里输入的文本
	 * @param preferred 目标自身的类型；“同名优先选同类”靠它实现
	 *                 （"钻石块"既能指方块也能指物品，贴在方块上就选方块）
	 * @return 解析结果；未命中返回 {@code null}
	 */
	public static ResolvedName resolve(String rawName, ResolvedName.Kind preferred) {
		List<ResolvedName> candidates = candidates(rawName);
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		if (preferred != null) {
			for (ResolvedName c : candidates) {
				if (c.kind() == preferred) {
					return c;
				}
			}
		}
		return candidates.get(0);
	}

	/** 不做同类优先，直接返回候选列表（用于"跨类"判定与提示）。 */
	public static List<ResolvedName> candidates(String rawName) {
		ensureLoaded();
		String key = normalize(rawName);
		if (key.isEmpty()) {
			return null;
		}
		List<ResolvedName> hit = index.get(key);
		if (hit == null) {
			hit = aliases.get(key);
		}
		if (hit == null) {
			String lower = key.toLowerCase(Locale.ROOT);
			hit = lowerIndex.get(lower);
			if (hit == null) {
				hit = lowerAliases.get(lower);
			}
		}
		return hit;
	}

	/** 去掉首尾空白、书名号/引号、格式码——玩家从别处复制名字时经常带着这些。 */
	public static String normalize(String rawName) {
		if (rawName == null) {
			return "";
		}
		String s = rawName.replaceAll("§.", "");
		s = s.replace('\u3000', ' ').trim();
		while (s.length() >= 2) {
			char first = s.charAt(0);
			char last = s.charAt(s.length() - 1);
			boolean quoted = (first == '"' && last == '"') || (first == '\'' && last == '\'')
					|| (first == '“' && last == '”') || (first == '‘' && last == '’')
					|| (first == '《' && last == '》') || (first == '「' && last == '」');
			if (!quoted) {
				break;
			}
			s = s.substring(1, s.length() - 1).trim();
		}
		return s;
	}

	/** 索引条目数（供日志与自检用）。 */
	public static int size() {
		ensureLoaded();
		return index.size();
	}
}
