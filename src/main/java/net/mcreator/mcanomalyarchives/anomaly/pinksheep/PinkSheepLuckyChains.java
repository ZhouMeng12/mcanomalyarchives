package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.mcreator.mcanomalyarchives.events.PinkSheepGameRules;
import net.mcreator.mcanomalyarchives.runtime.ServerTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Contextual, beneficial coincidences. No explosions, excavations, or rewards from an unloaded location. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "mcanomalyarchives")
public final class PinkSheepLuckyChains {
	private record Plan(String id, BlockPos origin, BooleanSupplier valid, Runnable hint, BooleanSupplier finish) { }
	private static final Map<ServerPlayer, PinkSheepFortuneHistory> HISTORY = new WeakHashMap<>();
	private PinkSheepLuckyChains() { }
	@net.neoforged.bus.api.SubscribeEvent
	public static void onDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && HISTORY.containsKey(player)) HISTORY.get(player).invalidate();
	}
	@net.neoforged.bus.api.SubscribeEvent
	public static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
		if (!PinkSheepGameRules.enabled(event.getServer())) HISTORY.values().forEach(PinkSheepFortuneHistory::invalidate);
	}
	@net.neoforged.bus.api.SubscribeEvent
	public static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { HISTORY.clear(); }

	public static void schedule(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var history = HISTORY.computeIfAbsent(player, p -> new PinkSheepFortuneHistory());
		if (!history.reserve(level.getGameTime())) return;
		long generation = history.generation();
		ServerTasks.scheduleForPlayer(player, 300 + player.getRandom().nextInt(601), () -> {
			if (!eligible(player, level) || history.generation() != generation) return;
			List<Plan> plans = candidates(player, level);
			if (plans.isEmpty()) return;
			List<Plan> fresh = plans.stream().filter(plan -> !history.recent(plan.id)).toList();
			List<Plan> pool = fresh.isEmpty() ? plans : fresh;
			Plan plan = pool.get(player.getRandom().nextInt(pool.size()));
			if (!valid(player, level, plan)) return;
			plan.hint.run();
			ServerTasks.scheduleForPlayer(player, 60, () -> {
				if (history.generation() != generation || !valid(player, level, plan) || !plan.finish.getAsBoolean()) return;
				history.completed(plan.id);
				PinkSheepAdvancements.grantSighting(player);
				player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 0));
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, plan.origin.getX() + .5,
						plan.origin.getY() + .8, plan.origin.getZ() + .5, 6, .3, .3, .3, .01);
			});
		});
	}

	private static boolean eligible(ServerPlayer player, ServerLevel level) {
		return player.isAlive() && !player.isRemoved() && player.serverLevel() == level
				&& !player.isCreative() && !player.isSpectator() && PinkSheepGameRules.enabled(level.getServer());
	}
	private static boolean valid(ServerPlayer player, ServerLevel level, Plan plan) {
		return eligible(player, level) && player.distanceToSqr(Vec3.atCenterOf(plan.origin)) < 24 * 24
				&& level.hasChunkAt(plan.origin) && plan.valid.getAsBoolean();
	}
	private static boolean present(Entity entity, ServerLevel level) {
		return entity.isAlive() && !entity.isRemoved() && entity.level() == level && level.hasChunkAt(entity.blockPosition());
	}
	private static boolean near(Entity entity, ServerPlayer player, ServerLevel level) {
		return present(entity, level) && entity.distanceToSqr(player) <= 16 * 16;
	}
	private static void sound(ServerLevel level, BlockPos pos, SoundEvent sound) {
		level.playSound(null, pos, sound, SoundSource.NEUTRAL, .7f, 1f);
	}
	private static void hint(ServerLevel level, BlockPos pos) {
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5,
				5, .25, .25, .25, .01);
	}
	private static boolean drop(ServerLevel level, Vec3 pos, ItemStack stack) {
		if (!level.hasChunkAt(BlockPos.containing(pos))) return false;
		ItemEntity item = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
		item.setDefaultPickUpDelay();
		return level.addFreshEntity(item);
	}

	private static List<Plan> candidates(ServerPlayer player, ServerLevel level) {
		Map<String, Plan> plans = new LinkedHashMap<>();
		BlockPos base = player.blockPosition();
		for (BlockPos cursor : BlockPos.betweenClosed(base.offset(-8, -3, -8), base.offset(8, 5, 8))) {
			BlockPos pos = cursor.immutable();
			if (!level.hasChunkAt(pos)) continue;
			BlockState state = level.getBlockState(pos);
			BooleanSupplier unchanged = () -> level.getBlockState(pos).equals(state);
			if (state.is(Blocks.SHORT_GRASS))
				plans.putIfAbsent("seeds", new Plan("seeds", pos, unchanged,
						() -> sound(level, pos, SoundEvents.GRASS_STEP), () -> {
							if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) return false;
							return drop(level, Vec3.atCenterOf(pos), new ItemStack(Items.WHEAT_SEEDS, 3));
						}));
			if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state))
				plans.putIfAbsent("harvest", new Plan("harvest", pos, unchanged, () -> hint(level, pos),
						() -> level.setBlock(pos, crop.getStateForAge(crop.getMaxAge()), 3)));
			if (state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) < 3)
				plans.putIfAbsent("berries", new Plan("berries", pos, unchanged, () -> sound(level, pos, SoundEvents.GRASS_STEP),
						() -> level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 3), 3)));
			if (state.getBlock() instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) < 5
					&& !level.getEntitiesOfClass(Bee.class, new net.minecraft.world.phys.AABB(pos).inflate(8)).isEmpty())
				plans.putIfAbsent("honey", new Plan("honey", pos, unchanged, () -> sound(level, pos, SoundEvents.BEE_POLLINATE),
						() -> level.setBlock(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, 5), 3)));
			if ((state.is(Blocks.OAK_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES))
					&& !state.getValue(LeavesBlock.PERSISTENT) && level.getBlockState(pos.below()).isAir())
				plans.putIfAbsent("apple", new Plan("apple", pos,
						() -> unchanged.getAsBoolean() && level.getBlockState(pos.below()).isAir(),
						() -> sound(level, pos, SoundEvents.GRASS_STEP),
						() -> drop(level, Vec3.atBottomCenterOf(pos), new ItemStack(Items.APPLE, 2))));
			// Bamboo stays in its own empty column; unlike arbitrary tree growth it cannot replace nearby builds.
			if (state.is(Blocks.BAMBOO) && level.hasChunkAt(pos.above()) && level.getBlockState(pos.above()).isAir())
				plans.putIfAbsent("bamboo", new Plan("bamboo", pos,
						() -> unchanged.getAsBoolean() && level.getBlockState(pos.above()).isAir(),
						() -> sound(level, pos, SoundEvents.BAMBOO_SAPLING_PLACE),
						() -> level.setBlock(pos.above(), Blocks.BAMBOO.defaultBlockState(), 3)));
			if (state.is(Blocks.SUGAR_CANE) && level.getBlockState(pos.above()).isAir()
					&& !level.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE))
				plans.putIfAbsent("sugar_cane", new Plan("sugar_cane", pos,
						() -> unchanged.getAsBoolean() && level.getBlockState(pos.above()).isAir(),
						() -> sound(level, pos, SoundEvents.GRASS_STEP),
						() -> level.setBlock(pos.above(), Blocks.SUGAR_CANE.defaultBlockState(), 3)));
		}
		var area = player.getBoundingBox().inflate(12);
		for (Chicken chicken : level.getEntitiesOfClass(Chicken.class, area, c -> !c.isBaby())) {
			plans.putIfAbsent("egg", new Plan("egg", chicken.blockPosition(), () -> near(chicken, player, level) && !chicken.isBaby(),
					() -> sound(level, chicken.blockPosition(), SoundEvents.CHICKEN_AMBIENT), () -> {
						if (!drop(level, chicken.position(), new ItemStack(Items.EGG))) return false;
						sound(level, chicken.blockPosition(), SoundEvents.CHICKEN_EGG);
						return true;
					}));
		}
		for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, area, s -> s.isSheared() && !s.isBaby())) {
			BlockPos grass = sheep.blockPosition().below();
			if (!level.getBlockState(grass).is(Blocks.GRASS_BLOCK)) continue;
			plans.putIfAbsent("wool", new Plan("wool", grass,
					() -> near(sheep, player, level) && sheep.isSheared() && sheep.blockPosition().below().equals(grass)
							&& level.getBlockState(grass).is(Blocks.GRASS_BLOCK),
					() -> { level.broadcastEntityEvent(sheep, (byte) 10); sound(level, grass, SoundEvents.GRASS_BREAK); },
					() -> { if (!level.setBlock(grass, Blocks.DIRT.defaultBlockState(), 3)) return false; sheep.setSheared(false); return true; }));
		}
		for (Cat cat : level.getEntitiesOfClass(Cat.class, area, c -> c.isTame() && !c.isOrderedToSit() && player.getUUID().equals(c.getOwnerUUID())))
			plans.putIfAbsent("cat_gift", new Plan("cat_gift", cat.blockPosition(), () -> near(cat, player, level)
					&& !cat.isOrderedToSit() && player.getUUID().equals(cat.getOwnerUUID()), () -> {
						sound(level, cat.blockPosition(), SoundEvents.CAT_AMBIENT);
						cat.getNavigation().moveTo(player, 1.1);
					}, () -> drop(level, cat.position(), new ItemStack(Items.RABBIT_HIDE, 2))));
		for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, area, g -> g.getTarget() == null)) {
			var threats = level.getEntitiesOfClass(Monster.class, area, m -> m.getTarget() == player && m.isAlive()
					&& m.getType() != EntityType.CREEPER && m.getType() != EntityType.WITHER && m.getType() != EntityType.WARDEN);
			if (threats.isEmpty()) continue;
			Monster threat = threats.get(0);
			plans.putIfAbsent("golem", new Plan("golem", golem.blockPosition(), () -> near(golem, player, level)
					&& near(threat, player, level) && threat.getTarget() == player && golem.getTarget() == null
					&& golem.distanceToSqr(threat) < 24 * 24,
					() -> sound(level, golem.blockPosition(), SoundEvents.IRON_GOLEM_STEP), () -> {
						golem.setTarget(threat); golem.getNavigation().moveTo(threat, 1.1); threat.setTarget(golem); return true;
					}));
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, e -> e.isInWater() && !e.getItem().isEmpty()))
			plans.putIfAbsent("current", new Plan("current", item.blockPosition(), () -> near(item, player, level) && item.isInWater(),
					() -> level.sendParticles(ParticleTypes.SPLASH, item.getX(), item.getY(), item.getZ(), 8, .3, .1, .3, .02),
					() -> { item.setDeltaMovement(player.position().subtract(item.position()).normalize().scale(.3).add(0, .12, 0)); item.hurtMarked = true; return true; }));
		// A nearby dolphin briefly escorts a swimmer; no new animal or loot is created.
		for (Dolphin dolphin : level.getEntitiesOfClass(Dolphin.class, area, d -> d.isAlive())) {
			if (!player.isInWater()) break;
			plans.putIfAbsent("dolphin", new Plan("dolphin", dolphin.blockPosition(), () -> near(dolphin, player, level) && player.isInWater(),
					() -> sound(level, dolphin.blockPosition(), SoundEvents.DOLPHIN_AMBIENT), () -> {
						player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 600, 0));
						dolphin.getNavigation().moveTo(player, 1.2); return true;
					}));
		}
		// On an otherwise empty plain, a single ordinary chicken can wander in from safe ground.
		// Never spawn in a wall, liquid, unloaded chunk, or within the player's immediate reach.
		if (plans.isEmpty()) {
			for (int attempt = 0; attempt < 16; attempt++) {
				double angle = player.getRandom().nextDouble() * Math.PI * 2;
				BlockPos arrival = base.offset((int) Math.round(Math.cos(angle) * 7), 0,
						(int) Math.round(Math.sin(angle) * 7));
				if (!level.hasChunkAt(arrival) || !safeChickenSpot(level, arrival)
						|| player.getLookAngle().dot(Vec3.atCenterOf(arrival).subtract(player.getEyePosition()).normalize()) > .5) continue;
				Chicken[] visitor = new Chicken[1];
				plans.put("visitor", new Plan("visitor", arrival,
						() -> visitor[0] == null ? safeChickenSpot(level, arrival) : near(visitor[0], player, level), () -> {
							Chicken chicken = EntityType.CHICKEN.create(level);
							if (chicken == null) return;
							chicken.moveTo(Vec3.atBottomCenterOf(arrival));
							if (!level.addFreshEntity(chicken)) return;
							visitor[0] = chicken;
							chicken.getNavigation().moveTo(player, 1);
							sound(level, arrival, SoundEvents.CHICKEN_AMBIENT);
						}, () -> {
							if (visitor[0] == null) return false;
							sound(level, visitor[0].blockPosition(), SoundEvents.CHICKEN_EGG);
							return drop(level, visitor[0].position(), new ItemStack(Items.EGG));
						}));
				break;
			}
		}
		return new ArrayList<>(plans.values());
	}
	private static boolean safeChickenSpot(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos) && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)
				&& level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
				&& level.noCollision(new net.minecraft.world.phys.AABB(pos));
	}
}
