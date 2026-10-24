package net.mcreator.mcanomalyarchives.client;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.config.ComfortConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** 单次、渐隐的屏幕边缘反馈，不黑屏、不控制视角，也不显示数值或文字。 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID, value = Dist.CLIENT)
public final class FortunePulseClient {
	private static ClientLevel world;
	private static long started;
	private static int severity;
	public static void play(int value) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || value < 0 || value > 3) return;
		// 同一时刻既获祝福又触发灾厄时，较严重的反馈不被后到的好运覆盖。
		if (world == mc.level && mc.level.getGameTime() - started < 40 && severity > value) return;
		world = mc.level;
		started = world.getGameTime();
		severity = value;
	}
	@SubscribeEvent
	public static void render(RenderGuiEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (world == null || mc.level != world || mc.player == null || !mc.player.isAlive()
				|| mc.options.hideGui || BlinkClientHandler.isBlinking()) return;
		float elapsed = world.getGameTime() - started;
		if (elapsed >= 40 || elapsed < 0) { world = null; return; }
		float envelope = Math.min(1f, elapsed / 4f) * (1f - elapsed / 40f);
		int alpha = Math.round((severity == 0 ? 48 : 48 + severity * 8) * envelope * ComfortConfig.blinkScale());
		int color = (alpha << 24) | (severity == 0 ? 0xDFB84B : 0x320D25);
		var gui = event.getGuiGraphics();
		int w = gui.guiWidth(), h = gui.guiHeight(), band = Math.max(12, h / 6);
		gui.fillGradient(0, 0, w, band, color, color & 0xFFFFFF);
		gui.fillGradient(0, h - band, w, h, color & 0xFFFFFF, color);
	}
	@SubscribeEvent
	public static void unload(LevelEvent.Unload event) {
		if (event.getLevel() == world) world = null;
	}
}
