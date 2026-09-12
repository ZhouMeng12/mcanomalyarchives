package net.mcreator.mcanomalyarchives.clouding;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * 云化黑名单：这些生物可以吃云化效果，但没有任何副作用
 * （不变灰、不失去 AI、不飘移）。
 * 改这里即可增删黑名单生物（实体注册名，如 "mcanomalyarchives:purple_monster"）。
 */
public final class CloudingBlacklist {

	private static final Set<ResourceLocation> BLACKLIST = Set.of(
			ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "purple_monster"),
			ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "purple_dog"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "wither"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "ender_dragon"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "warden")
	);

	private CloudingBlacklist() {
	}

	public static boolean isBlacklisted(Entity entity) {
		if (entity == null) return false;
		ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return key != null && BLACKLIST.contains(key);
	}
}
