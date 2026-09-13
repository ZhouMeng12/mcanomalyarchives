package net.mcreator.mcanomalyarchives.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 游戏体验 / 舒适度配置（客户端）。
 *
 * 【工程层归属】本类位于 config 包（非 MCreator 生成区），只负责定义配置与读写，
 * 实际生效点在 {@code client.TreeQuakeClientHandler}（震动）与 {@code client.BlinkClientHandler}（眨眼黑屏）。
 *
 * 为什么需要它：本模组的异常演出里有大量屏幕震动（怪树地震、伪云掠过、粉羊陨石下落的持续震颤
 * 与落地大震），对晕动症玩家很不友好；而震动强度属于"每个玩家自己的偏好"，
 * 放在服务端 gamerule 里没法按人设置，所以用客户端配置。
 *
 * 配置文件：{@code config/mcanomalyarchives-client.toml}
 * 游戏内：模组列表 → 本模组 → Config（NeoForge 自动生成界面）
 *
 * 读取全部走 {@link #shakeScale()} / {@link #blinkScale()}：配置尚未加载时回退默认值，
 * 避免在极端时序下抛异常把客户端搞崩。
 */
public final class ComfortConfig {

	public static final ModConfigSpec SPEC;

	/** 屏幕震动强度倍率：0 = 完全不震，1 = 原强度，2 = 双倍 */
	public static final ModConfigSpec.DoubleValue SCREEN_SHAKE_INTENSITY;
	/** 眨眼黑屏浓度倍率：0 = 不黑屏 */
	public static final ModConfigSpec.DoubleValue BLINK_DARKNESS;
	/** 陨石在无障碍物时是否破坏地形（默认开；关掉只伤人不炸地形） */
	public static final ModConfigSpec.BooleanValue METEOR_TERRAIN_DAMAGE;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		builder.comment("游戏体验 / 舒适度设置（客户端本地生效，不影响服务器）").push("comfort");

		SCREEN_SHAKE_INTENSITY = builder
				.comment("屏幕震动强度倍率。",
						"0 = 完全关闭震动；1.0 = 原强度；2.0 = 双倍。",
						"影响：怪树地震、伪云掠过、粉羊陨石下落时的持续震颤与落地大震。",
						"晕动症玩家建议 0 ~ 0.3。")
				.defineInRange("screenShakeIntensity", 1.0D, 0.0D, 2.0D);

		BLINK_DARKNESS = builder
				.comment("粉羊凝视触发眨眼演出时的黑屏浓度倍率。",
						"0 = 不黑屏（只保留其它反馈）；1.0 = 原强度（最多压暗到 85%）。")
				.defineInRange("blinkDarkness", 1.0D, 0.0D, 1.0D);

		METEOR_TERRAIN_DAMAGE = builder
				.comment("粉羊最高档灾厄的陨石是否破坏地形。",
						"true = 砸出陨石坑（默认）；false = 只保留爆炸伤害与演出，不炸方块。",
						"住在精心搭的建筑附近时建议关掉。")
				.define("meteorTerrainDamage", true);

		builder.pop();
		SPEC = builder.build();
	}

	private ComfortConfig() {
	}

	/** 在模组构造期注册（必须是构造期，NeoForge 之后不再接受注册） */
	public static void register(ModContainer container) {
		container.registerConfig(ModConfig.Type.CLIENT, SPEC);
		if (FMLEnvironment.dist.isClient()) {
			// 让模组列表里出现 Config 按钮（ConfigurationScreen 是客户端专属类，必须挡在 dist 判断里）
			net.mcreator.mcanomalyarchives.client.ComfortConfigScreen.register(container);
		}
	}

	/** 震动倍率；配置未加载时回退 1.0 */
	public static float shakeScale() {
		try {
			if (!SPEC.isLoaded())
				return 1.0F;
			return SCREEN_SHAKE_INTENSITY.get().floatValue();
		} catch (Exception e) {
			return 1.0F;
		}
	}

	/** 眨眼黑屏倍率；配置未加载时回退 1.0 */
	public static float blinkScale() {
		try {
			if (!SPEC.isLoaded())
				return 1.0F;
			return BLINK_DARKNESS.get().floatValue();
		} catch (Exception e) {
			return 1.0F;
		}
	}

	/** 陨石是否破坏地形；配置未加载时回退 true */
	public static boolean meteorTerrainDamage() {
		try {
			if (!SPEC.isLoaded())
				return true;
			return METEOR_TERRAIN_DAMAGE.get();
		} catch (Exception e) {
			return true;
		}
	}
}
