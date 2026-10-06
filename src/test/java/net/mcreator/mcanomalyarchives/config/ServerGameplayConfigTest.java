package net.mcreator.mcanomalyarchives.config;

import static org.junit.jupiter.api.Assertions.*;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerGameplayConfigTest {
	private static IConfigSpec.ILoadedConfig loaded(CommentedConfig config) {
		try {
			// NeoForge 的 ILoadedConfig 是 sealed；直接使用真实加载容器，不伪造实现。
			var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
					.getDeclaredConstructor(CommentedConfig.class, java.nio.file.Path.class, ModConfig.class);
			constructor.setAccessible(true);
			return (IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("NeoForge loaded config contract changed", failure);
		}
	}

	@BeforeEach
	@AfterEach
	void unload() {
		ServerGameplayConfig.SPEC.acceptConfig(null);
		ComfortConfig.SPEC.acceptConfig(null);
	}

	private void load(boolean meteor, boolean black) {
		CommentedConfig values = CommentedConfig.inMemory();
		values.set("world.meteorTerrainDamage", meteor);
		values.set("world.blackCloudTerrainDamage", black);
		ServerGameplayConfig.SPEC.correct(values); // 与真实加载流程一样先补 schema 注释/缺省项。
		ServerGameplayConfig.SPEC.acceptConfig(loaded(values));
	}

	@Test
	void unloadedConfigCannotAccidentallyEnableDestructiveEffects() {
		assertFalse(ServerGameplayConfig.meteorTerrainDamage());
		assertFalse(ServerGameplayConfig.blackCloudTerrainDamage());
	}

	@Test
	void normalLoadedDefaultsStillEnableOriginalGameplay() {
		CommentedConfig values = CommentedConfig.inMemory();
		ServerGameplayConfig.SPEC.correct(values);
		ServerGameplayConfig.SPEC.acceptConfig(loaded(values));
		assertTrue(ServerGameplayConfig.meteorTerrainDamage());
		assertTrue(ServerGameplayConfig.blackCloudTerrainDamage());
	}

	@Test
	void independentServerSwitchesDoNotForceEachOther() {
		load(true, false);
		assertTrue(ServerGameplayConfig.meteorTerrainDamage());
		assertFalse(ServerGameplayConfig.blackCloudTerrainDamage());
		ServerGameplayConfig.METEOR_TERRAIN_DAMAGE.set(false);
		ServerGameplayConfig.BLACK_CLOUD_TERRAIN_DAMAGE.set(true);
		assertFalse(ServerGameplayConfig.meteorTerrainDamage());
		assertTrue(ServerGameplayConfig.blackCloudTerrainDamage());
	}

	@Test
	void clientVisualPreferencesCannotOverrideLoadedServerRules() {
		load(false, false);
		CommentedConfig client = CommentedConfig.inMemory();
		client.set("comfort.screenShakeIntensity", 0.0);
		client.set("comfort.blinkDarkness", 0.0);
		ComfortConfig.SPEC.correct(client);
		ComfortConfig.SPEC.acceptConfig(loaded(client));
		assertEquals(0.0f, ComfortConfig.shakeScale());
		assertEquals(0.0f, ComfortConfig.blinkScale());
		assertFalse(ServerGameplayConfig.meteorTerrainDamage());
		assertFalse(ServerGameplayConfig.blackCloudTerrainDamage());
	}

	@Test
	void reloadAndUnloadDiscardPreviousWorldRules() {
		load(false, true);
		load(true, false);
		assertTrue(ServerGameplayConfig.meteorTerrainDamage());
		assertFalse(ServerGameplayConfig.blackCloudTerrainDamage());
		ServerGameplayConfig.SPEC.acceptConfig(null);
		assertFalse(ServerGameplayConfig.meteorTerrainDamage());
		assertFalse(ServerGameplayConfig.blackCloudTerrainDamage());
	}

	@Test
	void configSchemasSeparateVisualValuesFromWorldValues() {
		assertTrue(ComfortConfig.SPEC.getSpec().contains("comfort.screenShakeIntensity"));
		assertTrue(ComfortConfig.SPEC.getSpec().contains("comfort.blinkDarkness"));
		assertFalse(ComfortConfig.SPEC.getSpec().contains("comfort.meteorTerrainDamage"));
		assertFalse(ComfortConfig.SPEC.getSpec().contains("comfort.blackCloudTerrainDamage"));
		assertFalse(ServerGameplayConfig.SPEC.getSpec().contains("world.screenShakeIntensity"));
		assertTrue(ServerGameplayConfig.SPEC.getSpec().contains("world.meteorTerrainDamage"));
	}
}
