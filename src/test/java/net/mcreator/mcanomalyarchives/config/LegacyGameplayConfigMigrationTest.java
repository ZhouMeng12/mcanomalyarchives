package net.mcreator.mcanomalyarchives.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.mcreator.mcanomalyarchives.config.LegacyGameplayConfigMigration.Result;

class LegacyGameplayConfigMigrationTest {
	@TempDir Path config;

	private Path client() { return config.resolve(LegacyGameplayConfigMigration.CLIENT_FILE); }
	private Path target() { return config.resolve(ServerGameplayConfig.FILE_NAME); }
	private Path backup() { return config.resolve(LegacyGameplayConfigMigration.BACKUP_FILE); }
	private CommentedConfig serverValues() throws IOException {
		return new TomlParser().parse(new StringReader(Files.readString(target())));
	}

	@Test
	void localLegacySettingsMigrateWithExactBackupWithoutChangingOriginal() throws IOException {
		String original = "# 旧设置\n[comfort]\nmeteorTerrainDamage = false\nblackCloudTerrainDamage = true\nscreenShakeIntensity = 0.2\n";
		Files.writeString(client(), original, StandardCharsets.UTF_8);
		assertEquals(Result.MIGRATED, LegacyGameplayConfigMigration.migrate(config, true));
		assertEquals(original, Files.readString(client()));
		assertArrayEquals(Files.readAllBytes(client()), Files.readAllBytes(backup()));
		assertEquals(false, serverValues().get("world.meteorTerrainDamage"));
		assertEquals(true, serverValues().get("world.blackCloudTerrainDamage"));
		assertFalse(serverValues().contains("world.screenShakeIntensity"));
	}

	@Test
	void dedicatedServerNeverReadsOrImportsLegacyClientFile() throws IOException {
		Files.writeString(client(), "not valid TOML\n");
		assertEquals(Result.NOT_LOCAL_CLIENT, LegacyGameplayConfigMigration.migrate(config, false));
		assertFalse(Files.exists(target()));
		assertFalse(Files.exists(backup()));
	}

	@Test
	void existingServerFileAlwaysWinsEvenIfMalformed() throws IOException {
		Files.writeString(client(), "[comfort]\nmeteorTerrainDamage = false\n");
		Files.writeString(target(), "administrator file: do not overwrite\n");
		assertEquals(Result.SERVER_SETTINGS_EXIST, LegacyGameplayConfigMigration.migrate(config, true));
		assertEquals("administrator file: do not overwrite\n", Files.readString(target()));
		assertFalse(Files.exists(backup()));
	}

	@Test
	void migrationIsIdempotentAndDoesNotReapplyChangedClientPreferences() throws IOException {
		Files.writeString(client(), "[comfort]\nmeteorTerrainDamage = false\n");
		assertEquals(Result.MIGRATED, LegacyGameplayConfigMigration.migrate(config, true));
		String server = Files.readString(target()), previousBackup = Files.readString(backup());
		Files.writeString(client(), "[comfort]\nmeteorTerrainDamage = true\n");
		assertEquals(Result.SERVER_SETTINGS_EXIST, LegacyGameplayConfigMigration.migrate(config, true));
		assertEquals(server, Files.readString(target()));
		assertEquals(previousBackup, Files.readString(backup()));
	}

	@Test
	void missingLegacySettingRetainsItsOriginalDefault() throws IOException {
		Files.writeString(client(), "[comfort]\nmeteorTerrainDamage = false\n");
		assertEquals(Result.MIGRATED, LegacyGameplayConfigMigration.migrate(config, true));
		assertEquals(false, serverValues().get("world.meteorTerrainDamage"));
		assertEquals(true, serverValues().get("world.blackCloudTerrainDamage"));
	}

	@Test
	void visualOnlyClientConfigDoesNotSeedServerSettings() throws IOException {
		Files.writeString(client(), "[comfort]\nscreenShakeIntensity = 0.3\nblinkDarkness = 0.4\n");
		assertEquals(Result.NO_LEGACY_SETTINGS, LegacyGameplayConfigMigration.migrate(config, true));
		assertFalse(Files.exists(target()));
		assertFalse(Files.exists(backup()));
	}

	@Test
	void missingClientConfigRequiresNoMigration() throws IOException {
		assertEquals(Result.NO_LEGACY_SETTINGS, LegacyGameplayConfigMigration.migrate(config, true));
		assertFalse(Files.exists(target()));
	}

	@Test
	void invalidBooleanDoesNotCreateMigratedServerFileAndKeepsBackup() throws IOException {
		String original = "[comfort]\nmeteorTerrainDamage = \"false\"\n";
		Files.writeString(client(), original);
		assertThrows(IllegalArgumentException.class, () -> LegacyGameplayConfigMigration.migrate(config, true));
		assertEquals(original, Files.readString(client()));
		assertFalse(Files.exists(target()));
		assertEquals(original, Files.readString(backup()));
	}

	@Test
	void invalidTomlRemainsUntouchedAndReportsFailure() throws IOException {
		String original = "[comfort\nmeteorTerrainDamage = false\n";
		Files.writeString(client(), original);
		assertThrows(IllegalArgumentException.class, () -> LegacyGameplayConfigMigration.migrate(config, true));
		assertEquals(original, Files.readString(client()));
		assertEquals(original, Files.readString(backup()));
		assertFalse(Files.exists(target()));
	}

	@Test
	void worldOverridesAndExistingMigrationBackupAreNotChanged() throws IOException {
		Path world = config.resolve("fixture-world/serverconfig/" + ServerGameplayConfig.FILE_NAME);
		Files.createDirectories(world.getParent());
		String override = "[world]\nmeteorTerrainDamage = true\nblackCloudTerrainDamage = false\n";
		Files.writeString(world, override);
		Files.writeString(backup(), "previous backup\n");
		Files.writeString(client(), "[comfort]\nmeteorTerrainDamage = false\nblackCloudTerrainDamage = true\n");
		assertEquals(Result.MIGRATED, LegacyGameplayConfigMigration.migrate(config, true));
		assertEquals(override, Files.readString(world));
		assertEquals("previous backup\n", Files.readString(backup()));
		assertEquals(false, serverValues().get("world.meteorTerrainDamage"));
	}
}
