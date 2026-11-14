package net.mcreator.mcanomalyarchives.anomaly.negative;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.init.UO007Items;
import net.mcreator.mcanomalyarchives.runtime.ServerTasks;

/** Implements the restrained UO-007 stone-only replacement and cave reappearance loop. */
@EventBusSubscriber(modid = McanomalyarchivesMod.MODID)
public final class NegativeDiamondPickaxeHandler {
    private static final List<Block> REPLACEMENTS = List.of(
            Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF,
            Blocks.CALCITE, Blocks.DIRT, Blocks.MOSS_BLOCK, Blocks.RED_WOOL, Blocks.BLUE_WOOL,
            Blocks.ICE, Blocks.OBSIDIAN, Blocks.PRISMARINE, Blocks.GLASS);

	private NegativeDiamondPickaxeHandler() { }

	@SubscribeEvent
	public static void onNaturalStoneBroken(BlockEvent.BreakEvent event) {
		Player player = event.getPlayer();
		if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) return;
		if (!(player.getMainHandItem().getItem() instanceof net.mcreator.mcanomalyarchives.item.NegativeDiamondPickaxeItem)
				|| !event.getState().is(BlockTags.BASE_STONE_OVERWORLD)) return;

		BlockPos origin = event.getPos().immutable();
		BlockState brokenState = event.getState();
		ServerTasks.scheduleForPlayer(serverPlayer, 1, () -> {
			if (ThreadLocalRandom.current().nextFloat() < 0.10F) {
				net.mcreator.mcanomalyarchives.codex.CodexObservations.record(serverPlayer, "negative_pickaxe");
				replaceNearbyStoneThenReturnIt(level, origin, brokenState);
			}
		});
	}

	private static void replaceNearbyStoneThenReturnIt(ServerLevel level, BlockPos origin, BlockState sourceState) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
		for (int attempt = 0; attempt < 24; attempt++) {
			candidate.set(origin.getX() + random.nextInt(-5, 6), origin.getY() + random.nextInt(-3, 4), origin.getZ() + random.nextInt(-5, 6));
			if (candidate.equals(origin) || !level.hasChunkAt(candidate)) continue;
			BlockState current = level.getBlockState(candidate);
			if (!current.is(BlockTags.BASE_STONE_OVERWORLD) || current.hasBlockEntity()) continue;
            int choice = random.nextInt(REPLACEMENTS.size() + 1);
            BlockState replacement = (choice == REPLACEMENTS.size()
                    ? net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks.UO002_MISPLACED.get()
                    : REPLACEMENTS.get(choice)).defaultBlockState();
			if (replacement.is(current.getBlock())) continue;
			BlockPos changedPos = candidate.immutable();
			if (!level.setBlock(changedPos, replacement, Block.UPDATE_ALL)) continue;
			// The displaced natural stone returns later in nearby cave air; no bonus drops are created.
			ServerTasks.schedule(600, () -> {
				if (!level.hasChunkAt(changedPos)) return;
				BlockPos returnPos = findCaveAir(level, changedPos);
				if (returnPos == null) returnPos = changedPos;
				if (level.getBlockState(returnPos).isAir()) level.setBlock(returnPos, current, Block.UPDATE_ALL);
			});
			return;
		}
	}

	private static BlockPos findCaveAir(ServerLevel level, BlockPos center) {
		for (int dy = -3; dy <= 3; dy++) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					BlockPos pos = center.offset(dx, dy, dz);
					if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir() && !level.canSeeSky(pos)) return pos;
				}
			}
		}
		return null;
	}
}
