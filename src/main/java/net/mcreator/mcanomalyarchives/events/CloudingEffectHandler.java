package net.mcreator.mcanomalyarchives.events;

import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.mcreator.mcanomalyarchives.clouding.CloudingBlacklist;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.mcreator.mcanomalyarchives.network.CloudingVisualPacket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber
public class CloudingEffectHandler {

	private static final double DRIFT_SPEED = 0.04;

	/**
	 * 被云化二级剥夺 AI 的生物记录：UUID → 施加云化前的 isNoAi 状态。
	 * 效果结束后按此表恢复"原来的 AI"（而不是无条件 setNoAi(false)）。
	 * 服务端 tick 单线程访问，HashMap 足够。
	 */
	private static final Map<UUID, Boolean> CLOUDED_PRE_NO_AI = new HashMap<>();

	/** 云化二级的玩家（维持飞行、禁止落地） */
	private static final Set<UUID> CLOUDED_PLAYERS = new HashSet<>();

	/**
	 * 云化 II 效果添加时：
	 * - 玩家：记录并开启飞行模式
	 * - 非玩家生物：记录原 AI 状态，剥夺 AI、重力、碰撞，原地浮起
	 * - 黑名单生物：效果上身但无任何副作用
	 */
	@SubscribeEvent
	public static void onEffectAdded(MobEffectEvent.Added event) {
		if (event.getEffectInstance() == null
			|| event.getEffectInstance().getEffect().value() != McanomalyarchivesModMobEffects.CLOUDING.get()) return;
		if (event.getEffectInstance().getAmplifier() < 1) return;

		if (event.getEntity() instanceof Player player) {
			CLOUDED_PLAYERS.add(player.getUUID());
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		} else if (event.getEntity() instanceof Mob mob) {
			// 黑名单生物：效果可以上身，但无任何副作用（不变灰、不失去 AI、不飘移）
			if (CloudingBlacklist.isBlacklisted(mob)) {
				return;
			}
			CLOUDED_PRE_NO_AI.putIfAbsent(mob.getUUID(), mob.isNoAi());
			mob.setNoAi(true);
			mob.setNoGravity(true);
			mob.noPhysics = true;
		}
		// 下发客户端灰白标记（不依赖效果在客户端的可见性）
		sendVisual(event.getEntity(), true);
	}

	/** 每 tick：只遍历被跟踪的云化生物/玩家小集合（不再扫描全维度所有实体） */
	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		tickCloudedMobs(event);
		tickCloudedPlayers(event);
	}

	private static void tickCloudedMobs(ServerTickEvent.Post event) {
		if (CLOUDED_PRE_NO_AI.isEmpty()) return;
		Iterator<Map.Entry<UUID, Boolean>> it = CLOUDED_PRE_NO_AI.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Boolean> entry = it.next();
			Entity entity = findEntity(event, entry.getKey());
			if (entity instanceof LivingEntity living) {
				MobEffectInstance e = living.getEffect(McanomalyarchivesModMobEffects.CLOUDING);
				if (e != null && e.getAmplifier() >= 1 && !CloudingBlacklist.isBlacklisted(living) && living instanceof Mob mob) {
					// 仍在云化二级：维持剥夺 + 漂移
					mob.setNoAi(true);
					mob.setNoGravity(true);
					living.noPhysics = true;
					living.setDeltaMovement(0, 0, 0);
					drift(living);
					// 定期重发灰白标记（覆盖中途加入的玩家与丢包）
					if (living.tickCount % 200 == 0) {
						sendVisual(living, true);
					}
					continue;
				}
				// 效果结束/降级/被黑名单：恢复原来的 AI
				if (living instanceof Mob mob) {
					mob.setNoAi(entry.getValue());
					mob.setNoGravity(false);
					mob.noPhysics = false;
				}
				sendVisual(living, false);
			}
			it.remove();
		}
	}

	private static void tickCloudedPlayers(ServerTickEvent.Post event) {
		if (CLOUDED_PLAYERS.isEmpty()) return;
		Iterator<UUID> it = CLOUDED_PLAYERS.iterator();
		while (it.hasNext()) {
			UUID id = it.next();
			Entity entity = findEntity(event, id);
			if (entity instanceof Player player) {
				MobEffectInstance e = player.getEffect(McanomalyarchivesModMobEffects.CLOUDING);
				if (e != null && e.getAmplifier() >= 1) {
					// 仍在云化二级：强制维持飞行，禁止落地
					player.getAbilities().mayfly = true;
					player.getAbilities().flying = true;
					player.onUpdateAbilities();
					continue;
				}
				// 结束：恢复飞行状态
				if (!player.getAbilities().instabuild) {
					player.getAbilities().mayfly = false;
				}
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
				player.noPhysics = false;
				sendVisual(player, false);
			}
			it.remove();
		}
	}

	/** 云化生物：沿 X 轴正方向水平漂移（无上下浮动），并冻结走路/摆动动画 */
	private static void drift(LivingEntity entity) {
		entity.setPos(entity.getX() + DRIFT_SPEED, entity.getY(), entity.getZ());
		entity.xo = entity.getX();
		entity.yo = entity.getY();
		entity.zo = entity.getZ();
		entity.hurtMarked = true;
		// 冻结动画：walkAnimation 速度置 0，摆臂/迈腿幅度归零，不再播放行走动画
		entity.walkAnimation.setSpeed(0f);
	}

	private static Entity findEntity(ServerTickEvent.Post event, UUID id) {
		for (ServerLevel level : event.getServer().getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) return e;
		}
		return null;
	}

	/**
	 * 玩家开始追踪（靠近/重进世界/重新加载区块）某个已云化的生物时，
	 * 立刻补发灰白标记，避免客户端显示原皮肤。
	 */
	@SubscribeEvent
	public static void onStartTracking(PlayerEvent.StartTracking event) {
		Entity target = event.getTarget();
		if (!(target instanceof LivingEntity living)) return;
		if (CloudingBlacklist.isBlacklisted(living)) return;
		MobEffectInstance e = living.getEffect(McanomalyarchivesModMobEffects.CLOUDING);
		if (e != null && e.getAmplifier() >= 1 && event.getEntity() instanceof ServerPlayer player) {
			PacketDistributor.sendToPlayer(player, new CloudingVisualPacket(living.getId(), living.getUUID(), true));
		}
	}

	/** 云化效果移除时：恢复玩家飞行状态；生物的 AI 由 tickCloudedMobs 统一恢复 */
	@SubscribeEvent
	public static void onEffectRemoved(MobEffectEvent.Remove event) {
		if (event.getEffect() == null
			|| event.getEffect().value() != McanomalyarchivesModMobEffects.CLOUDING.get()) return;

		if (event.getEntity() instanceof Player player) {
			MobEffectInstance remaining = player.getEffect(McanomalyarchivesModMobEffects.CLOUDING);
			if (remaining != null) return;

			CLOUDED_PLAYERS.remove(player.getUUID());
			if (!player.getAbilities().instabuild) {
				player.getAbilities().mayfly = false;
			}
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			player.noPhysics = false;
			sendVisual(player, false);
		}
	}

	/** 广播灰白视觉标记给追踪该实体的玩家 */
	private static void sendVisual(LivingEntity entity, boolean gray) {
		if (entity.level().isClientSide()) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new CloudingVisualPacket(entity.getId(), entity.getUUID(), gray));
	}
}
