package net.mcreator.mcanomalyarchives.client;

import net.mcreator.mcanomalyarchives.anomaly.nametag.ResolvedName;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 转化中的**外观表现**：把目标方块的贴图**真正混到**生物身上。
 *
 * 作者要求："材质替换弄两张贴图的真正混合，不要体型缩放了"。
 *
 * 做法（见 {@code mixin/NamedTransformRenderMixin}）：**同一个模型画两遍**——
 * 第一遍是原版那一遍（生物自己的贴图，完全不动），
 * 第二遍把贴图换成目标方块、顶点色 alpha 随进度从 0 涨到 1。
 * 两遍叠起来就是"这块材质一点点盖满它的身体"，到 100% 时全身都是方块材质。
 *
 * 之所以不动原版那一遍：顶点色本来就带 alpha、{@code RenderType.entityTranslucent} 会做混合，
 * 所以**不需要自定义 shader**，也不需要把原版渲染改成半透明（那会牵扯渲染类型与排序，风险更大）。
 *
 * 【只给"变成方块"用】物品那一支作者明确说"不用换材质"；实体那一支终局是换 AI、模型材质不变。
 *
 * 【工程层归属】client 包（非 MCreator 生成区）。
 */
public final class NamedMorphClient {

	/** 贴图路径 → 存不存在（不存在就整段跳过，绝不会出现紫黑方块）。 */
	private static final Map<ResourceLocation, Boolean> EXISTS = new ConcurrentHashMap<>();

	private NamedMorphClient() {
	}

	/**
	 * 要盖上去的那张贴图（目标方块）。
	 *
	 * ⚠️ 这里**不能**在进度到 100% 时返回 null —— 之前就是这么写的，结果进度一满、
	 * 混合被关掉，贴图"啪"地弹回生物原本的材质，而它还要过一小会儿才真正变成方块。
	 * 现在只要进度 > 0 就一直盖着，到 100% 时 alpha = 255（全身都是方块材质），
	 * 直到服务端把它替换成方块为止。
	 *
	 * @return 目标方块贴图；没在转化、或贴图不存在时返回 null
	 */
	public static ResourceLocation blendTexture(LivingEntity entity) {
		if (NamedTransformClient.progressOf(entity) <= 0.0f) {
			return null;
		}
		String token = NamedTransformClient.targetOf(entity);
		ResolvedName target = token == null ? null : ResolvedName.parse(token);
		if (target == null || target.kind() != ResolvedName.Kind.BLOCK) {
			return null;
		}
		ResourceLocation path = ResourceLocation.fromNamespaceAndPath(target.id().getNamespace(),
				"textures/block/" + target.id().getPath() + ".png");
		return textureExists(path) ? path : null;
	}

	/** 第二遍（目标方块贴图）的 alpha：随进度 0 → 255。 */
	public static int targetAlpha(LivingEntity entity) {
		float progress = NamedTransformClient.progressOf(entity);
		int alpha = Math.round(Math.max(0.0f, Math.min(1.0f, progress)) * 255.0f);
		// 太小的时候干脆不画，省一次绘制调用
		return alpha < 4 ? 0 : Math.min(255, alpha);
	}

	private static boolean textureExists(ResourceLocation path) {
		return EXISTS.computeIfAbsent(path, p -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.getResourceManager() == null) {
				return false;
			}
			try {
				return mc.getResourceManager().getResource(p).isPresent();
			} catch (Throwable t) {
				return false;
			}
		});
	}
}
