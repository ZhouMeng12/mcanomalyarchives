package net.mcreator.mcanomalyarchives.dialogue;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 剧情脚本的加载与**热重载**。
 *
 * <h2>剧本存在哪</h2>
 * <ol>
 * <li><b>模组内置</b>（正式来源）：{@code data/mcanomalyarchives/story/<名字>.txt}，
 * 随 jar 一起发布、也进版本控制；数据包可以覆盖它。</li>
 * <li><b>config 覆盖</b>（可选）：{@code config/mcanomalyarchives/stories/<名字>.txt}，
 * 存在就用它。给作者"不进游戏就能改、改完几秒生效"用的；
 * 发布前记得把内容贴回模组内置那份。</li>
 * </ol>
 *
 * <p>热重载：每 {@link #CHECK_INTERVAL} tick 比一次 config 那份的修改时间并重新解析；
 * 解析失败会保留上一份可用脚本，不会因为写错一个字就让剧情消失。
 *
 * 【工程层归属】dialogue 包（非 MCreator 生成区）。
 */
public final class StoryLoader {

	/** 检查间隔（tick）：调用方每 40 tick 进一次，这里按 200 tick（约 10 秒）实际触发一次 */
	private static final int CHECK_INTERVAL = 200;
	/** 模组内置剧本的路径前缀（data/&lt;命名空间&gt;/story/&lt;名字&gt;.txt） */
	private static final String RESOURCE_PREFIX = "story/";

	private static final Map<String, Story> CACHE = new HashMap<>();
	private static final Map<String, String> SOURCE = new HashMap<>();
	private static final Map<String, Long> STAMPS = new HashMap<>();
	private static long lastCheck = 0;

	private StoryLoader() {
	}

	/** config 里的覆盖目录 */
	public static Path storiesDir() {
		return FMLPaths.CONFIGDIR.get().resolve("mcanomalyarchives").resolve("stories");
	}

	/** 取一个故事（优先 config 覆盖，其次模组内置） */
	@Nullable
	public static Story get(String name) {
		Story cached = CACHE.get(name);
		long now = System.currentTimeMillis();
		if (cached != null && now - lastCheck < 1000) {
			return cached;
		}
		return load(name, false);
	}

	/** 每 tick 由服务端调用：到点就检查 config 那份有没有被改过 */
	public static void tick(long gameTime) {
		if (gameTime % CHECK_INTERVAL != 0) {
			return;
		}
		lastCheck = System.currentTimeMillis();
		Path dir = storiesDir();
		if (Files.isDirectory(dir)) {
			try (var stream = Files.list(dir)) {
				stream.filter(p -> p.getFileName().toString().endsWith(".txt"))
						.forEach(p -> load(p.getFileName().toString().replace(".txt", ""), true));
			} catch (IOException e) {
				DialogueLog.error("stories", "扫描目录失败：" + dir, e);
			}
		}
		// 没有 config 覆盖的也重取一次内置版（数据包重载后能跟上）
		for (String name : CACHE.keySet().toArray(new String[0])) {
			if (!"config".equals(SOURCE.get(name))) {
				load(name, false);
			}
		}
	}

	/** 手动重新加载（例如数据包重载后） */
	public static Story reload(String name) {
		return load(name, false);
	}

	// ===== 加载 =====

	@Nullable
	private static Story load(String name, boolean forceRecheck) {
		Path override = storiesDir().resolve(name + ".txt");
		// 1) config 覆盖
		if (Files.isRegularFile(override)) {
			try {
				long stamp = Files.getLastModifiedTime(override).toMillis();
				Long known = STAMPS.get(name);
				if (!forceRecheck && known != null && known == stamp && CACHE.containsKey(name)) {
					return CACHE.get(name);
				}
				String raw = Files.readString(override, StandardCharsets.UTF_8);
				Story story = Story.parse(raw, name + "(config)");
				if (story.isUsable()) {
					boolean changed = !CACHE.containsKey(name);
					CACHE.put(name, story);
					STAMPS.put(name, stamp);
					SOURCE.put(name, "config");
					if (changed) {
						DialogueLog.info("剧情「" + name + "」来自 config 覆盖：" + override + "（段落 " + story.labels().size()
								+ " 个）");
					}
					return story;
				}
				DialogueLog.warn(name, "config 版解析后没有可用段落，保留上一份");
				return CACHE.get(name);
			} catch (IOException e) {
				DialogueLog.error(name, "读取 config 覆盖失败：" + override, e);
			}
		}
		// 2) 模组内置
		Story builtin = loadBuiltin(name);
		if (builtin != null) {
			boolean first = !CACHE.containsKey(name);
			CACHE.put(name, builtin);
			SOURCE.put(name, "mod");
			if (first) {
				DialogueLog.info("剧情「" + name + "」来自模组内置：data/" + McanomalyarchivesMod.MODID + "/" + RESOURCE_PREFIX
						+ name + ".txt（段落 " + builtin.labels().size() + " 个）");
			}
			return builtin;
		}
		// 3) 两份都没有：保留旧缓存（如果有）
		if (!CACHE.containsKey(name)) {
			DialogueLog.error(name,
					"剧本缺失：模组内置 data/" + McanomalyarchivesMod.MODID + "/" + RESOURCE_PREFIX + name + ".txt 不存在", null);
		}
		return CACHE.get(name);
	}

	@Nullable
	private static Story loadBuiltin(String name) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return null;
		}
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID,
				RESOURCE_PREFIX + name + ".txt");
		Optional<Resource> resource = server.getResourceManager().getResource(location);
		if (resource.isEmpty()) {
			return null;
		}
		try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
			StringBuilder sb = new StringBuilder();
			char[] buffer = new char[4096];
			int read;
			while ((read = reader.read(buffer)) > 0) {
				sb.append(buffer, 0, read);
			}
			Story story = Story.parse(sb.toString(), name + "(mod)");
			if (!story.isUsable()) {
				DialogueLog.warn(name, "模组内置剧本没有可用段落");
				return null;
			}
			return story;
		} catch (IOException e) {
			DialogueLog.error(name, "读取模组内置剧本失败：" + location, e);
			return null;
		}
	}

	/** 当前剧本来自哪里（config / mod），用于日志与排查 */
	@Nullable
	public static String sourceOf(String name) {
		return SOURCE.get(name);
	}
}
