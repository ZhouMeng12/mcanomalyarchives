package net.mcreator.mcanomalyarchives.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.mcreator.mcanomalyarchives.codex.CodexObservations;
import net.mcreator.mcanomalyarchives.anomaly.negative.NegativeSpaceLifetime;

/** A temporary absence left by negative blocks. Scheduled ticks persist through chunk saving. */
public final class NegativeSpaceBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, NegativeSpaceLifetime.LAST_AGE);
    public static final IntegerProperty MATERIAL = IntegerProperty.create("material", 0, 2);
    public NegativeSpaceBlock() {
        super(BlockBehaviour.Properties.of().strength(-1, 3600000).noCollission().noOcclusion().noLootTable());
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(MATERIAL, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE, MATERIAL); }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, NegativeSpaceLifetime.STEP_TICKS);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int next = NegativeSpaceLifetime.nextAge(state.getValue(AGE));
        if (next < 0) {
            if (level.removeBlock(pos, false)) returnMaterial(level, pos, state.getValue(MATERIAL), random);
            return;
        }
        level.setBlock(pos, state.setValue(AGE, next), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, NegativeSpaceLifetime.STEP_TICKS);
    }
    private static void returnMaterial(ServerLevel level, BlockPos origin, int material, RandomSource random) {
        Block ordinary = switch (material) {
            case 1 -> net.minecraft.world.level.block.Blocks.OAK_LOG;
            case 2 -> net.minecraft.world.level.block.Blocks.OAK_PLANKS;
            default -> net.minecraft.world.level.block.Blocks.DIRT;
        };
        for (int attempt = 0; attempt < 128; attempt++) {
            BlockPos target = origin.offset(random.nextInt(33) - 16, random.nextInt(17) - 8, random.nextInt(33) - 16);
            if (target.equals(origin) || !level.hasChunkAt(target) || !level.isInWorldBounds(target)
                    || !level.getBlockState(target).isAir() || level.canSeeSky(target)
                    || !level.getBlockState(target.below()).isFaceSturdy(level, target.below(), net.minecraft.core.Direction.UP)
                    || !level.getEntities(null, new net.minecraft.world.phys.AABB(target)).isEmpty()) continue;
            if (level.setBlock(target, ordinary.defaultBlockState(), Block.UPDATE_ALL)) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, target.getX()+.5, target.getY()+.5, target.getZ()+.5, 16, .4, .4, .4, .03);
                for (var player : level.players()) if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) < 24*24)
                    CodexObservations.record(player, "negative_block_return");
            }
            return;
        }
    }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || !living.isAlive()
                || (entity instanceof Player player && (player.isCreative() || player.isSpectator()))) return;
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) CodexObservations.record(player, "negative_space_contact");
        living.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
    }
}
