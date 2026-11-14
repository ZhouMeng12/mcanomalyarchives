package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.mcreator.mcanomalyarchives.anomaly.nametag.effects.EntityNaming;
import net.mcreator.mcanomalyarchives.network.NamedTransformPacket;

public final class PotatoTransformation {
    public static final int DURATION = NamedMaterialRules.POTATO_TICKS;
    private static final String YIELD = "McanomalyPotatoYield";
    private PotatoTransformation() { }
    public static void start(ServerLevel level, LivingEntity entity) {
        NamedState.apply(entity, new ResolvedName(ResolvedName.Kind.ITEM, ResourceLocation.withDefaultNamespace("potato")), "土豆", 0);
        entity.getPersistentData().putInt(YIELD, 1 + level.random.nextInt(3));
        NameTagTransform.start(entity, level.getGameTime(), DURATION);
        EntityNaming.loseVitality(entity);
        NamedTransformPacket.sendToWatchers(entity);
    }
    public static boolean tick(ServerLevel level, LivingEntity entity) {
        if (!entity.getPersistentData().contains(YIELD) || entity.isDeadOrDying()) return false;
        EntityNaming.loseVitality(entity);
        if (entity.tickCount % 20 != 0) return true;
        float progress = NameTagTransform.progressOf(entity, level.getGameTime());
        if (progress > .25f && progress < .75f)
            level.sendParticles(ParticleTypes.ASH, entity.getX(), entity.getY()+entity.getBbHeight()*.5, entity.getZ(), 3, .2, .25, .2, 0);
        if (progress >= .75f)
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY()+entity.getBbHeight(), entity.getZ(), 2, .15, .1, .15, 0);
        if (progress < 1) return true;
        var pos = entity.blockPosition();
        int count = Math.clamp(entity.getPersistentData().getInt(YIELD), 1, 3);
        entity.discard();
        for (var player : level.players()) if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 24*24)
            net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "name_potato");
        level.addFreshEntity(new ItemEntity(level, pos.getX()+.5, pos.getY()+.2, pos.getZ()+.5, new ItemStack(Items.POTATO, count)));
        // Sprouts remain young, and only appear on valid farmland without replacing anything.
        if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND))
            level.setBlockAndUpdate(pos, Blocks.POTATOES.defaultBlockState());
        return true;
    }
}
