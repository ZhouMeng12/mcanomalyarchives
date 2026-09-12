package net.mcreator.mcanomalyarchives.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;

import net.mcreator.mcanomalyarchives.entity.StrangeTreeEntity;
import net.mcreator.mcanomalyarchives.client.model.Modelstrangetree;
import net.mcreator.mcanomalyarchives.client.model.Modelstrangetreefoot;
import net.mcreator.mcanomalyarchives.client.model.animations.strangetreeAnimation;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * 怪树渲染器：站立=脚模型；钻地中=整树模型+zuandi 动画；钻出中=整树模型+zuanchu 动画；
 * 夹击中=整树模型+jiaren 动画（自包含实现，不依赖 Modelstrangetree 是否 HierarchicalModel，
 * MCreator 重新生成模型类也不受影响）；已钻地=不渲染。
 */
public class StrangeTreeRenderer extends MobRenderer<StrangeTreeEntity, EntityModel<StrangeTreeEntity>> {
	private final ResourceLocation entityTexture = ResourceLocation.parse("mcanomalyarchives:textures/entities/strangetree.png");
	private final Modelstrangetreefoot<StrangeTreeEntity> footModel;
	private final AnimatedTreeModel treeModel;

	/** 整树模型的垂直偏移（格）：偏高减小、偏低增大 */
	private static final double TREE_MODEL_Y_OFFSET = 0;

	public StrangeTreeRenderer(EntityRendererProvider.Context context) {
		super(context, new Modelstrangetreefoot<StrangeTreeEntity>(context.bakeLayer(Modelstrangetreefoot.LAYER_LOCATION)), 0f);
		this.footModel = new Modelstrangetreefoot<StrangeTreeEntity>(context.bakeLayer(Modelstrangetreefoot.LAYER_LOCATION));
		this.treeModel = new AnimatedTreeModel(context.bakeLayer(Modelstrangetree.LAYER_LOCATION));
	}

	@Override
	public void render(StrangeTreeEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		int state = entity.getBurrowState();
		if (state == 2) {
			// 已钻地（地下）：实体始终存在且可见，渲染整树模型（配合玩家自行加的发光
			// 描边可穿透方块观察地底移动）；防御性保留 invisible 跳过（正常情况下不 invisible）
			if (entity.isInvisible()) return;
			this.model = this.treeModel;
			poseStack.pushPose();
			poseStack.translate(0.0, TREE_MODEL_Y_OFFSET, 0.0);
			super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			poseStack.popPose();
			return;
		}
		if (state == 1 || state == 3 || state == 4) {
			// 整树模型垂直偏移（格）：按需微调（偏高减小、偏低增大）
			this.model = this.treeModel;
			poseStack.pushPose();
			poseStack.translate(0.0, TREE_MODEL_Y_OFFSET, 0.0);
			super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			poseStack.popPose();
			return;
		}
		this.model = this.footModel;
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	// 树身大部分在方块内/地下：方块光照拉满，避免渲染成黑色看不见
	@Override
	protected int getBlockLightLevel(StrangeTreeEntity entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}

	@Override
	public ResourceLocation getTextureLocation(StrangeTreeEntity entity) {
		return entityTexture;
	}

	private static final class AnimatedTreeModel extends Modelstrangetree<StrangeTreeEntity> {
		public AnimatedTreeModel(ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(StrangeTreeEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
			// 自包含播放动画：重置骨骼 → 按关键帧插值
			this.whole.getAllParts().forEach(ModelPart::resetPose);
			int state = entity.getBurrowState();
			if (state == 1) {
				playAnim(entity, this.whole, strangetreeAnimation.zuandi, ageInTicks);
			} else if (state == 3) {
				playAnim(entity, this.whole, strangetreeAnimation.zuanchu, ageInTicks);
			} else if (state == 4) {
				playAnim(entity, this.whole, strangetreeAnimation.jiaren, ageInTicks);
			}
			super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		}
	}

	private static final Vector3f CACHE = new Vector3f();

	/** 手动播放动画（1.21.1 API：boneAnimations() = 骨骼名 → 通道列表） */
	private static void playAnim(StrangeTreeEntity entity, ModelPart root, AnimationDefinition def, float ageInTicks) {
		if (entity.burrowStartAge < 0) return;
		// 直接用"当前时刻 - 动画开始时刻"算动画时间（秒），不依赖 AnimationState 累计（避免首次跳帧）
		float f = (ageInTicks - entity.burrowStartAge) / 20.0F;

		Map<String, ModelPart> parts = resolveBones(root);

		for (java.util.Map.Entry<String, java.util.List<AnimationChannel>> e : def.boneAnimations().entrySet()) {
			ModelPart part = parts.get(e.getKey());
			if (part == null) continue;
			for (AnimationChannel channel : e.getValue()) {
				Keyframe[] frames = channel.keyframes();
				// 找 f 所在的 keyframe 区间
				int i = frames.length - 1;
				for (int j = 0; j < frames.length - 1; j++) {
					if (f <= frames[j + 1].timestamp()) {
						i = j;
						break;
					}
				}
				if (i >= frames.length - 1) {
					CACHE.set(frames[i].target());
				} else {
					float span = frames[i + 1].timestamp() - frames[i].timestamp();
					float p = span <= 0 ? 1.0F : Mth.clamp((f - frames[i].timestamp()) / span, 0.0F, 1.0F);
					CACHE.set(frames[i].target()).lerp(frames[i + 1].target(), p);
				}
				channel.target().apply(part, CACHE);
			}
		}
	}

	/** 按动画用到的骨骼层级解析（whole → leg2→leg3、leg4→leg5），缺失的返回 null */
	private static Map<String, ModelPart> resolveBones(ModelPart whole) {
		Map<String, ModelPart> parts = new HashMap<>();
		parts.put("whole", whole);
		ModelPart leg2 = safeChild(whole, "leg2");
		if (leg2 != null) {
			parts.put("leg2", leg2);
			parts.put("leg3", safeChild(leg2, "leg3"));
		}
		ModelPart leg4 = safeChild(whole, "leg4");
		if (leg4 != null) {
			parts.put("leg4", leg4);
			parts.put("leg5", safeChild(leg4, "leg5"));
		}
		return parts;
	}

	private static ModelPart safeChild(ModelPart parent, String name) {
		try {
			return parent.getChild(name);
		} catch (Exception e) {
			return null;
		}
	}
}
