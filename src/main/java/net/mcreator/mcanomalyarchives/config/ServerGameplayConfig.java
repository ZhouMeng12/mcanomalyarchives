package net.mcreator.mcanomalyarchives.config;

import java.io.IOException;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务端权威的世界规则。21.1.190 使用 config 下的默认文件，世界 serverconfig 可覆盖；
 * 已连接客户端收到 SERVER 配置同步，但不能用自己的 CLIENT 偏好修改服务器规则。
 */
public final class ServerGameplayConfig {
	public static final String FILE_NAME = "mcanomalyarchives-server.toml";
	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.BooleanValue METEOR_TERRAIN_DAMAGE;
	public static final ModConfigSpec.BooleanValue BLACK_CLOUD_TERRAIN_DAMAGE;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		builder.comment("服务端世界规则；由服务器/单人世界控制，不属于客户端视觉偏好。").push("world");
		METEOR_TERRAIN_DAMAGE = builder
				.comment("粉羊最高档灾厄的陨石是否破坏方块。",
						"false 仅关闭地形破坏，保留伤害与演出。")
				.define("meteorTerrainDamage", true);
		BLACK_CLOUD_TERRAIN_DAMAGE = builder
				.comment("黑吸伪云是否吞掉方块；false 只消散。",
						"同时遵守 mobGriefing，两者任一关闭都不吞。")
				.define("blackCloudTerrainDamage", true);
		builder.pop();
		SPEC = builder.build();
	}

	private ServerGameplayConfig() { }

	/** 无参构造期接入，避免依赖 MCreator 会重生成的构造器参数。 */
	public static void register() {
		ModList.get().getModContainerById(McanomalyarchivesMod.MODID).ifPresentOrElse(
				ServerGameplayConfig::registerTo,
				() -> McanomalyarchivesMod.LOGGER.warn("Server gameplay configuration was not registered"));
	}

	private static void registerTo(ModContainer container) {
		try {
			LegacyGameplayConfigMigration.Result result = LegacyGameplayConfigMigration.migrate(
					FMLPaths.CONFIGDIR.get(), FMLEnvironment.dist.isClient());
			if (result == LegacyGameplayConfigMigration.Result.MIGRATED) {
				McanomalyarchivesMod.LOGGER.info("Migrated legacy local terrain preferences into {} "
						+ "(world serverconfig overrides still take precedence; original client file backed up)", FILE_NAME);
			}
		} catch (IOException | IllegalArgumentException failure) {
			McanomalyarchivesMod.LOGGER.warn("Legacy terrain settings were not migrated; "
					+ "original files preserved, configure {} explicitly", FILE_NAME, failure);
		}
		container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE_NAME);
	}

	/** 尚未加载服务端规则时不破坏地形；正常加载后的默认值仍为 true。 */
	public static boolean meteorTerrainDamage() {
		return SPEC.isLoaded() && METEOR_TERRAIN_DAMAGE.get();
	}

	public static boolean blackCloudTerrainDamage() {
		return SPEC.isLoaded() && BLACK_CLOUD_TERRAIN_DAMAGE.get();
	}
}
