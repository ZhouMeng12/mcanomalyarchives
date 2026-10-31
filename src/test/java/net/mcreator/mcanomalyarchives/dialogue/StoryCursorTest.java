package net.mcreator.mcanomalyarchives.dialogue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoryCursorTest {
	private static StoryCursor start(Story story) {
		return new StoryCursor(story, story.nodes(Story.START_LABEL), 0);
	}

	@Test
	void selfJumpTerminatesWithoutRecursing() {
		StoryCursor cursor = start(Story.parse("[label: start]\n-> start", "test"));
		assertNull(cursor.next());
		assertNotNull(cursor.failure());
		assertNull(cursor.next());
	}

	@Test
	void twoLabelCycleDoesNotRepeatActionsForever() {
		Story story = Story.parse("""
				[label: start]
				[give_codex]
				-> other
				[label: other]
				[take_book]
				-> start
				""", "test");
		StoryCursor cursor = start(story);
		List<String> actions = new ArrayList<>();
		for (Story.Node node; (node = cursor.next()) != null;) actions.add(node.text());
		assertEquals(List.of("give_codex", "take_book"), actions);
		assertNotNull(cursor.failure());
	}

	@Test
	void longValidJumpChainReachesTheLineWithoutRecursiveCalls() {
		StringBuilder script = new StringBuilder("[label: start]\n-> step0\n");
		for (int i = 0; i < 300; i++) {
			script.append("[label: step").append(i).append("]\n-> step").append(i + 1).append('\n');
		}
		script.append("[label: step300]\n角色: 到达\n");
		StoryCursor cursor = start(Story.parse(script.toString(), "test"));
		assertEquals("到达", cursor.next().text());
		assertNull(cursor.next());
		assertNull(cursor.failure());
	}

	@Test
	void resumingAfterAJumpUsesTheDestinationAndNextIndex() {
		Story story = Story.parse("[label: start]\n-> other\n[label: other]\n角色: 一句\n[give_codex]", "test");
		StoryCursor cursor = start(story);
		assertEquals(Story.Kind.LINE, cursor.next().kind());
		StoryCursor resumed = new StoryCursor(story, cursor.nodes(), cursor.index());
		assertEquals("give_codex", resumed.next().text());
		assertNull(resumed.next());
		assertNull(resumed.failure());
	}

	@Test
	void waitingMakesALoopSafeToResumeOnAnotherTick() {
		Story story = Story.parse("[label: start]\n[wait: 1]\n-> start", "test");
		StoryCursor cursor = start(story);
		assertEquals(Story.Kind.WAIT, cursor.next().kind());
		StoryCursor resumed = new StoryCursor(story, cursor.nodes(), cursor.index());
		assertEquals(Story.Kind.WAIT, resumed.next().kind());
		assertNull(resumed.failure());
	}

	@Test
	void missingLabelReportsAnErrorInsteadOfRunningUnrelatedNodes() {
		StoryCursor cursor = start(Story.parse("[label: start]\n-> missing", "test"));
		assertNull(cursor.next());
		assertTrue(cursor.failure().contains("missing"));
	}

	@Test
	void oversizedActionChainsHaveABoundedExecutionCost() {
		StoryCursor cursor = start(Story.parse("[label: start]\n" + "[give_codex]\n".repeat(2000), "test"));
		int calls = 0;
		while (cursor.next() != null) calls++;
		assertEquals(1024, calls);
		assertNotNull(cursor.failure());
	}
}
