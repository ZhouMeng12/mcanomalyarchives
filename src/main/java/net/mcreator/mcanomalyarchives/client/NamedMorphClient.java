package net.mcreator.mcanomalyarchives.client;

import net.mcreator.mcanomalyarchives.anomaly.nametag.ResolvedName;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 转化中的**外形表现**：体型向目标缩放 + 颜色向目标主色靠拢。
 *
 * 作者要求"被命名的生物会慢慢变成对应的生物"，并且"模型和材质会慢慢变（或者模型不变，材质慢慢变）"。
 * 这一版实现的是**形态不变 + 材质/颜色渐变 + 体型渐缩/渐涨**：
 * <ul>
 *   <li><b>体型</b>：按进度从自己的高度插值到目标生物的高度（牛→鸡就是一点点缩下去）。</li>
 *   <li><b>颜色</b>：采样目标生物贴图的平均色，从白色按进度乘算过去。</li>
 * </ul>
 *
 * ⚠️ 顶点色是**乘算**，所以只能"变暗/偏色"，没法把暗的生物变亮
 * （牛→末影人很明显，牛→鸡几乎看不出来）。想要两个方向都明显、并且真正"模型也变"，
 * 需要把目标的模型也画一遍做交叉溶解——那是下一步，见设计文档 §7。
 *
 * 【工程层归属】client 包（非 MCreator 生成区）。
 */
public final class NamedMorphClient {

	/** 原版颜色（白色不透明）。 */
	public static final int NO_TINT = -1;

	/** 当前正在渲染的实体应有的顶点色；由渲染 mixin 在 render 前后设置/还原。 */
	private static int currentTint = NO_TINT;

	/** 目标生物贴图的平均色（按实体类型缓存，采样一次就够）。 */
	private static final Map<ResourceLocation, Integer> AVERAGE_COLOR = new ConcurrentHashMap<>();
	/** 采不到的记下来，别再反复尝试。 */
	private static final java.util.Set<ResourceLocation> FAILED = ConcurrentHashMap.newKeySet();
	/** 存在性检查结果（贴图路径 → 存不存在）。 */
	private static final Map<ResourceLocation, Boolean> EXISTS = new ConcurrentHashMap<>();

	/** 转化过半之后，身体贴图换成目标方块/物品的贴图。 */
	private static final float TEXTURE_SWAP_AT = 0.5f;

	private NamedMorphClient() {
	}

	/**
	 * 身体贴图是否该换成目标方块/物品的贴图。
	 *
	 * 作者要求：**变成方块后身体材质慢慢变成对应的方块**（变成物品同理）。
	 *
	 * 两张贴图之间的真正混合需要额外把模型再画一遍，代价与风险都大；
	 * 这里做的是"过半之后换成目标材质"——配合体型缩放与颜色渐变，整体仍然是渐进的过程。
	 *
	 * @return 要换成的贴图；不该换 / 贴图不存在时返回 null（退回原样，不会出现紫黑方块）
	 */
	public static ResourceLocation bodyTextureOverride(LivingEntity entity) {
		float progress = NamedTransformClient.progressOf(entity);
		if (progress < TEXTURE_SWAP_AT) {
			return null;
		}
		ResolvedName target = targetOf(entity);
		if (target == null) {
			return null;
		}
		String folder = switch (target.kind()) {
			case BLOCK -> "textures/block/";
			case ITEM -> "textures/item/";
			// 生物不用换贴图：走到 100% 时直接换成真身
			case ENTITY -> null;
		};
		if (folder == null) {
			return null;
		}
		ResourceLocation path = ResourceLocation.fromNamespaceAndPath(target.id().getNamespace(),
				folder + target.id().getPath() + ".png");
		return textureExists(path) ? path : null;
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

	public static void beginRender(LivingEntity entity) {
		currentTint = tintFor(entity);
	}

	public static void endRender() {
		currentTint = NO_TINT;
	}

	public static int currentTint() {
		return currentTint;
	}

	// ===== 体型 =====

	public static float scaleFor(LivingEntity entity) {
		float p = NamedTransformClient.progressOf(entity);
		if (p <= 0.0f) {
			return 1.0f;
		}
		float ratio = targetHeightRatio(entity);
		return 1.0f + (ratio - 1.0f) * p;
	}

	/** 目标高度 ÷ 自己高度。变成方块/物品时没有高度可比，统一缩到 0.6。 */
	private static float targetHeightRatio(LivingEntity entity) {
		ResolvedName target = targetOf(entity);
		if (target == null) {
			return 1.0f;
		}
		if (target.kind() != ResolvedName.Kind.ENTITY) {
			return 0.6f;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(target.id());
		float self = entity.getBbHeight();
		float want = type.getDimensions().height();
		if (self <= 0.0f || want <= 0.0f) {
			return 1.0f;
		}
		return Math.max(0.15f, Math.min(4.0f, want / self));
	}

	// ===== 颜色 =====

	private static int tintFor(LivingEntity entity) {
		float p = NamedTransformClient.progressOf(entity);
		if (p <= 0.0f) {
			return NO_TINT;
		}
		Integer average = averageColorOf(entity);
		if (average == null) {
			return NO_TINT;
		}
		int targetR = average >> 16 & 0xFF;
		int targetG = average >> 8 & 0xFF;
		int targetB = average & 0xFF;
		// 归一化到"最亮通道 = 255"：否则采到深色目标会把实体压成一团黑
		int max = Math.max(targetR, Math.max(targetG, targetB));
		if (max <= 8) {
			// 近乎纯黑的目标（末影人、凋灵）：用一个深灰代替，避免整只变黑看不出形状
			targetR = targetG = targetB = 96;
		} else {
			float k = 255.0f / max;
			targetR = Math.min(255, Math.round(targetR * k));
			targetG = Math.min(255, Math.round(targetG * k));
			targetB = Math.min(255, Math.round(targetB * k));
		}
		int r = 255 + Math.round((targetR - 255) * p);
		int g = 255 + Math.round((targetG - 255) * p);
		int b = 255 + Math.round((targetB - 255) * p);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static ResolvedName targetOf(Entity entity) {
		String token = NamedTransformClient.targetOf(entity);
		return token == null ? null : ResolvedName.parse(token);
	}

	private static Integer averageColorOf(Entity entity) {
		ResolvedName target = targetOf(entity);
		if (target == null || target.kind() != ResolvedName.Kind.ENTITY || FAILED.contains(target.id())) {
			return null;
		}
		return AVERAGE_COLOR.computeIfAbsent(target.id(), NamedMorphClient::sampleAverageColor);
	}

	/** 采样目标生物贴图的平均色；任何一步出问题都返回 null（= 不做染色），绝不抛到渲染线程外。 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Integer sampleAverageColor(ResourceLocation typeId) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) {
				return null;
			}
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
			Entity dummy = type.create(mc.level);
			if (!(dummy instanceof LivingEntity)) {
				return null;
			}
			EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(dummy);
			if (renderer == null) {
				return null;
			}
			ResourceLocation texture = renderer.getTextureLocation(dummy);
			if (texture == null) {
				return null;
			}
			var resource = mc.getResourceManager().getResource(texture);
			if (resource.isEmpty()) {
				FAILED.add(typeId);
				return null;
			}
			try (var in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
				long r = 0;
				long g = 0;
				long b = 0;
				long n = 0;
				for (int y = 0; y < image.getHeight(); y += 2) {
					for (int x = 0; x < image.getWidth(); x += 2) {
						int px = image.getPixelRGBA(x, y);
						if ((px >>> 24) < 128) {
							continue; // 跳过透明像素
						}
						r += px >> 16 & 0xFF;
						g += px >> 8 & 0xFF;
						b += px & 0xFF;
						n++;
					}
				}
				if (n == 0) {
					return null;
				}
				return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
			}
		} catch (Throwable t) {
			FAILED.add(typeId);
			return null;
		}
	}
}
