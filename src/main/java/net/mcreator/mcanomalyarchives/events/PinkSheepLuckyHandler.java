package net.mcreator.mcanomalyarchives.events;

import net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepLuckyChains;

/** First confirmed witness starts a delayed, contextual lucky encounter. */
public final class PinkSheepLuckyHandler {
	private PinkSheepLuckyHandler() { }
	public static void init() {
		PinkSheepLookHandler.encounterCallback = (player, sheep) -> PinkSheepLuckyChains.schedule(player);
	}
}
