package net.mcreator.mcanomalyarchives.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

import net.mcreator.mcanomalyarchives.procedures.ComputerShuBiaoYouJianDanJiFangKuaiShiProcedure;

import com.google.common.collect.ImmutableMap;

public class ComputerBlock extends Block {
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	private final ImmutableMap<BlockState, VoxelShape> shapes = this.makeShapes();

	public ComputerBlock() {
		super(BlockBehaviour.Properties.of().strength(1f, 10f).noOcclusion().isRedstoneConductor((bs, br, bp) -> false));
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, true));
	}

	private ImmutableMap<BlockState, VoxelShape> makeShapes() {
		return this.getShapeForEachState(state -> {
			return switch (state.getValue(FACING)) {
				default -> Shapes.or(box(1, 0, 7, 11, 2, 9), box(1, 0, 10, 11, 1, 12), box(7, 1, 3, 9, 9, 4), box(7, 8, 4, 9, 10, 5), box(0, 4, 5, 16, 13, 7), box(5, 0, 2, 11, 1, 5), box(13, 0, 8, 15, 1, 11));
				case NORTH -> Shapes.or(box(5, 0, 7, 15, 2, 9), box(5, 0, 4, 15, 1, 6), box(7, 1, 12, 9, 9, 13), box(7, 8, 11, 9, 10, 12), box(0, 4, 9, 16, 13, 11), box(5, 0, 11, 11, 1, 14), box(1, 0, 5, 3, 1, 8));
				case EAST -> Shapes.or(box(7, 0, 5, 9, 2, 15), box(10, 0, 5, 12, 1, 15), box(3, 1, 7, 4, 9, 9), box(4, 8, 7, 5, 10, 9), box(5, 4, 0, 7, 13, 16), box(2, 0, 5, 5, 1, 11), box(8, 0, 1, 11, 1, 3));
				case WEST -> Shapes.or(box(7, 0, 1, 9, 2, 11), box(4, 0, 1, 6, 1, 11), box(12, 1, 7, 13, 9, 9), box(11, 8, 7, 12, 10, 9), box(9, 4, 0, 11, 13, 16), box(11, 0, 5, 14, 1, 11), box(5, 0, 13, 8, 1, 15));
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
		builder.add(FACING, OPEN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return super.getStateForPlacement(context).setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(OPEN, true);
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
		ComputerShuBiaoYouJianDanJiFangKuaiShiProcedure.execute(world, x, y, z, blockstate);
		return InteractionResult.SUCCESS;
	}
}