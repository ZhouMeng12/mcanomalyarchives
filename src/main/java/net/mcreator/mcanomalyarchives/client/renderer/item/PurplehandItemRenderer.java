package net.mcreator.mcanomalyarchives.client.renderer.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;

import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.procedures.PurplehandHuiFangTiaoJianProcedure;
import net.mcreator.mcanomalyarchives.client.model.animations.purplechushouAnimation;
import net.mcreator.mcanomalyarchives.custommodel.ModelpurplechushouGeo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.RandomSource;

import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.WeakHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

@EventBusSubscriber(value = Dist.CLIENT)
public class PurplehandItemRenderer extends BlockEntityWithoutLevelRenderer {

	private static final ResourceLocation TEXTURE = ResourceLocation.parse("mcanomalyarchives:textures/entities/purpletexture.png");

	// 延迟初始化：RegisterClientExtensionsEvent 在模型层烘焙之前分发，
	// 所以不能在构造函数中调用 bakeLayer，需要在第一次渲染时才获取模型
	private AnimatedModel model;
	private static final float ANIM_DURATION_TICKS = 15.0f;
	private static final float ANIM_SPEED = 2.0f;
	private final Map<ItemStack, Float> animStartTick = new WeakHashMap<>();
	private final Map<ItemStack, Boolean> wasSwinging = new WeakHashMap<>();
	private float currentAnimTick = 0.0f;
	private static final Map<ItemStack, Map<Integer, AnimationState>> ANIM_STATE_CACHE = new WeakHashMap<>();

	public PurplehandItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	private AnimatedModel getOrCreateModel() {
		if (this.model == null) {
			EntityModelSet modelSet = Minecraft.getInstance().getEntityModels();
			this.model = new AnimatedModel(modelSet.bakeLayer(ModelpurplechushouGeo.LAYER_LOCATION));
		}
		return this.model;
	}

	public void renderByItem(ItemStack itemstack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (isInventory(displayContext)) {
			// 1.21.1: 直接渲染 BakedModel quads，不调用 ItemRenderer.render() 避免重复变换
			BakedModel guiModel = Minecraft.getInstance().getModelManager().getModel(
				ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "item/purplehand_gui"))
			);
			poseStack.pushPose();
			guiModel.getTransforms().getTransform(ItemDisplayContext.GUI).apply(false, poseStack);
			renderFlatModel(poseStack, bufferSource, packedLight, packedOverlay, guiModel);
			poseStack.popPose();
			return;
		}
		// 非 GUI: 渲染 3D 动画模型
		AnimatedModel model = getOrCreateModel();
		updateAnimState(itemstack);
		poseStack.pushPose();
		poseStack.translate(0.5, 2, 0.5);
		poseStack.scale(1, -1, 1);
		VertexConsumer vertexConsumer = ItemRenderer.getFoilBuffer(bufferSource, model.renderType(TEXTURE), false, false);
		model.setupItemStackAnim(this, itemstack, currentAnimTick);
		model.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private void updateAnimState(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		float now = mc.level != null ? mc.level.getGameTime() : 0;
		boolean swinging = PurplehandHuiFangTiaoJianProcedure.execute(mc.player);
		boolean prev = Boolean.TRUE.equals(wasSwinging.get(stack));

		if (swinging && !prev) {
			animStartTick.put(stack, now);
			ANIM_STATE_CACHE.put(stack, IntStream.range(0, 1).boxed().collect(Collectors.toMap(i -> i, i -> new AnimationState(), (a, b) -> b)));
		}
		wasSwinging.put(stack, swinging);

		Float startTick = animStartTick.get(stack);
		float elapsed = startTick != null ? (now - startTick) : 0;
		boolean shouldAnimate = swinging || (startTick != null && elapsed < ANIM_DURATION_TICKS);

		currentAnimTick = Math.max(0, elapsed);
		getAnimationState(stack).get(0).animateWhen(shouldAnimate, (int) currentAnimTick);
	}

	private Map<Integer, AnimationState> getAnimationState(ItemStack stack) {
		return ANIM_STATE_CACHE.computeIfAbsent(stack, s -> IntStream.range(0, 1).boxed().collect(Collectors.toMap(i -> i, i -> new AnimationState(), (a, b) -> b)));
	}

	private static boolean isInventory(ItemDisplayContext type) {
		return type == ItemDisplayContext.GUI || type == ItemDisplayContext.FIXED;
	}

	/** 直接渲染 BakedModel 的 quads，不经过 ItemRenderer（避免重复变换） */
	static void renderFlatModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, BakedModel model) {
		VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
		for (BakedQuad quad : model.getQuads(null, null, RandomSource.create())) {
			consumer.putBulkData(poseStack.last(), quad, 1.0f, 1.0f, 1.0f, 1.0f, packedLight, packedOverlay);
		}
	}

	private static final class AnimatedModel extends ModelpurplechushouGeo {
		private final AnimationDefinition keyframeAnimation0;

		public AnimatedModel(ModelPart root) {
			super(root);
			this.keyframeAnimation0 = purplechushouAnimation.sway2;
		}

		public void setupItemStackAnim(PurplehandItemRenderer renderer, ItemStack itemstack, float ageInTicks) {
			this.root().getAllParts().forEach(ModelPart::resetPose);
			AnimationState animState = renderer.getAnimationState(itemstack).get(0);
			this.animate(animState, this.keyframeAnimation0, ageInTicks, ANIM_SPEED);
		}
	}

	@SubscribeEvent
	public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
		event.registerItem(new IClientItemExtensions() {
			private final PurplehandItemRenderer renderer = new PurplehandItemRenderer();

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return renderer;
			}
		}, McanomalyarchivesModItems.PURPLEHAND.get());
	}
}
