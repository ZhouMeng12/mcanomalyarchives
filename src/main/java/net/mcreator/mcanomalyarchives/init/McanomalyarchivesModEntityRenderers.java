/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.mcanomalyarchives.client.renderer.*;

@EventBusSubscriber(Dist.CLIENT)
public class McanomalyarchivesModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(McanomalyarchivesModEntities.STANGE_CLOUD.get(), StangeCloudRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.ANBULA.get(), AnbulaRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.PRISONER.get(), PrisonerRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.SITTING_ENITY.get(), SittingEnityRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.SVAN.get(), SvanRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.QU.get(), QuRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.PURPLE_MONSTER.get(), PurpleMonsterRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.PURPLE_DOG.get(), PurpleDogRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.PURPLE_ARROW.get(), PurpleArrowRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.YIFULIN.get(), YifulinRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.DAVID.get(), DavidRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.POTTER.get(), PotterRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.STRANGE_TREE.get(), StrangeTreeRenderer::new);
		event.registerEntityRenderer(McanomalyarchivesModEntities.PINK_SHEEP.get(), PinkSheepRenderer::new);
	}
}