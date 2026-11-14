package net.mcreator.mcanomalyarchives.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import net.mcreator.mcanomalyarchives.anomaly.nametag.NamedMaterialRules;

/** A finite stone puddle, not an infinite fluid source. Children inherit age and bounded spread depth. */
public final class LiquidCobblestoneBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, NamedMaterialRules.LIQUID_LAST_AGE);
    public static final IntegerProperty DEPTH = IntegerProperty.create("depth", 0, 3);
    public static final int STEP_TICKS = NamedMaterialRules.LIQUID_STEP_TICKS;
    public LiquidCobblestoneBlock() {
        super(BlockBehaviour.Properties.of().strength(.5f).noCollission().noOcclusion().noLootTable());
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(DEPTH, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE, DEPTH); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return box(0,0,0,16,3,16); }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, STEP_TICKS);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE), depth = state.getValue(DEPTH);
        int nextAge = NamedMaterialRules.nextLiquidAge(age);
        if (nextAge < 0) { level.removeBlock(pos, false); return; }
        BlockState next = state.setValue(AGE, nextAge);
        if (!level.setBlock(pos, next, Block.UPDATE_ALL)) { level.scheduleTick(pos, this, STEP_TICKS); return; }
        level.scheduleTick(pos, this, STEP_TICKS);
        if (depth >= 3) return;
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos target = pos.relative(direction);
            if (!level.hasChunkAt(target) || !level.isInWorldBounds(target) || !level.getBlockState(target).isAir()) continue;
            if (direction != Direction.DOWN && !level.getBlockState(target.below()).isFaceSturdy(level, target.below(), Direction.UP)) continue;
            level.setBlock(target, next.setValue(DEPTH, depth+1), Block.UPDATE_ALL);
        }
    }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        entity.makeStuckInBlock(state, new Vec3(.05, .01, .05));
        if (entity instanceof net.minecraft.server.level.ServerPlayer player)
            net.mcreator.mcanomalyarchives.codex.CodexObservations.record(player, "name_liquid_stone");
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) level.addParticle(new net.minecraft.core.particles.BlockParticleOption(
                net.minecraft.core.particles.ParticleTypes.FALLING_DUST, Blocks.COBBLESTONE.defaultBlockState()),
                pos.getX()+random.nextDouble(), pos.getY()+.35, pos.getZ()+random.nextDouble(), 0, .02, 0);
    }
}
