package net.mcreator.mcanomalyarchives.anomaly.poppy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.mcreator.mcanomalyarchives.block.CornPoppyBlock;
import net.mcreator.mcanomalyarchives.codex.CodexObservations;
import net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModMobEffects;
import net.mcreator.mcanomalyarchives.network.PoppyAddictionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Player-only state machine. NPC behaviour remains in the legacy listener. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "mcanomalyarchives")
public final class PoppyAddictionSystem {
    private static final String KEY = "mcanomalyarchives_poppy_addiction_v2";
    private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "poppy_movement_restriction");
    private static final ResourceLocation WEAKNESS = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "poppy_withdrawal_weakness");
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final class State {
        int ticks, away, sad, sadLevel, route = -1, entityId = -1;
        long seed;
        BlockPos flower;
        UUID cloud;
        String dimension;
        Vec3 anchor, lastBase;
        boolean wasActive;
    }
    private PoppyAddictionSystem() {}

    public static void tick(MinecraftServer server) {
        boolean update = server.getTickCount() % 5 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State s = STATES.computeIfAbsent(player.getUUID(), id -> load(player));
            if (!player.isAlive() || player.isSpectator()) {
                if (s.ticks > 0 || s.sad > 0 || s.wasActive) reset(player, s);
                continue;
            }
            if (s.dimension != null && !s.dimension.equals(player.level().dimension().location().toString())) {
                withdraw(player, s);
                s.flower = null; s.cloud = null; s.dimension = null;
            }
            if (update) {
                if (s.sad > 0 && !player.hasEffect(McanomalyarchivesModMobEffects.SAD)) {
                    if (s.ticks == 0) net.mcreator.mcanomalyarchives.codex.ArchiveAdvancements.award(player, "poppy_recover");
                    s.sad = 0; s.sadLevel = 0;
                }
                boolean valid = validTarget(player, s);
                int level = PoppyAddictionRules.level(s.ticks);
                boolean looking = valid && (PoppyAddictionRules.hardGaze(level) || lookingAtFocus(player, s));
                // An acquired source stays fixed throughout addiction, including giant-flower gaze.
                if (!looking && level == 0) looking = acquire(player, s);
                if (looking) {
                    boolean newSession=s.route<0;
                    s.route=0;
                    if(newSession)s.seed=player.getRandom().nextLong();
                    int oldLevel = level;
                    if (s.sad > 0) { s.ticks = 195; s.sad = 0; s.sadLevel = 0; player.removeEffect(McanomalyarchivesModMobEffects.SAD); }
                    var progress = PoppyAddictionRules.advance(s.ticks, s.away, true, 5);
                    s.ticks = progress.ticks(); s.away = progress.away();
                    level = PoppyAddictionRules.level(s.ticks);
                    if (s.flower != null) calm(player, s.flower);
                    if (level > 0) {
                        if (oldLevel == 0) {
                            CodexObservations.record(player, "poppy_addiction");
                            net.mcreator.mcanomalyarchives.McanomalyarchivesMod.LOGGER.debug(
                                    "Poppy session: player={}, resumed={}, source={}",
                                    player.getScoreboardName(),!newSession,s.cloud==null?"flower":"red_cloud");
                        }
                        player.addEffect(new MobEffectInstance(McanomalyarchivesModMobEffects.ADDICTE, 60, level - 1, false, true));
                        if (level >= 4 && s.anchor == null) s.anchor = player.position();
                        if (PoppyAddictionRules.lethal(s.ticks)) {
                            execute(player); reset(player, s); continue;
                        }
                    }
                } else if (level > 0) {
                    var progress = PoppyAddictionRules.advance(s.ticks, s.away, false, 5);
                    if (progress.withdrawal()) withdraw(player, s);
                    else s.away = progress.away();
                } else {
                    s.ticks = 0; s.flower = null; s.cloud = null;
                }
                if (s.sad > 0) {
                    s.sad = Math.max(0, s.sad - 5);
                    if (s.sad == 0 && s.ticks == 0) net.mcreator.mcanomalyarchives.codex.ArchiveAdvancements.award(player, "poppy_recover");
                }
                if (s.ticks == 0 && s.sad == 0) { s.route = -1; s.anchor = null; }
                save(player, s);
                sync(player, s);
            }
            restrict(player, s);
        }
    }

    private static boolean acquire(ServerPlayer player, State s) {
        Vec3 eye = player.getEyePosition();
        var hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(5)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        var block = player.level().getBlockState(hit.getBlockPos()).getBlock();
        if (hit.getType() == HitResult.Type.BLOCK && (block == McanomalyarchivesModBlocks.CORN_POPPY.get()
                || block == McanomalyarchivesModBlocks.SAD_POPPY.get())) {
            s.flower = hit.getBlockPos().immutable(); s.cloud = null; s.entityId = -1;
            s.dimension = player.level().dimension().location().toString();
            calm(player, s.flower); return true;
        }
        SmallStangeCloudEntity nearest = null;
        double distance = 24 * 24;
        for (var cloud : player.level().getEntitiesOfClass(SmallStangeCloudEntity.class, player.getBoundingBox().inflate(24))) {
            Vec3 delta = cloud.position().add(0, -0.33, 0).subtract(eye);
            if (cloud.isRed() && delta.lengthSqr() < distance && delta.normalize().dot(player.getLookAngle()) >= Math.cos(Math.toRadians(30))
                    && player.hasLineOfSight(cloud)) { nearest = cloud; distance = delta.lengthSqr(); }
        }
        if (nearest == null) return false;
        s.cloud = nearest.getUUID(); s.entityId = nearest.getId(); s.flower = null;
        s.dimension = player.level().dimension().location().toString(); return true;
    }

    private static boolean validTarget(ServerPlayer player, State s) {
        Vec3 target = source(player, s);
        if (target != null) s.lastBase = target;
        if (target == null || player.getEyePosition().distanceToSqr(target) > (s.cloud == null ? 36 : 625)) return false;
        var hit = player.level().clip(new ClipContext(player.getEyePosition(), s.cloud == null ? target.add(0, .5, 0) : target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || (s.flower != null && hit.getBlockPos().equals(s.flower));
    }

    private static Vec3 source(ServerPlayer player, State s) {
        if (s.cloud != null) {
            var entity = player.serverLevel().getEntity(s.cloud);
            if (!(entity instanceof SmallStangeCloudEntity cloud) || !cloud.isAlive() || !cloud.isRed()) return null;
            s.entityId = cloud.getId();
            return cloud.position().add(0, -0.33, 0);
        }
        if (s.flower == null || !player.serverLevel().hasChunkAt(s.flower)) return null;
        var block = player.level().getBlockState(s.flower).getBlock();
        if (block != McanomalyarchivesModBlocks.CORN_POPPY.get() && block != McanomalyarchivesModBlocks.SAD_POPPY.get()) return null;
        return Vec3.atLowerCornerOf(s.flower).add(0.5, 0, 0.5);
    }

    private static Vec3 focus(ServerPlayer player, State s) {
        Vec3 base = source(player, s);
        if (base == null || s.cloud != null) return base;
        return base.add(0, 0.5, 0);
    }

    private static boolean lookingAtFocus(ServerPlayer player, State s) {
        Vec3 target = focus(player, s);
        return target != null && target.subtract(player.getEyePosition()).normalize().dot(player.getLookAngle()) >= Math.cos(Math.toRadians(45));
    }

    private static void calm(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockState(pos).is(McanomalyarchivesModBlocks.SAD_POPPY.get()))
            player.level().setBlock(pos, McanomalyarchivesModBlocks.CORN_POPPY.get().defaultBlockState(), 3);
        CornPoppyBlock.setLastWatchedTime(player.level(), pos, player.level().getGameTime());
        CornPoppyBlock.clearAngryState(player.level(), pos);
    }

    private static void restrict(ServerPlayer player, State s) {
        int level = PoppyAddictionRules.level(s.ticks);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            if (s.sad > 0 && s.sadLevel > 0 && level == 0) {
                double amount = -4 * (1 + (s.sadLevel - 1) / 2);
                if (attack.getModifier(WEAKNESS) == null || attack.getModifier(WEAKNESS).amount() != amount) {
                    attack.removeModifier(WEAKNESS);
                    attack.addTransientModifier(new AttributeModifier(WEAKNESS, amount, AttributeModifier.Operation.ADD_VALUE));
                }
            } else attack.removeModifier(WEAKNESS);
        }
        if (level == 0 && s.sad == 0) { if (speed != null) speed.removeModifier(SPEED); return; }
        player.setSprinting(false);
        double factor = level == 0 ? Math.max(.25, 1 - .15 * s.sadLevel)
                : level < 4 ? new double[]{1, .35, .25, .15}[level] : .08;
        if (speed != null && (speed.getModifier(SPEED) == null || speed.getModifier(SPEED).amount() != factor - 1)) {
            speed.removeModifier(SPEED);
            speed.addTransientModifier(new AttributeModifier(SPEED, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (level >= 4 && s.anchor != null) {
            player.stopRiding();
            double radius = level >= 7 ? 0 : .5;
            Vec3 offset = player.position().subtract(s.anchor).multiply(1, 0, 1);
            Vec3 desired = s.anchor.add(offset.length() > radius ? offset.normalize().scale(radius) : offset);
            Vec3 correction = new Vec3(desired.x - player.getX(), 0, desired.z - player.getZ());
            if (correction.lengthSqr() > 1.0e-6 && player.level().noCollision(player, player.getBoundingBox().move(correction)))
                player.connection.teleport(desired.x, player.getY(), desired.z, player.getYRot(), player.getXRot());
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(level >= 7 ? new Vec3(0, Math.min(0, velocity.y), 0) : new Vec3(velocity.x, Math.min(0, velocity.y), velocity.z));
        }
        Vec3 target = focus(player, s);
        if (level >= 4 && target != null) {
            Vec3 d = target.subtract(player.getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90;
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
            float limit = level >= 7 ? 0 : new float[]{12, 8, 5}[level - 4];
            player.setYRot(yaw + Mth.clamp(Mth.wrapDegrees(player.getYRot() - yaw), -limit, limit));
            player.setXRot(Mth.clamp(pitch + Mth.clamp(player.getXRot() - pitch, -limit, limit), -90, 90));
        }
    }

    private static void withdraw(ServerPlayer player, State s) {
        int level = PoppyAddictionRules.level(s.ticks);
        if (level > 0) {
            s.sad = 600 + level * 200;
            s.sadLevel = Math.min(5, level);
            player.removeEffect(McanomalyarchivesModMobEffects.ADDICTE);
            player.addEffect(new MobEffectInstance(McanomalyarchivesModMobEffects.SAD, s.sad, Math.min(5, level) - 1, false, true));
            // Own modifiers avoid leaving withdrawal slowness behind when looking again,
            // and never remove potion effects supplied by another source.
            CodexObservations.record(player, "poppy_withdrawal");
        }
        s.ticks = 0; s.away = 0; s.anchor = null;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED);
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(WEAKNESS);
    }

    private static void execute(ServerPlayer player) {
        var damage = player.damageSources().source(ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "braindamage")));
        var advancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "watchdeath"));
        if (advancement != null) player.getAdvancements().award(advancement, "watchdeath_0");
        player.hurt(damage, Float.MAX_VALUE);
        if (player.isAlive()) {
            // die() sends the death screen but does not zero ServerPlayer health.
            // Vanilla rejects PERFORM_RESPAWN while health is still positive.
            player.setHealth(0);
            player.die(damage);
        }
    }

    private static void reset(ServerPlayer player, State s) {
        player.removeEffect(McanomalyarchivesModMobEffects.ADDICTE);
        player.removeEffect(McanomalyarchivesModMobEffects.SAD);
        s.ticks = 0; s.sad = 0; s.sadLevel = 0; s.away = 0; s.route = -1;
        s.anchor = null; s.flower = null; s.cloud = null; s.dimension = null; s.entityId = -1;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED);
        player.getPersistentData().remove(KEY); sync(player, s);
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(WEAKNESS);
    }

    private static void sync(ServerPlayer player, State s) {
        boolean active = s.ticks >= 200 || s.sad > 0;
        if (!active && !s.wasActive) return;
        Vec3 base = source(player, s);
        if (base == null) base = s.lastBase == null ? player.position() : s.lastBase;
        PacketDistributor.sendToPlayer(player, new PoppyAddictionPacket(s.ticks, s.route, s.sad,
                base.x, base.y, base.z, s.cloud == null ? -1 : s.entityId, s.seed));
        s.wasActive = active;
    }

    private static void save(ServerPlayer player, State s) {
        if (s.ticks < 200 && s.sad == 0) { player.getPersistentData().remove(KEY); return; }
        CompoundTag tag = new CompoundTag();
        tag.putInt("Ticks", s.ticks); tag.putInt("Sad", s.sad); tag.putInt("SadLevel", s.sadLevel); tag.putInt("Route", s.route);
        tag.putLong("Seed", s.seed); tag.putString("Dimension", player.level().dimension().location().toString());
        if (s.flower != null) tag.putLong("Flower", s.flower.asLong());
        if (s.cloud != null) tag.putUUID("Cloud", s.cloud);
        if (s.anchor != null) { tag.putDouble("AX", s.anchor.x); tag.putDouble("AY", s.anchor.y); tag.putDouble("AZ", s.anchor.z); }
        player.getPersistentData().put(KEY, tag);
    }

    private static State load(ServerPlayer player) {
        State s = new State(); CompoundTag tag = player.getPersistentData().getCompound(KEY);
        player.getPersistentData().remove("PoppyVisualOverride");
        s.ticks = Mth.clamp(tag.getInt("Ticks"), 0, 2200); s.sad = Mth.clamp(tag.getInt("Sad"), 0, 2600);
        s.sadLevel = Mth.clamp(tag.getInt("SadLevel"), 0, 5);
        s.route = s.ticks>0||s.sad>0?0:-1; // Migrate saved giant sessions to the sole remaining visual.
        s.seed = tag.getLong("Seed"); s.dimension = tag.contains("Dimension") ? tag.getString("Dimension") : null;
        if (tag.contains("Flower")) s.flower = BlockPos.of(tag.getLong("Flower"));
        if (tag.hasUUID("Cloud")) s.cloud = tag.getUUID("Cloud");
        if (tag.contains("AX")) s.anchor = new Vec3(tag.getDouble("AX"), tag.getDouble("AY"), tag.getDouble("AZ"));
        return s;
    }
    public static void leave(ServerPlayer player) {
        State s = STATES.remove(player.getUUID());
        if (s != null && player.isAlive()) save(player, s);
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(WEAKNESS);
    }
    public static void clear() { STATES.clear(); }

    @net.neoforged.bus.api.SubscribeEvent
    public static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State s = STATES.remove(player.getUUID());
            if (s != null) reset(player, s);
            player.getPersistentData().remove(KEY);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        com.mojang.brigadier.Command<net.minecraft.commands.CommandSourceStack> status=ctx->{
            ServerPlayer player=ctx.getSource().getPlayerOrException();
            State active=STATES.get(player.getUUID());
            int ticks=active==null?0:active.ticks,sad=active==null?0:active.sad;
            String current=ticks>0?(active.cloud!=null?"红云（无花形幻觉）":"边缘生长"):sad>0?"戒断中":"未观赏";
            ctx.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(
                    "当前："+current+"；累计观赏 "+(ticks/20f)+" 秒，上瘾 "+PoppyAddictionRules.level(ticks)
                    +" 级；戒断剩余 "+(sad/20f)+" 秒；已仅保留边缘生长。"),false);
            return 1;
        };
        var root=net.minecraft.commands.Commands.literal("poppyvisual").requires(source->source.hasPermission(2)).executes(status);
        root.then(net.minecraft.commands.Commands.literal("status").executes(status));
        event.getDispatcher().register(root);
    }
}
