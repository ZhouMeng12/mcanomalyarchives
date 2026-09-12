package net.mcreator.mcanomalyarchives.client.renderer.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import com.mojang.blaze3d.vertex.PoseStack;

@EventBusSubscriber(value = Dist.CLIENT)
public class DetecterItemRenderer extends BlockEntityWithoutLevelRenderer {

	public DetecterItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	private static boolean isInventory(ItemDisplayContext type) {
		return type == ItemDisplayContext.GUI || type == ItemDisplayContext.FIXED || type == ItemDisplayContext.GROUND;
	}

	private static BakedModel getBakedModel(String itemModelName) {
		// 1.21.1 NeoForge: standalone variant 用于 RegisterAdditional 注册的附加模型
		return Minecraft.getInstance().getModelManager().getModel(
			ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "item/" + itemModelName))
		);
	}

	@Override
	public void renderByItem(ItemStack itemstack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		BakedModel bakedModel;
		if (displayContext == ItemDisplayContext.HEAD) {
			// 戴在头上：用 detecter_head（带 head display 变换），否则会落在下巴位置
			bakedModel = getBakedModel("detecter_head");
			poseStack.pushPose();
			bakedModel.getTransforms().getTransform(ItemDisplayContext.HEAD).apply(false, poseStack);
			PurplehandItemRenderer.renderFlatModel(poseStack, bufferSource, packedLight, packedOverlay, bakedModel);
			poseStack.popPose();
			return;
		}
		if (isInventory(displayContext)) {
			// GUI/Fixed/Ground: 2D 贴图，直接渲染 quads 避免重复变换
			bakedModel = getBakedModel("detecter_gui");
			poseStack.pushPose();
			bakedModel.getTransforms().getTransform(ItemDisplayContext.GUI).apply(false, poseStack);
			PurplehandItemRenderer.renderFlatModel(poseStack, bufferSource, packedLight, packedOverlay, bakedModel);
			poseStack.popPose();
		} else {
			// 世界/手持: 3D 模型
			bakedModel = getBakedModel("detecter_world");
			poseStack.pushPose();
			bakedModel.getTransforms().getTransform(displayContext).apply(false, poseStack);
			PurplehandItemRenderer.renderFlatModel(poseStack, bufferSource, packedLight, packedOverlay, bakedModel);
			poseStack.popPose();
		}
	}

	@SubscribeEvent
	public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
		event.registerItem(new IClientItemExtensions() {
			private final DetecterItemRenderer renderer = new DetecterItemRenderer();

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		}, McanomalyarchivesModItems.DETECTER.get());
	}
}
