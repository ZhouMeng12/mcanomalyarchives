package net.mcreator.mcanomalyarchives.anomaly.purple;

import java.util.UUID;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.Actor;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.Outcome;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.Session;
import net.mcreator.mcanomalyarchives.anomaly.purple.PurpleStorySessions.Stage;
import net.mcreator.mcanomalyarchives.entity.PurpleDogEntity;
import net.mcreator.mcanomalyarchives.entity.PurpleMonsterEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModAttachments;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModEntities;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.network.OpenPurpleGuiPacket;
import net.mcreator.mcanomalyarchives.network.PurpleStoryEndedPacket;
import net.mcreator.mcanomalyarchives.network.StartPettingClientPacket;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 紫怪剧情唯一服务端入口：生成、转换、交互授权、结算与清理集中在这里。 */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class PurpleStoryModule {
	/** 只在表演附近接受交互，不能隔着整个世界操作；正常演员生成在玩家前方 1.5~2 格。 */
	private static final double MAX_INTERACTION_DISTANCE_SQUARED = 16.0 * 16.0;
	private static MinecraftServer server;
	private static PurpleStorySessions sessions;

	private PurpleStoryModule() {
	}

	@SubscribeEvent
	public static void onStarting(ServerStartingEvent event) {
		shutdown();
		server = event.getServer();
		sessions = new PurpleStorySessions();
	}

	public static boolean hasActive(ServerPlayer player) {
		return isCurrent(player) && sessions.get(player.getUUID()) != null;
	}

	public static boolean startNatural(ServerPlayer player) {
		if (player.getData(McanomalyarchivesModAttachments.PURPLE_ASSIMILATION_COMPLETED)
				|| net.mcreator.mcanomalyarchives.event.PurpleStalkerHandler.hasActiveEncounter()) return false;
		boolean started = start(player, 1);
		if (started) net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "purple_arrival");
		return started;
	}

	public static boolean hasAnyActive() {
		return sessions != null && !sessions.active().isEmpty();
	}

	public static void cancel(ServerPlayer player, int purpleId, int dogId) {
		Session session = authorized(player);
		if (session != null && session.stage() != Stage.EXECUTION && session.ownsActors(purpleId, dogId))
			abort(player.getUUID(), true);
	}

	/** 只从已校验权限的 /purplegui 命令调用。第五阶段仍是无奖励的界面预览。 */
	public static boolean startDebug(ServerPlayer player, int phase) {
		if (!isCurrent(player) || !player.hasPermissions(2) || phase < 1 || phase > 5) return false;
		net.mcreator.mcanomalyarchives.event.PurpleStalkerHandler.dismissActive();
		abort(player.getUUID(), true);
		if (phase == 5) {
			PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(0, 5, 0));
			return true;
		}
		boolean started = start(player, phase);
		if (started) sessions.get(player.getUUID()).markPreview();
		return started;
	}

	private static boolean start(ServerPlayer player, int phase) {
		if (!isCurrent(player) || !player.isAlive() || player.isRemoved() || hasActive(player)) return false;
		Actors actors = spawnActors(player, phase);
		if (actors == null) return false;
		PurpleEncounterEffects.materialize(actors.purple);
		Stage opening = phase == 1 ? Stage.QUESTION : phase == 4 ? Stage.ANIMAL : Stage.REVENGE;
		sessions.open(player.getUUID(), dimension(player.serverLevel()), actor(actors.purple), actor(actors.dog), opening);
		PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(actors.purple.getId(), phase,
				actors.dog == null ? 0 : actors.dog.getId()));
		return true;
	}

	public static void advanceToPhase2(ServerPlayer player, int purpleId) {
		Session session = authorized(player);
		if (session == null || !session.advanceToPhase2(purpleId)) return;
		PurpleMonsterEntity purple = purple(session);
		net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "purple_change");
		purple.setTextureVariant(1); // 原客户端只改本地贴图，现在同步服务器权威表现。
		syncAppearance(player);
		PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(purpleId, 2, 0));
	}

	public static void advanceToPhase4(ServerPlayer player, int purpleId) {
		Session session = authorized(player);
		if (session != null) session.beginPhase4(purpleId);
	}

	public static void startPetting(ServerPlayer player, int purpleId, int dogId) {
		Session session = authorized(player);
		if (session != null && session.stage() == Stage.ANIMAL
				&& !pettingRoute(player, findActor(session, session.dog()))) {
			abort(player.getUUID(), true);
			return;
		}
		if (session == null || !session.startPetting(purpleId, dogId, server.getTickCount())) return;
		syncAppearance(player);
		PacketDistributor.sendToPlayer(player, new StartPettingClientPacket(purpleId, dogId,
				player.getX(), player.getY(), player.getZ()));
	}

	public static void completePetting(ServerPlayer player, int purpleId, int dogId) {
		Session session = authorized(player);
		if (session == null || !session.completePetting(purpleId, dogId, server.getTickCount())) return;
		net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "purple_pet");
		// 丢弃准确的主手槽，而不是按物品相等从背包里移除其他同类物品。
		ItemStack held = player.getMainHandItem().copy();
		if (!held.isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.drop(held, false);
		}
		purple(session).setModelVariant(1);
		PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(purpleId, 5, dogId));
	}

	public static void claimReward(ServerPlayer player) {
		Session session = authorized(player);
		if (session == null) return;
		// 兼容旧的空载荷领奖消息：只能结算自己已经到第五阶段的当前会话，不允许独立发物品。
		settle(player, session, session.claimReward());
	}

	public static void finish(ServerPlayer player, int purpleId, int dogId, boolean kill, boolean killPlayer) {
		Session session = authorized(player);
		if (session == null) return;
		settle(player, session, session.finish(purpleId, dogId, kill, killPlayer));
	}

	private static void settle(ServerPlayer player, Session session, Outcome outcome) {
		if (outcome == Outcome.REJECTED) return;
		PurpleMonsterEntity purple = purple(session);
		if (outcome == Outcome.KILL_PLAYER) {
			purple.setExecution(player.getId(), 0);
			return;
		}
		// 先消耗会话，再执行物品/死亡等可触发其他事件的效果，重复包和重入均不能再结算。
		sessions.close(player.getUUID());
		try {
			if (!session.preview() && (outcome == Outcome.ASSIMILATED || outcome == Outcome.REWARDED)) {
				net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "purple_assimilation");
				player.setData(McanomalyarchivesModAttachments.PURPLE_ASSIMILATION_COMPLETED, true);
			}
			if (!session.preview() && outcome == Outcome.REWARDED) {
				net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "purple_reward");
				ItemStack reward = new ItemStack(McanomalyarchivesModItems.PURPLEHAND.get());
				if (!player.getInventory().add(reward)) player.drop(reward, false);
			}
		} finally {
			discardActors(session);
			syncAppearance(player);
			notifyEnded(player);
		}
	}

	private static boolean isCurrent(ServerPlayer player) {
		return belongsToServer(player) && sessions != null
				&& server.getPlayerList().getPlayer(player.getUUID()) == player;
	}

	private static boolean belongsToServer(ServerPlayer player) {
		return server != null && player.getServer() == server && server.isSameThread();
	}

	private static Session authorized(ServerPlayer player) {
		if (!isCurrent(player)) return null;
		Session session = sessions.get(player.getUUID());
		if (session == null) return null;
		if (!valid(player, session)) {
			abort(player.getUUID(), true);
			return null;
		}
		return session;
	}

	private static boolean valid(ServerPlayer player, Session session) {
		boolean withdrawing = session.stage() == Stage.EXECUTION && session.execution().hit();
		if ((!player.isAlive() && !withdrawing) || player.isRemoved() || player.isSpectator() || (player.isCreative() && !session.preview())
				|| session.pettingExpired(server.getTickCount())
				|| !dimension(player.serverLevel()).equals(session.dimension())) return false;
		if (session.actorsTemporarilyAbsent()) return true;
		PurpleMonsterEntity purple = purple(session);
		if (purple == null || !purple.isAlive() || !purple.isPerformMode()
				|| !player.getUUID().equals(purple.getPerformTargetUUID())
				|| player.distanceToSqr(purple) > MAX_INTERACTION_DISTANCE_SQUARED) return false;
		if (session.dog() != null) {
			Entity dog = findActor(session, session.dog());
			if (!(dog instanceof PurpleDogEntity pd) || !pd.isAlive() || !pd.isPerformMode()
					|| !player.getUUID().equals(pd.getPerformTargetUUID())
					|| player.distanceToSqr(pd) > MAX_INTERACTION_DISTANCE_SQUARED) return false;
		}
		return true;
	}

	@SubscribeEvent
	public static void onTick(ServerTickEvent.Post event) {
		if (event.getServer() != server || sessions == null) return;
		for (Session session : sessions.active()) {
			ServerPlayer player = server.getPlayerList().getPlayer(session.player());
			if (player == null || !valid(player, session)) {
				abort(session.player(), player != null);
				continue;
			}
			if (session.stage() == Stage.EXECUTION) {
				PurpleMonsterEntity actor = purple(session);
				var step = session.execution().tick();
				actor.setExecution(player.getId(), session.execution().ticks());
				if (step == PurpleExecutionSequence.Step.HIT) {
					if (!player.hasLineOfSight(actor)) { abort(session.player(), true); continue; }
					if (!session.preview()) player.hurt(player.serverLevel().damageSources().mobAttack(actor), 1000f);
					player.serverLevel().playSound(null, actor.blockPosition(), net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
							net.minecraft.sounds.SoundSource.HOSTILE, .8f, .65f);
				} else if (step == PurpleExecutionSequence.Step.END) abort(session.player(), true);
				continue;
			}
			switch (session.tickTransition()) {
				case REMOVE_OLD -> discardActors(session);
				case SPAWN -> {
					Actors replacement = spawnActors(player, 4);
					if (replacement == null) {
						abort(session.player(), true);
					} else if (!session.bindReplacement(actor(replacement.purple), actor(replacement.dog))) {
						replacement.purple.discard();
						replacement.dog.discard();
						abort(session.player(), true);
					}
				}
				case OPEN -> PacketDistributor.sendToPlayer(player,
						new OpenPurpleGuiPacket(session.purple().id(), 4, session.dogId()));
				case NONE -> { }
			}
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && belongsToServer(player)) {
			abort(player.getUUID(), false);
		}
	}

	@SubscribeEvent public static void onActorLoad(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
		if (!event.getLevel().isClientSide() && event.loadedFromDisk()
				&& (event.getEntity() instanceof PurpleMonsterEntity || event.getEntity() instanceof PurpleDogEntity)
				&& event.getEntity().getPersistentData().getBoolean("PurpleTransientActor")) event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		if (event.getEntity() instanceof ServerPlayer player && belongsToServer(player)) {
			abort(player.getUUID(), true);
		}
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && belongsToServer(player)) {
			Session session = sessions == null ? null : sessions.get(player.getUUID());
			if (session != null && session.stage() == Stage.EXECUTION && session.execution().hit()) return;
			abort(player.getUUID(), true);
		}
	}

	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && belongsToServer(player)) {
			abort(player.getUUID(), true);
			syncAppearance(player);
		}
	}

	@SubscribeEvent
	public static void onStopping(ServerStoppingEvent event) {
		if (event.getServer() == server) shutdown();
	}

	@SubscribeEvent
	public static void onStopped(ServerStoppedEvent event) {
		if (event.getServer() == server) shutdown();
	}

	private static void abort(UUID owner, boolean notify) {
		if (sessions == null) return;
		Session session = sessions.close(owner);
		if (session == null) return;
		discardActors(session);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) syncAppearance(player);
		if (notify && player != null) notifyEnded(player);
	}

	private static void shutdown() {
		if (sessions != null) sessions.closeAll().forEach(PurpleStoryModule::discardActors);
		sessions = null;
		server = null;
	}

	private static void notifyEnded(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new PurpleStoryEndedPacket());
	}

	private static int appearance(ServerPlayer player) {
		if (player.getData(McanomalyarchivesModAttachments.PURPLE_ASSIMILATION_COMPLETED)) return 3;
		Session session = sessions == null ? null : sessions.get(player.getUUID());
		if (session == null) return 0;
		return switch (session.stage()) {
			case PETTING, FINAL -> 2;
			case REVENGE, TRANSITION, ANIMAL -> 1;
			default -> 0;
		};
	}

	private static void syncAppearance(ServerPlayer player) {
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
				new net.mcreator.mcanomalyarchives.network.PurpleAssimilationPacket(player.getUUID(), appearance(player)));
	}

	@SubscribeEvent public static void onTracking(PlayerEvent.StartTracking event) {
		if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject)
			PacketDistributor.sendToPlayer(viewer, new net.mcreator.mcanomalyarchives.network.PurpleAssimilationPacket(
					subject.getUUID(), appearance(subject)));
	}

	@SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) syncAppearance(player);
	}

	@SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) syncAppearance(player);
	}

	private static String dimension(ServerLevel level) {
		return level.dimension().location().toString();
	}

	private static Entity findActor(Session session, Actor actor) {
		if (actor == null || server == null) return null;
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
				ResourceLocation.parse(session.dimension())));
		Entity entity = level == null ? null : level.getEntity(actor.uuid());
		return entity != null && entity.getId() == actor.id() && !entity.isRemoved() ? entity : null;
	}

	private static PurpleMonsterEntity purple(Session session) {
		Entity entity = findActor(session, session.purple());
		return entity instanceof PurpleMonsterEntity pm ? pm : null;
	}

	private static void discardActors(Session session) {
		discardOwned(session, session.purple());
		discardOwned(session, session.dog());
	}

	private static void discardOwned(Session session, Actor actor) {
		Entity entity = findActor(session, actor);
		boolean owned = entity instanceof PurpleMonsterEntity pm && pm.isPerformMode()
				&& session.player().equals(pm.getPerformTargetUUID())
				|| entity instanceof PurpleDogEntity pd && pd.isPerformMode()
				&& session.player().equals(pd.getPerformTargetUUID());
		if (owned) {
			if (entity instanceof PurpleMonsterEntity) PurpleEncounterEffects.dissolve(entity);
			entity.discard();
		}
	}

	private record Actors(PurpleMonsterEntity purple, PurpleDogEntity dog) { }

	private static Actor actor(Entity entity) {
		return entity == null ? null : new Actor(entity.getId(), entity.getUUID());
	}

	private static Actors spawnActors(ServerPlayer player, int phase) {
		ServerLevel level = player.serverLevel();
		double yaw = Math.toRadians(player.getYRot());
		double front = phase == 4 ? 1.5 : 2.0;
		double side = phase == 4 ? 1.0 : 0.0;
		PurpleMonsterEntity purple = new PurpleMonsterEntity(McanomalyarchivesModEntities.PURPLE_MONSTER.get(), level);
		if (!place(purple, player, player.getX() - Math.sin(yaw) * front + Math.cos(yaw) * side,
				player.getZ() + Math.cos(yaw) * front + Math.sin(yaw) * side)) return null;
		purple.setPerformMode(player.getUUID());
		purple.setTextureVariant(phase == 4 ? 1 : 0);
		if (!level.addFreshEntity(purple)) return null;
		PurpleDogEntity dog = null;
		if (phase == 4) {
			dog = new PurpleDogEntity(McanomalyarchivesModEntities.PURPLE_DOG.get(), level);
			if (!place(dog, player, player.getX() - Math.sin(yaw) * 1.5, player.getZ() + Math.cos(yaw) * 1.5)) {
				purple.discard();
				return null;
			}
			dog.setPerformMode(player.getUUID());
			if (!level.addFreshEntity(dog)) {
				purple.discard();
				return null;
			}
		}
		return new Actors(purple, dog);
	}

	/** The client walks on a straight horizontal path; reject steps, pits and blocked return routes. */
	private static boolean pettingRoute(ServerPlayer player, Entity dog) {
		if (dog == null || Math.abs(dog.getY() - player.getY()) > .5) return false;
		var delta = dog.position().subtract(player.position()).multiply(1, 0, 1);
		double length = delta.length();
		if (length > 4) return false;
		var travel = delta.normalize().scale(Math.max(0, length - 1.35));
		int steps = Math.max(1, (int)Math.ceil(travel.length() * 8));
		var level = player.serverLevel();
		for (int i = 0; i <= steps; i++) {
			var box = player.getBoundingBox().move(travel.scale(i / (double)steps));
			for (var p : net.minecraft.core.BlockPos.betweenClosed(
					net.minecraft.core.BlockPos.containing(box.minX, box.minY - .1, box.minZ),
					net.minecraft.core.BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
				if (!level.hasChunkAt(p) || !level.getFluidState(p).isEmpty()) return false;
			if (!level.noCollision(player, box) || level.noCollision(player, box.move(0, -.1, 0))) return false;
		}
		return true;
	}

	private static boolean place(Entity entity, ServerPlayer player, double x, double z) {
		var level = player.serverLevel();
		boolean found = false;
		double[][] offsets = {{0, 0}, {.8, 0}, {-.8, 0}, {0, .8}, {0, -.8}, {1.4, 0}, {-1.4, 0}};
		outer: for (double[] offset : offsets) {
			for (int dy : new int[] {0, 1, -1}) {
				entity.setPos(x + offset[0], player.getY() + dy, z + offset[1]);
				var feet = entity.blockPosition();
				var box = entity.getBoundingBox();
				if (level.hasChunksAt(net.minecraft.core.BlockPos.containing(box.minX, box.minY - .1, box.minZ),
						net.minecraft.core.BlockPos.containing(box.maxX, box.maxY, box.maxZ))
						&& level.noCollision(entity, entity.getBoundingBox())
						&& level.getFluidState(feet).isEmpty()
						&& !level.noCollision(entity, entity.getBoundingBox().move(0, -.1, 0))
						&& player.hasLineOfSight(entity)
						&& (!(entity instanceof PurpleDogEntity) || pettingRoute(player, entity))) {
					found = true;
					break outer;
				}
			}
		}
		if (!found) return false;
		entity.setXRot(0.0F);
		entity.setYRot(player.getYRot() + 180.0F);
		if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
			living.yBodyRot = entity.getYRot();
			living.yHeadRot = entity.getYRot();
		}
		return true;
	}
}
