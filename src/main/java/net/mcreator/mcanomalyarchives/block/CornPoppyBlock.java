package net.mcreator.mcanomalyarchives.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.mcreator.mcanomalyarchives.anomaly.poppy.PoppyAngerData;
import java.util.Map;

public class CornPoppyBlock extends Block {
	public static final BooleanProperty ANGRY = BooleanProperty.create("angry");
	
	private static PoppyAngerData data(Level level) {
		return PoppyAngerData.get((net.minecraft.server.level.ServerLevel) level);
	}
	private static final long ANGRY_TRIGGER_TIME = 1200;
	private static final long ANGRY_DURATION = 1200;
	private static final int ANGRY_RANGE = 128;

	public CornPoppyBlock() {
		super(BlockBehaviour.Properties.of().sound(SoundType.GRAVEL).strength(-1, 3600000).noCollission().noOcclusion().randomTicks().isRedstoneConductor((bs, br, bp) -> false));
		this.registerDefaultState(this.stateDefinition.any().setValue(ANGRY, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ANGRY);
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.getBlock() != newState.getBlock()) {
			// 转入悲伤花是同一段生命周期，保留恢复计时。
			if (!world.isClientSide) {
				data(world).state().onReplacement(GlobalPos.of(world.dimension(), pos),
						newState.getBlock() == net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks.SAD_POPPY.get());
				data(world).setDirty();
			}
			super.onRemove(state, world, pos, newState, isMoving);
		}
	}

	public static void setLastWatchedTime(Level level, BlockPos pos, long time) {
		GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
		data(level).state().watch(key, time);
		data(level).setDirty();
	}

	public static void clearAngryState(Level level, BlockPos pos) {
		data(level).state().calm(GlobalPos.of(level.dimension(), pos));
		data(level).setDirty();
	}

	public static long getLastWatchedTime(Level level, BlockPos pos) {
		return data(level).state().lastWatched(GlobalPos.of(level.dimension(), pos));
	}

	public static boolean getHasBeenWatched(Level level, BlockPos pos) {
		return data(level).state().wasWatched(GlobalPos.of(level.dimension(), pos));
	}

	public static Long getAngryEndTime(Level level, BlockPos pos) {
		return data(level).state().angryUntil(GlobalPos.of(level.dimension(), pos));
	}

	public static long getAngryTriggerTime() {
		return ANGRY_TRIGGER_TIME;
	}

	public static long getAngryDuration() {
		return ANGRY_DURATION;
	}

	public static int getAngryRange() {
		return ANGRY_RANGE;
	}

	public static void putAngryEndTime(Level level, BlockPos pos, long time) {
		data(level).state().becomeAngry(GlobalPos.of(level.dimension(), pos.immutable()), time);
		data(level).setDirty();
	}

	public static void removeFromAllMaps(Level level, BlockPos pos) {
		removeFromAllMaps(level, GlobalPos.of(level.dimension(), pos));
	}

	public static void removeFromAllMaps(Level level, GlobalPos pos) {
		data(level).state().remove(pos);
		data(level).setDirty();
	}

	public static Map<GlobalPos, Long> getLastWatchedTimeMap(Level level) {
		return data(level).state().watchTimes();
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
		return true;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
		return 0;
	}

	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}
}
