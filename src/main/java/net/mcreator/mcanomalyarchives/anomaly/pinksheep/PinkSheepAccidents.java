package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import java.util.*;
import net.mcreator.mcanomalyarchives.events.PinkSheepGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Scene-selected incidents. All positions are fixed when the incident is selected. */
@EventBusSubscriber(modid = "mcanomalyarchives")
public final class PinkSheepAccidents {
	private enum Kind { CAVE, JOCKEY, ROCK, ARROW }
	private record Site(Kind kind, BlockPos source, BlockPos charge, BlockState original) { }
	private static final List<Incident> PENDING = new ArrayList<>();
	private static final String PREPARING_ARROW = "PinkSheepPreparingArrow";
	private static final class Incident {
		final ServerPlayer owner;
		final ServerLevel level;
		final Site site;
		final Vec3 aim;
		final PinkSheepAccidentSequence sequence;
		final List<Entity> actors = new ArrayList<>();
		Arrow arrow;
		PrimedTnt charge;
		boolean created, failed, arrived;
		Incident(ServerPlayer owner, Site site) {
			this.owner = owner; this.level = owner.serverLevel(); this.site = site;
			this.aim = owner.position().add(0, 1, 0);
			this.sequence = new PinkSheepAccidentSequence(level.getGameTime());
		}
	}
	private PinkSheepAccidents() { }

	/** False leaves the distance threshold unconsumed. No scene means no invented disaster. */
	public static boolean start(ServerPlayer player, int severity) {
		if (PENDING.stream().anyMatch(i -> i.owner == player)) return false;
		ServerLevel level = player.serverLevel();
		List<Site> sites = new ArrayList<>();
		BlockPos base = player.blockPosition();
		// Loose material above an actual air gap; never manufacture a ceiling.
		if (level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
			for (BlockPos cursor : BlockPos.betweenClosed(base.offset(-3, 4, -3), base.offset(3, 14, 3))) {
				BlockPos pos = cursor.immutable();
				if (!level.hasChunkAt(pos)) continue;
				BlockState state = level.getBlockState(pos);
				if ((state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD))
						&& level.getBlockState(pos.below()).isAir() && !state.hasBlockEntity()) {
					sites.add(new Site(Kind.ROCK, pos, null, state)); break;
				}
			}
		}
		for (int attempt = 0; attempt < 64; attempt++) {
			double angle = player.getRandom().nextDouble() * Math.PI * 2;
			double radius = 9 + player.getRandom().nextInt(12);
			BlockPos horizontal = base.offset((int) Math.round(Math.cos(angle) * radius), 0,
					(int) Math.round(Math.sin(angle) * radius));
			for (int dy = -3; dy <= 3; dy++) {
				BlockPos source = horizontal.above(dy);
				if (!standingSpace(level, source)) continue;
				Vec3 eye = Vec3.atCenterOf(source).add(0, .9, 0);
				boolean hidden = player.getLookAngle().dot(eye.subtract(player.getEyePosition()).normalize()) < .25
						|| !clear(level, player.getEyePosition(), eye);
				if (!hidden) continue;
				boolean roof = false;
				for (int up = 2; up <= 5; up++) roof |= level.getBlockState(source.above(up)).is(BlockTags.BASE_STONE_OVERWORLD);
				if (roof && sites.stream().noneMatch(s -> s.kind == Kind.CAVE)) sites.add(new Site(Kind.CAVE, source, null, null));
				if (severity >= 2 && sites.stream().noneMatch(s -> s.kind == Kind.JOCKEY)) sites.add(new Site(Kind.JOCKEY, source, null, null));
				if (severity >= 3 && sites.stream().noneMatch(s -> s.kind == Kind.ARROW)) {
					Vec3 direction = player.position().subtract(Vec3.atBottomCenterOf(source)).multiply(1, 0, 1).normalize();
					BlockPos charge = BlockPos.containing(Vec3.atBottomCenterOf(source).add(direction.scale(4)));
					if (standingSpace(level, charge) && clear(level, eye, Vec3.atCenterOf(charge).add(0, .3, 0))
							&& clear(level, Vec3.atCenterOf(charge).add(0, .3, 0), player.position().add(0, 1, 0)))
						sites.add(new Site(Kind.ARROW, source, charge, null));
				}
				break;
			}
		}
		if (severity >= 3 && PinkSheepMeteor.canStrike(player, level)
				&& (sites.isEmpty() || player.getRandom().nextBoolean())) {
			return PinkSheepMeteor.strike(player, level);
		}
		if (sites.isEmpty()) return false;
		// Highest severity prioritizes the complete explosive-arrow chain when its scene exists.
		List<Site> choice = severity >= 3 ? sites.stream().filter(s -> s.kind == Kind.ARROW).toList() : List.of();
		if (choice.isEmpty()) choice = sites;
		Site chosen = choice.get(player.getRandom().nextInt(choice.size()));
		Incident incident = new Incident(player, chosen);
		PENDING.add(incident);
		cue(incident);
		return true;
	}

	public static void tick(MinecraftServer server) {
		for (Incident incident : List.copyOf(PENDING)) {
			if (incident.site.kind == Kind.ARROW && incident.arrow != null && incident.arrow.isAlive() && !incident.arrived) {
				Vec3 charge = Vec3.atCenterOf(incident.site.charge).add(0, .3, 0);
				if (incident.arrow.position().distanceToSqr(charge) < .7 * .7) {
					incident.arrived = true; incident.arrow.setDeltaMovement(Vec3.ZERO);
				}
			}
			boolean eligible = eligible(incident, server);
			boolean sourceAlive = !incident.failed && sourcesValid(incident);
			boolean ready = incident.site.kind != Kind.ARROW || incident.arrived;
			switch (incident.sequence.tick(incident.level.getGameTime(), eligible, sourceAlive, ready)) {
				case CREATE_SOURCE -> { createSource(incident); incident.created = true; }
				case RELEASE -> { release(incident); PENDING.remove(incident); }
				case CANCEL -> { cleanup(incident); PENDING.remove(incident); }
				case NONE -> { }
			}
		}
	}

	private static boolean eligible(Incident i, MinecraftServer server) {
		return server.getLevel(i.level.dimension()) == i.level && PinkSheepGameRules.enabled(server)
				&& server.getPlayerList().getPlayer(i.owner.getUUID()) == i.owner && i.owner.isAlive()
				&& !i.owner.isCreative() && !i.owner.isSpectator() && i.owner.level() == i.level
				&& i.owner.distanceToSqr(i.aim) < 48 * 48 && loaded(i.level, Vec3.atCenterOf(i.site.source), i.aim)
				&& (i.site.charge == null || i.level.hasChunkAt(i.site.charge));
	}
	private static boolean sourcesValid(Incident i) {
		if (i.site.kind == Kind.ROCK) return i.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
				&& i.level.getBlockState(i.site.source).equals(i.site.original) && i.level.getBlockState(i.site.source.below()).isAir();
		if (!i.created) return standingSpace(i.level, i.site.source)
				&& (i.site.charge == null || standingSpace(i.level, i.site.charge));
		return !i.actors.isEmpty() && i.actors.stream().allMatch(e -> e.isAlive() && !e.isRemoved() && i.level.hasChunkAt(e.blockPosition()));
	}
	private static void cue(Incident i) {
		var sound = switch (i.site.kind) {
			case ROCK -> SoundEvents.GRAVEL_STEP;
			case JOCKEY -> SoundEvents.CHICKEN_AMBIENT;
			case CAVE -> SoundEvents.ZOMBIE_AMBIENT;
			case ARROW -> SoundEvents.SKELETON_AMBIENT;
		};
		i.level.playSound(null, i.site.source, sound, SoundSource.HOSTILE, 1.2f, .8f);
	}
	private static void createSource(Incident i) {
		if (!sourcesValid(i)) { i.failed = true; return; }
		if (i.site.kind == Kind.ROCK) {
			Vec3 p = Vec3.atBottomCenterOf(i.site.source);
			i.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, i.site.original), p.x, p.y, p.z, 18, .3, .2, .3, .01);
			return;
		}
		Vec3 sourceEye = Vec3.atCenterOf(i.site.source).add(0, .9, 0);
		if (i.level.players().stream().anyMatch(player -> !player.isSpectator()
				&& player.distanceToSqr(sourceEye) < 64 * 64
				&& player.getLookAngle().dot(sourceEye.subtract(player.getEyePosition()).normalize()) > .25
				&& clear(i.level, player.getEyePosition(), sourceEye))) { i.failed = true; return; }
		if (i.site.kind == Kind.ARROW) { createArrowSource(i); return; }
		Zombie zombie = EntityType.ZOMBIE.create(i.level);
		if (zombie == null) { i.failed = true; return; }
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i.site.kind == Kind.CAVE ? Items.IRON_SWORD : Items.NETHERITE_SWORD));
		zombie.setNoAi(true);
		zombie.moveTo(Vec3.atBottomCenterOf(i.site.source));
		if (i.site.kind == Kind.JOCKEY) {
			Chicken chicken = EntityType.CHICKEN.create(i.level);
			if (chicken == null) { i.failed = true; return; }
			chicken.moveTo(zombie.position()); chicken.setNoAi(true); zombie.setBaby(true);
			if (!add(i, chicken) || !add(i, zombie) || !zombie.startRiding(chicken)) { i.failed = true; return; }
		} else if (!add(i, zombie)) i.failed = true;
	}
	private static void createArrowSource(Incident i) {
		Skeleton skeleton = EntityType.SKELETON.create(i.level);
		PrimedTnt tnt = EntityType.TNT.create(i.level);
		Arrow arrow = EntityType.ARROW.create(i.level);
		if (skeleton == null || tnt == null || arrow == null) { i.failed = true; return; }
		skeleton.moveTo(Vec3.atBottomCenterOf(i.site.source)); skeleton.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		tnt.moveTo(Vec3.atBottomCenterOf(i.site.charge)); tnt.setFuse(200); tnt.setNoGravity(true);
		tnt.setDeltaMovement(Vec3.ZERO);
		arrow.setOwner(skeleton); arrow.setPos(skeleton.getX(), skeleton.getEyeY(), skeleton.getZ());
		arrow.setNoGravity(true); arrow.setBaseDamage(0);
		arrow.getPersistentData().putBoolean(PREPARING_ARROW, true);
		arrow.setDeltaMovement(Vec3.atCenterOf(i.site.charge).add(0, .3, 0).subtract(arrow.position()).normalize().scale(.45));
		if (!add(i, skeleton) || !add(i, tnt) || !add(i, arrow)) { i.failed = true; return; }
		i.arrow = arrow; i.charge = tnt;
		i.level.playSound(null, i.site.source, SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1, 1);
		i.level.playSound(null, i.site.charge, SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1, 1);
	}
	private static boolean add(Incident i, Entity entity) {
		if (!i.level.addFreshEntity(entity)) return false;
		i.actors.add(entity); return true;
	}
	private static void release(Incident i) {
		if (i.site.kind == Kind.ROCK) {
			FallingBlockEntity rock = FallingBlockEntity.fall(i.level, i.site.source, i.site.original);
			rock.setHurtsEntities(4, 40);
			return;
		}
		if (i.site.kind == Kind.ARROW) {
			Vec3 blast = i.charge.position();
			// Real blast at the visible charge. Keep the arrow alive so the same arrow is accelerated.
			i.charge.discard();
			i.arrow.setInvulnerable(true);
			i.level.explode(null, blast.x, blast.y, blast.z, 2f, false, Level.ExplosionInteraction.NONE);
			i.arrow.setInvulnerable(false);
			i.arrow.getPersistentData().remove(PREPARING_ARROW);
			i.arrow.setBaseDamage(12);
			i.arrow.setNoGravity(false);
			i.arrow.setDeltaMovement(i.aim.subtract(i.arrow.position()).normalize().scale(4));
			i.arrow.hurtMarked = true;
		}
		for (Entity entity : i.actors) if (entity instanceof Mob mob && mob.isAlive()) {
			mob.setNoAi(false);
			if (mob instanceof Zombie || mob instanceof Skeleton) mob.setTarget(i.owner);
			mob.getNavigation().moveTo(i.aim.x, i.aim.y, i.aim.z, 1.2);
		}
	}
	/** Before acceleration, hitting anything except the charge interrupts this chain. */
	public static boolean onArrowImpact(Entity projectile, HitResult hit) {
		if (!projectile.getPersistentData().getBoolean(PREPARING_ARROW)) return false;
		for (Incident i : PENDING) if (i.arrow == projectile) {
			if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == i.charge) {
				i.arrived = true; i.arrow.setDeltaMovement(Vec3.ZERO);
			} else { i.failed = true; projectile.discard(); }
			return true;
		}
		projectile.discard(); return true;
	}
	private static void cleanup(Incident i) { i.actors.forEach(Entity::discard); }
	@SubscribeEvent public static void onDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
		for (Incident i : PENDING) if (i.owner == event.getEntity()) i.failed = true;
	}
	@SubscribeEvent public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
		PENDING.forEach(PinkSheepAccidents::cleanup); PENDING.clear();
	}
	private static boolean standingSpace(ServerLevel level, BlockPos pos) {
		return level.isInWorldBounds(pos.above(2)) && level.hasChunkAt(pos)
				&& level.getBlockState(pos.below()).isSolid() && level.getBlockState(pos).isAir()
				&& level.getBlockState(pos.above()).isAir() && level.noCollision(new AABB(pos).expandTowards(0, 1, 0));
	}
	static boolean loaded(ServerLevel level, Vec3 from, Vec3 to) {
		int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 4));
		for (int step = 0; step <= steps; step++)
			if (!level.hasChunkAt(BlockPos.containing(from.lerp(to, (double) step / steps)))) return false;
		return true;
	}
	private static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
		return loaded(level, from, to) && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
	}
}
