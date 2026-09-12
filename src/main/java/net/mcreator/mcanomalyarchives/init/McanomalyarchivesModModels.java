/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.mcanomalyarchives.init;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.mcanomalyarchives.client.model.*;

@EventBusSubscriber(Dist.CLIENT)
public class McanomalyarchivesModModels {
	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(Modelqu.LAYER_LOCATION, Modelqu::createBodyLayer);
		event.registerLayerDefinition(Modelstrangetreefoot.LAYER_LOCATION, Modelstrangetreefoot::createBodyLayer);
		event.registerLayerDefinition(Modelpurpledog.LAYER_LOCATION, Modelpurpledog::createBodyLayer);
		event.registerLayerDefinition(ModelStrangeCloud.LAYER_LOCATION, ModelStrangeCloud::createBodyLayer);
		event.registerLayerDefinition(Modelpurplephasefour.LAYER_LOCATION, Modelpurplephasefour::createBodyLayer);
		event.registerLayerDefinition(Modelpurplechushou.LAYER_LOCATION, Modelpurplechushou::createBodyLayer);
		event.registerLayerDefinition(Modelstrangetree.LAYER_LOCATION, Modelstrangetree::createBodyLayer);
	}
}