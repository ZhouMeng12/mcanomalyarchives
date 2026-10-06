package net.mcreator.mcanomalyarchives.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;

/**
 * 首次本地升级迁移。独立文件策略使真实迁移可在临时目录测试；
 * 不改旧文件、不扫描/写世界目录、不从专用服务器读取客户端偏好。
 */
public final class LegacyGameplayConfigMigration {
	public static final String CLIENT_FILE = "mcanomalyarchives-client.toml";
	public static final String BACKUP_FILE = CLIENT_FILE + ".pre-server-split.bak";
	public enum Result { NOT_LOCAL_CLIENT, SERVER_SETTINGS_EXIST, NO_LEGACY_SETTINGS, MIGRATED }

	private LegacyGameplayConfigMigration() { }

	public static Result migrate(Path configDirectory, boolean localClient) throws IOException {
		if (!localClient) return Result.NOT_LOCAL_CLIENT;
		Path target = configDirectory.resolve(ServerGameplayConfig.FILE_NAME);
		// 即使目标损坏或不完整，也不能用客户端文件覆盖服务器管理员的决定。
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return Result.SERVER_SETTINGS_EXIST;
		Path source = configDirectory.resolve(CLIENT_FILE);
		if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return Result.NO_LEGACY_SETTINGS;
		CommentedConfig legacy;
		try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
			legacy = new TomlParser().parse(reader);
		} catch (com.electronwill.nightconfig.core.io.ParsingException failure) {
			backup(source, configDirectory.resolve(BACKUP_FILE));
			throw new IllegalArgumentException("Legacy client TOML is invalid", failure);
		}
		if (!legacy.contains("comfort.meteorTerrainDamage") && !legacy.contains("comfort.blackCloudTerrainDamage")) {
			return Result.NO_LEGACY_SETTINGS;
		}
		// 先保存原始字节（包括无效的旧值），避免后续 CLIENT schema 修正清掉迁移前记录。
		backup(source, configDirectory.resolve(BACKUP_FILE));
		boolean meteor = readBoolean(legacy, "comfort.meteorTerrainDamage");
		boolean blackCloud = readBoolean(legacy, "comfort.blackCloudTerrainDamage");
		String content = "# Migrated from legacy local client terrain settings; world serverconfig can override.\n"
				+ "[world]\nmeteorTerrainDamage = " + meteor + "\nblackCloudTerrainDamage = " + blackCloud + "\n";
		try {
			Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		} catch (FileAlreadyExistsException race) {
			return Result.SERVER_SETTINGS_EXIST;
		}
		return Result.MIGRATED;
	}

	private static void backup(Path source, Path backup) throws IOException {
		if (!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
			try {
				Files.copy(source, backup);
			} catch (FileAlreadyExistsException race) {
				// 另一个启动进程已创建备份，按不覆盖原则继续。
			}
		}
	}

	private static boolean readBoolean(CommentedConfig config, String key) {
		if (!config.contains(key)) return true; // 早于黑云配置的旧文件仍有原默认值。
		Object value = config.get(key);
		if (!(value instanceof Boolean booleanValue)) throw new IllegalArgumentException("Invalid legacy boolean: " + key);
		return booleanValue;
	}
}
