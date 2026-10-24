package net.mcreator.mcanomalyarchives.client;

import java.util.Map;
import java.util.WeakHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mcreator.mcanomalyarchives.anomaly.nametag.ResolvedName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;

/** 目标外观仅用客户端替身绘制，不替换服务端血量、掉落和寿命。 */
public final class NamedEntityAppearance {
	private static final Map<LivingEntity, LivingEntity> MODELS = new WeakHashMap<>();
	private static final Map<LivingEntity, Integer> WALK_TICKS = new WeakHashMap<>();
	private static boolean renderingProxy;
	private NamedEntityAppearance() { }

	public static void clear() { MODELS.clear(); WALK_TICKS.clear(); }

	public static boolean render(LivingEntity source, float yaw, float partialTick, PoseStack poses,
			MultiBufferSource buffers, int light) {
		if (renderingProxy) return false;
		String token = NamedTransformClient.targetOf(source);
		if (token == null) return false;
		ResolvedName target = ResolvedName.parse(token);
		if (target == null || target.kind() != ResolvedName.Kind.ENTITY
				|| target.id().equals(BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()))
				|| source.isInvisible()) return false;
		LivingEntity model = MODELS.get(source);
		if (model == null || !target.id().equals(BuiltInRegistries.ENTITY_TYPE.getKey(model.getType()))) {
			if (!BuiltInRegistries.ENTITY_TYPE.containsKey(target.id())) return false;
			var created = BuiltInRegistries.ENTITY_TYPE.get(target.id()).create(source.level());
			if (!(created instanceof LivingEntity living)) return false;
			model = living;
			MODELS.put(source, model);
		}
		model.setPos(source.position());
		model.setYRot(source.getYRot());
		model.yRotO = source.yRotO;
		model.setXRot(source.getXRot());
		model.xRotO = source.xRotO;
		model.yBodyRot = source.yBodyRot;
		model.yBodyRotO = source.yBodyRotO;
		model.yHeadRot = source.yHeadRot;
		model.yHeadRotO = source.yHeadRotO;
		model.tickCount = source.tickCount;
		model.hurtTime = source.hurtTime;
		model.deathTime = source.deathTime;
		model.attackAnim = source.attackAnim;
		model.oAttackAnim = source.oAttackAnim;
		model.setPose(source.getPose());
		model.setOnGround(source.onGround());
		model.setDeltaMovement(source.getDeltaMovement());
		model.setCustomName(source.getCustomName());
		model.setCustomNameVisible(source.isCustomNameVisible());
		if (model instanceof AgeableMob ageable) ageable.setAge(source.isBaby() ? -24000 : 0);
		if (model.tickCount != WALK_TICKS.getOrDefault(model, -1)) {
			model.walkAnimation.update(source.walkAnimation.speed(), 1.0f);
			WALK_TICKS.put(model, model.tickCount);
		}
		// 替身不加入世界、不执行 AI。即使临时 ID 撞上命名缓存，也由标记阻止递归。
		renderingProxy = true;
		try {
			Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(model)
					.render(model, yaw, partialTick, poses, buffers, light);
		} finally {
			renderingProxy = false;
		}
		return true;
	}
}
