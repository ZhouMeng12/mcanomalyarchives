package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.BlockBackup;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Snapshot;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.PlacementUndo;

/** NeoForgeData 中的版本化恢复记录；不依赖生成区 addAdditionalSaveData 的调用顺序。 */
public final class StrangeTreeNbt {
	public static final String KEY = "McanomalyarchivesTreeRuntime";
	public static final int VERSION = 2;

	private StrangeTreeNbt() { }

	public static CompoundTag write(Snapshot state) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", VERSION);
		tag.putString("Dimension", state.dimension());
		tag.putString("Phase", state.phase().name());
		tag.putInt("Ticks", state.ticks());
		tag.put("Origin", point(state.origin()));
		if (state.ground() != null) tag.put("Ground", point(state.ground()));
		if (state.target() != null) tag.putUUID("Target", state.target());
		if (state.ambush() != null) tag.put("Ambush", point(state.ambush()));
		tag.putBoolean("Ambushed", state.ambushed());
		tag.putBoolean("Chasing", state.chasing());
		tag.putBoolean("KillDelivered", state.killDelivered());
		ListTag blocks = new ListTag();
		for (BlockBackup backup : state.blocks()) {
			CompoundTag block = point(backup.point());
			block.putString("State", backup.state());
			blocks.add(block);
		}
		tag.put("Blocks", blocks);
		if (state.undo() != null) {
			CompoundTag undo = new CompoundTag();
			undo.put("Offset", point(state.undo().offset()));
			undo.putIntArray("Entries", state.undo().entries());
			tag.put("PlacementUndo", undo);
		}
		return tag;
	}

	public static Snapshot read(CompoundTag tag) {
		int version = tag.getInt("Version");
		if (version != 1 && version != VERSION) throw new IllegalArgumentException("Unsupported tree journal version");
		if (version == 1 && tag.contains("PlacementUndo")) throw new IllegalArgumentException("Undo plan requires journal version 2");
		if (!tag.contains("Origin", Tag.TAG_COMPOUND) || tag.getString("Dimension").isBlank()
				|| !tag.contains("Phase", Tag.TAG_STRING) || !tag.contains("Blocks", Tag.TAG_LIST)) {
			throw new IllegalArgumentException("Incomplete tree recovery journal");
		}
		ListTag stored = tag.getList("Blocks", Tag.TAG_COMPOUND);
		ListTag raw = (ListTag) tag.get("Blocks");
		if (!raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid tree block list");
		if (stored.size() > StrangeTreeState.MAX_JOURNAL_BLOCKS) throw new IllegalArgumentException("Oversized tree journal");
		List<BlockBackup> blocks = new ArrayList<>();
		for (Tag entry : stored) {
			CompoundTag block = (CompoundTag) entry;
			StrangeTreeBlockCodec.parse(block.getString("State")); // 先验证语法/名称，调用方不能先重写损坏记录。
			blocks.add(new BlockBackup(readPoint(block), block.getString("State")));
		}
		PlacementUndo undo = null;
		if (tag.contains("PlacementUndo")) {
			if (!tag.contains("PlacementUndo", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid undo compound");
			CompoundTag plan = tag.getCompound("PlacementUndo");
			if (!plan.contains("Offset", Tag.TAG_COMPOUND) || !plan.contains("Entries", Tag.TAG_INT_ARRAY)) {
				throw new IllegalArgumentException("Incomplete placement undo");
			}
			List<Integer> indices = new ArrayList<>();
			for (int index : plan.getIntArray("Entries")) indices.add(index);
			undo = new PlacementUndo(readPoint(plan.getCompound("Offset")), indices);
		}
		return new Snapshot(tag.getString("Dimension"), StrangeTreeState.Phase.valueOf(tag.getString("Phase")),
				tag.getInt("Ticks"), readPoint(tag.getCompound("Origin")),
				tag.contains("Ground", Tag.TAG_COMPOUND) ? readPoint(tag.getCompound("Ground")) : null,
				tag.hasUUID("Target") ? tag.getUUID("Target") : null,
				tag.contains("Ambush", Tag.TAG_COMPOUND) ? readPoint(tag.getCompound("Ambush")) : null,
				tag.getBoolean("Ambushed"), tag.getBoolean("Chasing"), tag.getBoolean("KillDelivered"), blocks, undo);
	}

	/** 只更新进度，避免每 tick 重建最多 567 个方块快照。 */
	public static void writeClock(CompoundTag tag, StrangeTreeState state) {
		tag.putInt("Ticks", state.ticks());
	}

	private static CompoundTag point(Point point) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("X", point.x());
		tag.putInt("Y", point.y());
		tag.putInt("Z", point.z());
		return tag;
	}

	private static Point readPoint(CompoundTag tag) {
		if (!tag.contains("X", Tag.TAG_INT) || !tag.contains("Y", Tag.TAG_INT) || !tag.contains("Z", Tag.TAG_INT)) {
			throw new IllegalArgumentException("Incomplete tree position");
		}
		return new Point(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
	}
}
