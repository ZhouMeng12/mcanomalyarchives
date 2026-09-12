package net.mcreator.mcanomalyarchives.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

import net.mcreator.mcanomalyarchives.procedures.ChairShuBiaoYouJianDanJiFangKuaiShiProcedure;

import com.google.common.collect.ImmutableMap;

public class ChairBlock extends Block {
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty IS_SITTING = BooleanProperty.create("is_sitting");
	private final ImmutableMap<BlockState, VoxelShape> shapes = this.makeShapes();

	public ChairBlock() {
		super(BlockBehaviour.Properties.of().strength(1f, 10f).noOcclusion().isRedstoneConductor((bs, br, bp) -> false));
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(IS_SITTING, false));
	}

	private ImmutableMap<BlockState, VoxelShape> makeShapes() {
		return this.getShapeForEachState(state -> {
			return switch (state.getValue(FACING)) {
				default -> Shapes.or(box(13, 0, 1, 15, 2, 3), box(12, 1, 2, 14, 3, 4), box(2, 1, 2, 4, 3, 4), box(1, 0, 1, 3, 2, 3), box(11, 1, 3, 13, 3, 5), box(3, 1, 3, 5, 3, 5), box(10, 2, 4, 12, 4, 6), box(4, 2, 4, 6, 4, 6),
						box(9, 2, 5, 11, 4, 7), box(5, 2, 5, 7, 4, 7), box(8, 2, 6, 10, 4, 8), box(6, 2, 6, 8, 4, 8), box(6, 2, 6, 8, 4, 8), box(13, 0, 1, 15, 2, 3), box(12, 1, 2, 14, 3, 4), box(2, 1, 2, 4, 3, 4), box(1, 0, 1, 3, 2, 3),
						box(11, 1, 3, 13, 3, 5), box(3, 1, 3, 5, 3, 5), box(10, 2, 4, 12, 4, 6), box(4, 2, 4, 6, 4, 6), box(9, 2, 5, 11, 4, 7), box(5, 2, 5, 7, 4, 7), box(8, 2, 6, 10, 4, 8), box(8, 2, 8, 10, 4, 10), box(1, 0, 13, 3, 2, 15),
						box(2, 1, 12, 4, 3, 14), box(12, 1, 12, 14, 3, 14), box(13, 0, 13, 15, 2, 15), box(3, 1, 11, 5, 3, 13), box(11, 1, 11, 13, 3, 13), box(4, 2, 10, 6, 4, 12), box(10, 2, 10, 12, 4, 12), box(5, 2, 9, 7, 4, 11),
						box(9, 2, 9, 11, 4, 11), box(6, 2, 8, 8, 4, 10), box(7, 4, 7, 9, 9, 9), box(1, 7, 6, 15, 9, 10), box(3, 7, 10, 13, 9, 14), box(3, 7, 2, 13, 9, 6), box(2, 7, 4, 14, 9, 6), box(2, 7, 10, 14, 9, 12), box(6, 7, 1, 10, 9, 15),
						box(2, 9, 7, 14, 10, 9), box(4, 9, 11, 12, 10, 13), box(4, 9, 3, 12, 10, 5), box(3, 9, 5, 13, 10, 7), box(3, 9, 9, 13, 10, 11), box(7, 9, 2, 9, 10, 14), box(14, 9, 7, 15, 13, 9), box(1, 9, 7, 2, 13, 9),
						box(14, 13, 5, 16, 14, 14), box(0, 13, 5, 2, 14, 14), box(7, 9, 1, 9, 12, 2), box(7, 11, 0, 9, 16, 1), box(7, 16, 0, 9, 18, 2), box(4, 14, 2, 12, 24, 3), box(12, 14, 3, 13, 23, 4), box(13, 15, 3, 14, 21, 4),
						box(2, 15, 3, 3, 21, 4), box(3, 14, 3, 4, 23, 4), box(13, 15, 2, 14, 21, 3), box(12, 14, 2, 13, 23, 3), box(3, 14, 2, 4, 23, 3), box(2, 15, 2, 3, 21, 3), box(4, 14, 3, 12, 24, 4), box(12, 14, 3, 13, 23, 4),
						box(13, 15, 3, 14, 21, 4), box(2, 15, 3, 3, 21, 4), box(3, 14, 3, 4, 23, 4));
				case NORTH -> Shapes.or(box(1, 0, 13, 3, 2, 15), box(2, 1, 12, 4, 3, 14), box(12, 1, 12, 14, 3, 14), box(13, 0, 13, 15, 2, 15), box(3, 1, 11, 5, 3, 13), box(11, 1, 11, 13, 3, 13), box(4, 2, 10, 6, 4, 12), box(10, 2, 10, 12, 4, 12),
						box(5, 2, 9, 7, 4, 11), box(9, 2, 9, 11, 4, 11), box(6, 2, 8, 8, 4, 10), box(8, 2, 8, 10, 4, 10), box(8, 2, 8, 10, 4, 10), box(1, 0, 13, 3, 2, 15), box(2, 1, 12, 4, 3, 14), box(12, 1, 12, 14, 3, 14), box(13, 0, 13, 15, 2, 15),
						box(3, 1, 11, 5, 3, 13), box(11, 1, 11, 13, 3, 13), box(4, 2, 10, 6, 4, 12), box(10, 2, 10, 12, 4, 12), box(5, 2, 9, 7, 4, 11), box(9, 2, 9, 11, 4, 11), box(6, 2, 8, 8, 4, 10), box(6, 2, 6, 8, 4, 8), box(13, 0, 1, 15, 2, 3),
						box(12, 1, 2, 14, 3, 4), box(2, 1, 2, 4, 3, 4), box(1, 0, 1, 3, 2, 3), box(11, 1, 3, 13, 3, 5), box(3, 1, 3, 5, 3, 5), box(10, 2, 4, 12, 4, 6), box(4, 2, 4, 6, 4, 6), box(9, 2, 5, 11, 4, 7), box(5, 2, 5, 7, 4, 7),
						box(8, 2, 6, 10, 4, 8), box(7, 4, 7, 9, 9, 9), box(1, 7, 6, 15, 9, 10), box(3, 7, 2, 13, 9, 6), box(3, 7, 10, 13, 9, 14), box(2, 7, 10, 14, 9, 12), box(2, 7, 4, 14, 9, 6), box(6, 7, 1, 10, 9, 15), box(2, 9, 7, 14, 10, 9),
						box(4, 9, 3, 12, 10, 5), box(4, 9, 11, 12, 10, 13), box(3, 9, 9, 13, 10, 11), box(3, 9, 5, 13, 10, 7), box(7, 9, 2, 9, 10, 14), box(1, 9, 7, 2, 13, 9), box(14, 9, 7, 15, 13, 9), box(0, 13, 2, 2, 14, 11),
						box(14, 13, 2, 16, 14, 11), box(7, 9, 14, 9, 12, 15), box(7, 11, 15, 9, 16, 16), box(7, 16, 14, 9, 18, 16), box(4, 14, 13, 12, 24, 14), box(3, 14, 12, 4, 23, 13), box(2, 15, 12, 3, 21, 13), box(13, 15, 12, 14, 21, 13),
						box(12, 14, 12, 13, 23, 13), box(2, 15, 13, 3, 21, 14), box(3, 14, 13, 4, 23, 14), box(12, 14, 13, 13, 23, 14), box(13, 15, 13, 14, 21, 14), box(4, 14, 12, 12, 24, 13), box(3, 14, 12, 4, 23, 13), box(2, 15, 12, 3, 21, 13),
						box(13, 15, 12, 14, 21, 13), box(12, 14, 12, 13, 23, 13));
				case EAST -> Shapes.or(box(1, 0, 1, 3, 2, 3), box(2, 1, 2, 4, 3, 4), box(2, 1, 12, 4, 3, 14), box(1, 0, 13, 3, 2, 15), box(3, 1, 3, 5, 3, 5), box(3, 1, 11, 5, 3, 13), box(4, 2, 4, 6, 4, 6), box(4, 2, 10, 6, 4, 12),
						box(5, 2, 5, 7, 4, 7), box(5, 2, 9, 7, 4, 11), box(6, 2, 6, 8, 4, 8), box(6, 2, 8, 8, 4, 10), box(6, 2, 8, 8, 4, 10), box(1, 0, 1, 3, 2, 3), box(2, 1, 2, 4, 3, 4), box(2, 1, 12, 4, 3, 14), box(1, 0, 13, 3, 2, 15),
						box(3, 1, 3, 5, 3, 5), box(3, 1, 11, 5, 3, 13), box(4, 2, 4, 6, 4, 6), box(4, 2, 10, 6, 4, 12), box(5, 2, 5, 7, 4, 7), box(5, 2, 9, 7, 4, 11), box(6, 2, 6, 8, 4, 8), box(8, 2, 6, 10, 4, 8), box(13, 0, 13, 15, 2, 15),
						box(12, 1, 12, 14, 3, 14), box(12, 1, 2, 14, 3, 4), box(13, 0, 1, 15, 2, 3), box(11, 1, 11, 13, 3, 13), box(11, 1, 3, 13, 3, 5), box(10, 2, 10, 12, 4, 12), box(10, 2, 4, 12, 4, 6), box(9, 2, 9, 11, 4, 11),
						box(9, 2, 5, 11, 4, 7), box(8, 2, 8, 10, 4, 10), box(7, 4, 7, 9, 9, 9), box(6, 7, 1, 10, 9, 15), box(10, 7, 3, 14, 9, 13), box(2, 7, 3, 6, 9, 13), box(4, 7, 2, 6, 9, 14), box(10, 7, 2, 12, 9, 14), box(1, 7, 6, 15, 9, 10),
						box(7, 9, 2, 9, 10, 14), box(11, 9, 4, 13, 10, 12), box(3, 9, 4, 5, 10, 12), box(5, 9, 3, 7, 10, 13), box(9, 9, 3, 11, 10, 13), box(2, 9, 7, 14, 10, 9), box(7, 9, 1, 9, 13, 2), box(7, 9, 14, 9, 13, 15),
						box(5, 13, 0, 14, 14, 2), box(5, 13, 14, 14, 14, 16), box(1, 9, 7, 2, 12, 9), box(0, 11, 7, 1, 16, 9), box(0, 16, 7, 2, 18, 9), box(2, 14, 4, 3, 24, 12), box(3, 14, 3, 4, 23, 4), box(3, 15, 2, 4, 21, 3),
						box(3, 15, 13, 4, 21, 14), box(3, 14, 12, 4, 23, 13), box(2, 15, 2, 3, 21, 3), box(2, 14, 3, 3, 23, 4), box(2, 14, 12, 3, 23, 13), box(2, 15, 13, 3, 21, 14), box(3, 14, 4, 4, 24, 12), box(3, 14, 3, 4, 23, 4),
						box(3, 15, 2, 4, 21, 3), box(3, 15, 13, 4, 21, 14), box(3, 14, 12, 4, 23, 13));
				case WEST -> Shapes.or(box(13, 0, 13, 15, 2, 15), box(12, 1, 12, 14, 3, 14), box(12, 1, 2, 14, 3, 4), box(13, 0, 1, 15, 2, 3), box(11, 1, 11, 13, 3, 13), box(11, 1, 3, 13, 3, 5), box(10, 2, 10, 12, 4, 12), box(10, 2, 4, 12, 4, 6),
						box(9, 2, 9, 11, 4, 11), box(9, 2, 5, 11, 4, 7), box(8, 2, 8, 10, 4, 10), box(8, 2, 6, 10, 4, 8), box(8, 2, 6, 10, 4, 8), box(13, 0, 13, 15, 2, 15), box(12, 1, 12, 14, 3, 14), box(12, 1, 2, 14, 3, 4), box(13, 0, 1, 15, 2, 3),
						box(11, 1, 11, 13, 3, 13), box(11, 1, 3, 13, 3, 5), box(10, 2, 10, 12, 4, 12), box(10, 2, 4, 12, 4, 6), box(9, 2, 9, 11, 4, 11), box(9, 2, 5, 11, 4, 7), box(8, 2, 8, 10, 4, 10), box(6, 2, 8, 8, 4, 10), box(1, 0, 1, 3, 2, 3),
						box(2, 1, 2, 4, 3, 4), box(2, 1, 12, 4, 3, 14), box(1, 0, 13, 3, 2, 15), box(3, 1, 3, 5, 3, 5), box(3, 1, 11, 5, 3, 13), box(4, 2, 4, 6, 4, 6), box(4, 2, 10, 6, 4, 12), box(5, 2, 5, 7, 4, 7), box(5, 2, 9, 7, 4, 11),
						box(6, 2, 6, 8, 4, 8), box(7, 4, 7, 9, 9, 9), box(6, 7, 1, 10, 9, 15), box(2, 7, 3, 6, 9, 13), box(10, 7, 3, 14, 9, 13), box(10, 7, 2, 12, 9, 14), box(4, 7, 2, 6, 9, 14), box(1, 7, 6, 15, 9, 10), box(7, 9, 2, 9, 10, 14),
						box(3, 9, 4, 5, 10, 12), box(11, 9, 4, 13, 10, 12), box(9, 9, 3, 11, 10, 13), box(5, 9, 3, 7, 10, 13), box(2, 9, 7, 14, 10, 9), box(7, 9, 14, 9, 13, 15), box(7, 9, 1, 9, 13, 2), box(2, 13, 14, 11, 14, 16),
						box(2, 13, 0, 11, 14, 2), box(14, 9, 7, 15, 12, 9), box(15, 11, 7, 16, 16, 9), box(14, 16, 7, 16, 18, 9), box(13, 14, 4, 14, 24, 12), box(12, 14, 12, 13, 23, 13), box(12, 15, 13, 13, 21, 14), box(12, 15, 2, 13, 21, 3),
						box(12, 14, 3, 13, 23, 4), box(13, 15, 13, 14, 21, 14), box(13, 14, 12, 14, 23, 13), box(13, 14, 3, 14, 23, 4), box(13, 15, 2, 14, 21, 3), box(12, 14, 4, 13, 24, 12), box(12, 14, 12, 13, 23, 13), box(12, 15, 13, 13, 21, 14),
						box(12, 15, 2, 13, 21, 3), box(12, 14, 3, 13, 23, 4));
			};
		});
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return shapes.get(state);
	}

	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, IS_SITTING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return super.getStateForPlacement(context).setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(IS_SITTING, false);
	}

	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	public BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
	}

	@Override
	public InteractionResult useWithoutItem(BlockState blockstate, Level world, BlockPos pos, Player entity, BlockHitResult hit) {
		super.useWithoutItem(blockstate, world, pos, entity, hit);
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		double hitX = hit.getLocation().x;
		double hitY = hit.getLocation().y;
		double hitZ = hit.getLocation().z;
		Direction direction = hit.getDirection();
		ChairShuBiaoYouJianDanJiFangKuaiShiProcedure.execute(world, x, y, z, blockstate, entity);
		return InteractionResult.SUCCESS;
	}
}