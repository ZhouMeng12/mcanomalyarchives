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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class CornPoppyBlock extends Block {
	public static final BooleanProperty ANGRY = BooleanProperty.create("angry");
	
	private static final Map<BlockPos, Long> lastWatchedTime = new ConcurrentHashMap<>();
	private static final Map<BlockPos, Long> angryEndTime = new ConcurrentHashMap<>();
	private static final Map<BlockPos, Boolean> hasBeenWatched = new ConcurrentHashMap<>();
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
			removeFromAllMaps(pos);
			super.onRemove(state, world, pos, newState, isMoving);
		}
	}

	public static void setLastWatchedTime(BlockPos pos, long time) {
		lastWatchedTime.put(pos, time);
		hasBeenWatched.put(pos, true);
		angryEndTime.remove(pos);
	}

	public static void clearAngryState(BlockPos pos) {
		angryEndTime.remove(pos);
	}

	public static long getLastWatchedTime(BlockPos pos) {
		return lastWatchedTime.getOrDefault(pos, 0L);
	}

	public static boolean getHasBeenWatched(BlockPos pos) {
		return hasBeenWatched.getOrDefault(pos, false);
	}

	public static Long getAngryEndTime(BlockPos pos) {
		return angryEndTime.get(pos);
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

	public static void putAngryEndTime(BlockPos pos, long time) {
		angryEndTime.put(pos, time);
	}

	public static void removeFromAllMaps(BlockPos pos) {
		lastWatchedTime.remove(pos);
		angryEndTime.remove(pos);
		hasBeenWatched.remove(pos);
	}

	public static Map<BlockPos, Long> getLastWatchedTimeMap() {
		return lastWatchedTime;
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