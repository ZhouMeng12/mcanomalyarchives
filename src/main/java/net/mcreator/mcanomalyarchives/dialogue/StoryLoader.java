package net.mcreator.mcanomalyarchives.dialogue;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 剧情脚本入口：config 覆盖优先于数据包/模组内置版。
 * 每 200 tick 检查修改，解析失败保留最后可用脚本；服务器会话结束时清空缓存。
 * 文件与缓存逻辑在 StoryRepository，NeoForge 生命周期与资源读取留在这里。
 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class StoryLoader {
    private static final int CHECK_INTERVAL = 200;
    private static final String RESOURCE_PREFIX = "story/";
    private static final StoryRepository REPOSITORY =
            new StoryRepository(StoryLoader::storiesDir, StoryLoader::loadBuiltin);

    private StoryLoader() { }

    public static Path storiesDir() {
        return FMLPaths.CONFIGDIR.get().resolve("mcanomalyarchives").resolve("stories");
    }

    @Nullable
    public static Story get(String name) {
        ensureEditableCopy(name);
        return REPOSITORY.get(name);
    }

    public static void tick(long gameTime) {
        if (gameTime % CHECK_INTERVAL == 0) REPOSITORY.refresh();
    }

    @Nullable
    public static Story reload(String name) {
        return REPOSITORY.reload(name);
    }

    @Nullable
    public static String sourceOf(String name) {
        return REPOSITORY.sourceOf(name);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        REPOSITORY.clear();
        ensureEditableCopy(event.getServer(), "svan_codex");
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REPOSITORY.clear();
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

    /**
     * Materialize the packaged story as a config override on first run. MCreator
     * may regenerate resource files; this editable copy survives regeneration and
     * is also watched by StoryRepository for hot reloads.
     */
    private static void ensureEditableCopy(String name) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) ensureEditableCopy(server, name);
    }

    private static void ensureEditableCopy(MinecraftServer server, String name) {
        if (!"svan_codex".equals(name)) return;
        Path target = storiesDir().resolve(name + ".txt");
        if (Files.isRegularFile(target)) return;
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID,
                RESOURCE_PREFIX + name + ".txt");
        Optional<Resource> resource = server.getResourceManager().getResource(location);
        if (resource.isEmpty()) return;
        try {
            Files.createDirectories(target.getParent());
            try (var input = resource.get().open()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            DialogueLog.info("已创建可编辑剧情副本：" + target);
        } catch (IOException e) {
            DialogueLog.error(name, "创建可编辑剧情副本失败：" + target, e);
        }
    }

}
