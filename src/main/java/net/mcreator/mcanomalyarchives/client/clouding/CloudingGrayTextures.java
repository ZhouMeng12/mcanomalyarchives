package net.mcreator.mcanomalyarchives.client.clouding;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import net.mcreator.mcanomalyarchives.clouding.CloudingBlacklist;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 云化一级灰白贴图工具（仅客户端）。
 * 运行时把生物本体贴图处理成"灰白亮化"变体并缓存，供渲染 mixin 换贴图使用。
 */
public final class CloudingGrayTextures {

	/** 触发灰白的云化等级：1 = 云化二级；改成 0 则一级也灰白 */
	private static final int TRIGGER_AMPLIFIER = 1;

	/** 亮化系数：灰阶向白色靠拢的比例（0~1），0.7 ≈ 云化奶白 */
	private static final float WHITEN = 0.7f;

	/** 原贴图 → 灰白变体 */
	private static final Map<ResourceLocation, ResourceLocation> CACHE = new ConcurrentHashMap<>();

	private CloudingGrayTextures() {
	}

	/** 清空灰白贴图缓存（资源重载时调用） */
	public static void clearCache() {
		CACHE.clear();
	}

	/** 生物是否处于云化二级及以上（amplifier >= 1）；黑名单生物不变灰 */
	public static boolean shouldGray(LivingEntity entity) {
		if (CloudingBlacklist.isBlacklisted(entity)) return false;
		MobEffectInstance effect = entity.getEffect(McanomalyarchivesModMobEffects.CLOUDING);
		return effect != null && effect.getAmplifier() >= TRIGGER_AMPLIFIER;
	}

	/** 返回 original 的灰白变体；任何失败都回退 original，保证不崩 */
	public static ResourceLocation gray(ResourceLocation original) {
		Minecraft mc = Minecraft.getInstance();
		ResourceLocation cached = CACHE.get(original);
		if (cached != null) {
			// F3+T 等资源重载后动态贴图会被清掉：检测到缺失就重建
			if (mc.getTextureManager().getTexture(cached) != MissingTextureAtlasSprite.getTexture()) {
				return cached;
			}
			CACHE.remove(original);
		}
		try {
			Optional<Resource> res = mc.getResourceManager().getResource(original);
			if (res.isEmpty()) return original;
			NativeImage image;
			try (InputStream in = res.get().open()) {
				image = NativeImage.read(in);
			}
			grayify(image);
			String name = "clouding_gray/" + original.getNamespace() + "_" + Integer.toHexString(original.getPath().hashCode());
			DynamicTexture dynamic = new DynamicTexture(image);
			ResourceLocation out = mc.getTextureManager().register(name, dynamic);
			dynamic.upload();
			CACHE.put(original, out);
			return out;
		} catch (Exception e) {
			return original;
		}
	}

	/**
	 * 灰白化 + 亮化。
	 * 三个低字节等权取灰、等值写回（不依赖通道字节序），alpha（高字节）原样保留。
	 */
	private static void grayify(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int c = image.getPixelRGBA(x, y);
				int a = c >>> 24;
				int gray = ((c & 0xFF) + ((c >> 8) & 0xFF) + ((c >> 16) & 0xFF)) / 3;
				int bright = gray + (int) ((255 - gray) * WHITEN);
				image.setPixelRGBA(x, y, (a << 24) | (bright << 16) | (bright << 8) | bright);
			}
		}
	}
}
