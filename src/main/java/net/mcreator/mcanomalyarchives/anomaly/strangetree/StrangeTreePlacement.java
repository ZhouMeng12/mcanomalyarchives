package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.mcreator.mcanomalyarchives.anomaly.strangetree.StrangeTreeState.Point;

/**
 * 树身写入的内部接口。生产世界与可拒绝写入的测试 adapter 共用同一提交路径，
 * 不再用手动 finishStanding 的测试冒充世界恢复成功。
 */
final class StrangeTreePlacement {
	interface Access<S> {
		boolean ready(Point point);
		S read(Point point);
		boolean isAir(S state);
		S air();
		boolean write(Point point, S state);
	}

	record Entry<S>(int index, Point point, S state) { }

	private StrangeTreePlacement() { }

	static <S> boolean apply(List<Entry<S>> entries, Access<S> world, Consumer<List<Integer>> beforeWrite) {
		return apply(entries, world, beforeWrite, false);
	}

	/** 迁居必须完整放下树身，不能把被挡住的树干当作成功后丢掉快照。 */
	static <S> boolean applyRelocation(List<Entry<S>> entries, Access<S> world, Consumer<List<Integer>> beforeWrite) {
		return apply(entries, world, beforeWrite, true);
	}

	private static <S> boolean apply(List<Entry<S>> entries, Access<S> world,
			Consumer<List<Integer>> beforeWrite, boolean complete) {
		List<Entry<S>> pending = new ArrayList<>();
		for (Entry<S> entry : entries) {
			if (!world.ready(entry.point)) return false;
			S current = world.read(entry.point);
			if (world.isAir(current)) pending.add(entry);
			else if (complete && !Objects.equals(current, entry.state)) return false;
		}
		List<Integer> attempted = new ArrayList<>();
		for (Entry<S> entry : pending) {
			if (!world.ready(entry.point)) return false;
			// 前一块的世界回调可能改了后一位置；不要用旧预检结果覆盖后来出现的方块。
			if (!world.isAir(world.read(entry.point))) {
				if (complete && !Objects.equals(world.read(entry.point), entry.state)) return false;
				continue;
			}
			attempted.add(entry.index);
			beforeWrite.accept(List.copyOf(attempted));
			boolean accepted;
			try {
				accepted = world.write(entry.point, entry.state);
			} catch (RuntimeException failure) {
				return false; // 写入抛异常也进入同一撤回路径，不消耗 journal。
			}
			if (!accepted || !Objects.equals(world.read(entry.point), entry.state)) {
				return false;
			}
		}
		return true;
	}

	/** 只撤回本次尝试、且仍是预期树块的位置。玩家后来放的其他块永远不动。 */
	static <S> boolean rollback(List<Entry<S>> attempted, Access<S> world) {
		for (Entry<S> entry : attempted) {
			if (!world.ready(entry.point)) return false;
		}
		boolean complete = true;
		for (Entry<S> entry : attempted.reversed()) {
			if (!Objects.equals(world.read(entry.point), entry.state)) continue;
			boolean removed;
			try {
				removed = world.write(entry.point, world.air());
			} catch (RuntimeException failure) {
				removed = false;
			}
			if (!removed || !world.isAir(world.read(entry.point))) {
				complete = false;
			}
		}
		return complete;
	}
}
