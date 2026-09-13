package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * UO-012 幸运粉羊 · 成就（进度线）。
 *
 * 【工程层归属】本类位于 anomaly 包（非 MCreator 生成区）。
 * 成就的"定义"在 MCreator 元素里（elements/PinkSheep*Sighting|Blink|Calamity.mod.json → 生成
 * resources/.../advancement/*.json），触发器用的是 MCreator 的 custom_trigger，
 * 生成的 advancement 判定条件是 {@code minecraft:impossible} —— 也就是说必须由代码显式授予，
 * 授予点就在本类，调用方是三个 handler。
 *
 * 进度线（与 MCreator 里的 parent 链一致）：
 * <pre>
 *   欢迎来到诡异见闻录 (welcome)
 *     └─ 幸运粉羊 sighting  目击粉羊并收到祝福（tasks）
 *          └─ 眨眼之间 blink  凝视到它眨眼消失（challenge）
 *               └─ 陨石问候 calamity  惹出最高档灾厄（challenge）
 * </pre>
 * 判定条件名固定为 {@code <registry_name>_0}（MCreator 自定义触发器生成的唯一判定项）。
 */
public final class PinkSheepAdvancements {

	private static final String SIGHTING = "pink_sheep_sighting";
	private static final String BLINK = "pink_sheep_blink";
	private static final String CALAMITY = "pink_sheep_calamity";

	private PinkSheepAdvancements() {
	}

	/** 目击粉羊并收到它的祝福（由 PinkSheepLuckyHandler 在发放幸运时调用） */
	public static void grantSighting(ServerPlayer player) {
		award(player, SIGHTING);
	}

	/** 凝视到粉羊眨眼消失（由 PinkSheepLookHandler 在眨眼转移时调用） */
	public static void grantBlink(ServerPlayer player) {
		award(player, BLINK);
	}

	/** 惹出最高档灾厄（由 PinkSheepCalamityHandler 在 Lv3 触发时调用） */
	public static void grantCalamity(ServerPlayer player) {
		award(player, CALAMITY);
	}

	/**
	 * 授予成就。找不到该成就（例如资源被 MCreator 重生成时漏掉）就静默跳过 ——
	 * 成就只是锦上添花，绝不能因为它把玩法主流程搞崩。
	 */
	private static void award(ServerPlayer player, String registryName) {
		if (player == null || !player.isAlive())
			return;
		MinecraftServer server = player.serverLevel().getServer();
		if (server == null)
			return;
		AdvancementHolder holder = server.getAdvancements()
				.get(ResourceLocation.fromNamespaceAndPath(McanomalyarchivesMod.MODID, registryName));
		if (holder == null)
			return;
		if (player.getAdvancements().getOrStartProgress(holder).isDone())
			return;
		player.getAdvancements().award(holder, registryName + "_0");
	}
}
