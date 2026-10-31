package net.mcreator.mcanomalyarchives.dialogue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 服务器线程持有的脚本缓存；文件来源可注入，以真实临时文件验证热重载。 */
final class StoryRepository {
	private static final Pattern NAME = Pattern.compile("[a-z0-9_-][a-z0-9_.-]*");
	private record Cached(Story story, String source, FileTime modified, long size) { }
	private final Map<String, Cached> cache = new HashMap<>();
	private final Supplier<Path> directory;
	private final Function<String, Story> builtin;

	StoryRepository(Supplier<Path> directory, Function<String, Story> builtin) {
		this.directory = directory;
		this.builtin = builtin;
	}

	Story get(String name) {
		if (!validName(name)) return null;
		Cached known = cache.get(name);
		return known != null ? known.story() : load(name, false);
	}

	Story reload(String name) { return load(name, true); }
	String sourceOf(String name) {
		Cached known = cache.get(name);
		return known == null ? null : known.source();
	}
	void clear() { cache.clear(); }

	void refresh() {
		var names = new HashSet<>(cache.keySet());
		Path dir = directory.get();
		if (Files.isDirectory(dir)) {
			try (var files = Files.list(dir)) {
				files.filter(p -> p.getFileName().toString().endsWith(".txt")).forEach(p -> {
					String filename = p.getFileName().toString();
					names.add(filename.substring(0, filename.length() - 4));
				});
			} catch (IOException e) {
				DialogueLog.error("stories", "扫描目录失败：" + dir, e);
			}
		}
		// 也检查曾来自 config 的名称，使删除覆盖文件后能够恢复内置版。
		for (String name : names) load(name, false);
	}

	private Story load(String name, boolean force) {
		if (!validName(name)) return null;
		Cached known = cache.get(name);
		Path override = directory.get().resolve(name + ".txt");
		if (Files.isRegularFile(override)) {
			try {
				BasicFileAttributes attrs = Files.readAttributes(override, BasicFileAttributes.class);
				if (!force && known != null && "config".equals(known.source())
						&& attrs.lastModifiedTime().equals(known.modified()) && attrs.size() == known.size()) {
					return known.story();
				}
				Story story = Story.parse(Files.readString(override, StandardCharsets.UTF_8), name + "(config)");
				if (story.isUsable()) {
					cache.put(name, new Cached(story, "config", attrs.lastModifiedTime(), attrs.size()));
					if (known == null || !"config".equals(known.source())) {
						DialogueLog.info("剧情「" + name + "」来自 config 覆盖：" + override);
					}
					return story;
				}
				DialogueLog.warn(name, "config 版没有可用段落，保留上一份可用脚本");
			} catch (IOException e) {
				DialogueLog.error(name, "读取 config 覆盖失败：" + override, e);
			}
			if (known != null) return known.story();
		}
		Story story = builtin.apply(name);
		if (story != null && story.isUsable()) {
			cache.put(name, new Cached(story, "mod", null, 0));
			if (known == null || !"mod".equals(known.source())) {
				DialogueLog.info("剧情「" + name + "」来自数据包/模组内置版");
			}
			return story;
		}
		if (known == null) DialogueLog.error(name, "剧本缺失，config 与内置版均无可用脚本", null);
		return known == null ? null : known.story();
	}

	private static boolean validName(String name) {
		// 限制为直接子文件名，避免目录越界与不合法的注册表资源名。
		return name != null && NAME.matcher(name).matches();
	}
}
