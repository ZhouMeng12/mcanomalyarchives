package net.mcreator.mcanomalyarchives.dialogue;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 剧情脚本的加载与**热重载**。
 *
 * <p>脚本放在 {@code config/mcanomalyarchives/stories/} 下，一个故事一个 {@code .txt}。
 * 首次启动会把内置的默认脚本写出来（就是《见闻录》的那段剧情），作者直接改这份文件即可。
 *
 * <p>热重载：每 {@link #CHECK_INTERVAL} tick 比一次文件的修改时间，变了就重新解析，
 * 不需要重启游戏、不需要重新编译。解析失败会保留上一份可用的脚本，
 * 并在日志里说明哪一行有问题 —— 绝不会因为写错一个字就让剧情整体消失。
 *
 * 【工程层归属】dialogue 包（非 MCreator 生成区）。
 */
public final class StoryLoader {

	/** 检查间隔（tick）：调用方每 40 tick 进一次，这里按 200 tick（约 10 秒）实际触发一次 */
	private static final int CHECK_INTERVAL = 200;

	private static final Map<String, Story> CACHE = new HashMap<>();
	private static final Map<String, Long> STAMPS = new HashMap<>();
	private static long lastCheck = 0;

	private StoryLoader() {
	}

	public static Path storiesDir() {
		return FMLPaths.CONFIGDIR.get().resolve("mcanomalyarchives").resolve("stories");
	}

	/** 取一个故事；文件不存在会先用默认内容创建出来 */
	public static Story get(String name) {
		long now = System.currentTimeMillis(); // 仅用于避免同一 tick 内反复 stat
		Story cached = CACHE.get(name);
		if (cached != null && now - lastCheck < 1000) {
			return cached;
		}
		return reload(name, null);
	}

	/** 每 tick 由服务端调用：到点就检查文件是否被改过 */
	public static void tick(long gameTime) {
		if (gameTime % CHECK_INTERVAL != 0) {
			return;
		}
		lastCheck = System.currentTimeMillis();
		Path dir = storiesDir();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.list(dir)) {
			stream.filter(p -> p.getFileName().toString().endsWith(".txt")).forEach(p -> {
				String name = p.getFileName().toString().replace(".txt", "");
				reload(name, p);
			});
		} catch (IOException e) {
			DialogueLog.error("stories", "扫描目录失败：" + dir, e);
		}
	}

	/** 重新读取并解析（改动过才真的重新解析） */
	public static Story reload(String name, Path fileOrNull) {
		Path file = fileOrNull != null ? fileOrNull : storiesDir().resolve(name + ".txt");
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, defaultScript(name), StandardCharsets.UTF_8);
				DialogueLog.info("已生成默认剧情脚本：" + file);
			}
			long stamp = Files.getLastModifiedTime(file).toMillis();
			Long known = STAMPS.get(name);
			if (known != null && known == stamp && CACHE.containsKey(name)) {
				return CACHE.get(name);
			}
			String raw = Files.readString(file, StandardCharsets.UTF_8);
			Story story = Story.parse(raw, name);
			if (story.isUsable()) {
				CACHE.put(name, story);
				STAMPS.put(name, stamp);
				DialogueLog.info("已加载剧情「" + name + "」：段落 " + story.labels().size() + " 个");
				return story;
			}
			DialogueLog.warn(name, "解析后没有任何可用段落，保留上一份");
		} catch (IOException e) {
			DialogueLog.error(name, "读取失败：" + file, e);
		}
		return CACHE.get(name);
	}

	/** 内置默认脚本（首次启动写出来，作者照它改） */
	private static String defaultScript(String name) {
		if ("svan_codex".equals(name)) {
			return DEFAULT_SVAN_CODEX;
		}
		return "# 空剧情：" + name + "\n[label: start]\n[end]\n";
	}

	/**
	 * 《见闻录》获取剧情的内置默认版。
	 * 与 codex/CodexOriginStory 之前的硬编码内容一致，作者可以随意改。
	 */
	public static final String DEFAULT_SVAN_CODEX = """
			# ===================== 《见闻录》：斯万剧情 =====================
			# 这是默认脚本，随便改。改完存盘，游戏里几秒内自动生效（不用重启）。
			#
			# 语法：
			#   台词            斯万: 文本
			#   带图的台词      斯万[图:mcanomalyarchives:item/anomalycodex]: 文本
			#   段落标签        [label: 名字]
			#   跳转            -> 名字
			#   选项            [choice]  然后一行 "? 提问"，若干行 "- 选项 -> 目标标签"
			#   停顿 N tick     [wait: 40]
			#   动作            [take_book] 收走一本普通的书
			#                   [give_codex] 给出《见闻录》
			#                   [sound:minecraft:block.enchantment_table.use]
			#                   [unlock:UO-001] 解锁见闻录里的一条档案
			#   结束            [end]
			# ==============================================================

			[label: start]
			斯万[图:mcanomalyarchives:item/anomalycodex]: ……你手里那本，是空白的。
			斯万: 你见过它们了，对吧？
			[choice]
			? 你想先听哪一件？
			- 天上那些云 -> cloud
			- 会走路的树 -> tree
			- 那朵花 -> flower

			[label: cloud]
			斯万: 那不是云。
			斯万: 它有三副样子：温顺的、会变的，还有一种专挑低处出现。
			斯万: 它不怕你，它怕被记下来。
			-> middle

			[label: tree]
			斯万: 山里那棵黑的，是活的。
			斯万: 你砍得越多，它离你越近。
			斯万: 砍一棵，种一棵——这是唯一的规矩。
			-> middle

			[label: flower]
			斯万: 虞美人。别直视它。
			斯万: 被它迷住的人，最后死在花边上，脸上还会长出新的花。
			斯万: 看一眼就够，别停。
			-> middle

			[label: middle]
			斯万: ……问得好。
			斯万: 但你问到的，都只是碎片。
			斯万: 真正的麻烦不是它们。是没人记得。
			[choice]
			? ……那你打算怎么办？
			- 我来记。 -> remember
			- 记得住又怎样？ -> pointless

			[label: remember]
			斯万: 好。
			-> give

			[label: pointless]
			斯万: 至少你不会死得不明不白。
			-> give

			[label: give]
			斯万: 把你的书给我。
			[take_book]
			[sound:minecraft:block.enchantment_table.use]
			斯万: ……成了。
			斯万[图:mcanomalyarchives:item/anomalycodex]: 《见闻录》。往后你见过什么，它自己会写。
			斯万: 别弄丢了。它不是只给你一个人的。
			[give_codex]
			[end]
			""";
}
