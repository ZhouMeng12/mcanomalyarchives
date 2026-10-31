package net.mcreator.mcanomalyarchives.dialogue;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 一次连续执行的游标：跳转不递归，遇到无等待的循环或过长动作链时中止。 */
final class StoryCursor {
	private static final int MAX_IMMEDIATE_STEPS = 1024;
	private final Story story;
	private final Map<List<Story.Node>, BitSet> visited = new IdentityHashMap<>();
	private List<Story.Node> nodes;
	private int index;
	private int steps;
	private String failure;

	StoryCursor(Story story, List<Story.Node> nodes, int index) {
		this.story = story;
		this.nodes = nodes;
		this.index = index;
	}

	Story.Node next() {
		if (failure != null) return null;
		while (index >= 0 && index < nodes.size()) {
			BitSet positions = visited.computeIfAbsent(nodes, ignored -> new BitSet());
			if (positions.get(index)) {
				failure = "检测到没有台词、等待或选项的循环跳转，剧情已停止";
				return null;
			}
			if (++steps > MAX_IMMEDIATE_STEPS) {
				failure = "连续执行超过 " + MAX_IMMEDIATE_STEPS + " 个节点，剧情已停止";
				return null;
			}
			positions.set(index);
			Story.Node node = nodes.get(index++);
			if (node.kind() != Story.Kind.JUMP) return node;
			nodes = story.nodes(node.text());
			index = 0;
			if (nodes.isEmpty()) {
				failure = "跳转目标为空：" + node.text() + "，剧情已停止";
				return null;
			}
		}
		return null;
	}

	List<Story.Node> nodes() { return nodes; }
	int index() { return index; }
	String failure() { return failure; }
}
