package net.mcreator.mcanomalyarchives.dialogue;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoryRepositoryTest {
	@TempDir Path directory;
	private final AtomicInteger builtinReads = new AtomicInteger();
	private final AtomicReference<Story> builtin = new AtomicReference<>(story("内置版本"));
	private final StoryRepository repository = new StoryRepository(() -> directory, name -> {
		builtinReads.incrementAndGet();
		return builtin.get();
	});

	private static Story story(String line) {
		return Story.parse("[label: start]\n角色: " + line, "test");
	}

	private Path write(String text) throws IOException {
		return Files.writeString(directory.resolve("sample.txt"), "[label: start]\n角色: " + text);
	}

	private static String line(Story story) { return story.nodes(Story.START_LABEL).getFirst().text(); }

	@Test
	void repeatedReadsReuseTheParsedBuiltinUntilRefresh() {
		Story first = repository.get("sample");
		builtin.set(story("数据包重载后"));
		assertSame(first, repository.get("sample"));
		assertEquals(1, builtinReads.get());
		repository.refresh();
		assertEquals("数据包重载后", line(repository.get("sample")));
	}

	@Test
	void unchangedOverrideKeepsTheSameParsedStory() throws IOException {
		write("覆盖版本");
		Story first = repository.get("sample");
		repository.refresh();
		assertSame(first, repository.get("sample"));
		assertEquals("config", repository.sourceOf("sample"));
		assertEquals(0, builtinReads.get());
	}

	@Test
	void changedSizeIsReloadedEvenWhenTheTimestampIsUnchanged() throws IOException {
		Path path = write("一");
		FileTime original = Files.getLastModifiedTime(path);
		repository.get("sample");
		write("更长的新内容");
		Files.setLastModifiedTime(path, original);
		repository.refresh();
		assertEquals("更长的新内容", line(repository.get("sample")));
	}

	@Test
	void explicitReloadAlsoReadsAnEditWithTheSameSizeAndTimestamp() throws IOException {
		Path path = write("甲");
		FileTime original = Files.getLastModifiedTime(path);
		repository.get("sample");
		write("乙");
		Files.setLastModifiedTime(path, original);
		assertEquals("乙", line(repository.reload("sample")));
	}

	@Test
	void deletingAnOverrideRestoresTheBuiltinOnTheNextRefresh() throws IOException {
		Path path = write("覆盖版本");
		repository.get("sample");
		Files.delete(path);
		repository.refresh();
		assertEquals("内置版本", line(repository.get("sample")));
		assertEquals("mod", repository.sourceOf("sample"));
	}

	@Test
	void unusableEditRetainsTheLastGoodOverride() throws IOException {
		Path path = write("上一份可用版本");
		Story first = repository.get("sample");
		Files.writeString(path, "# 编辑中，暂时没有段落");
		repository.refresh();
		assertSame(first, repository.get("sample"));
		assertEquals("config", repository.sourceOf("sample"));
	}

	@Test
	void unusableInitialOverrideCanStillUseTheBuiltin() throws IOException {
		Files.writeString(directory.resolve("sample.txt"), "# 未完成");
		assertEquals("内置版本", line(repository.get("sample")));
	}

	@Test
	void clearDropsStoriesAndSourceMetadataBeforeAnotherServerSession() {
		repository.get("sample");
		repository.clear();
		assertNull(repository.sourceOf("sample"));
		builtin.set(story("下一个服务器"));
		assertEquals("下一个服务器", line(repository.get("sample")));
		assertEquals(2, builtinReads.get());
	}

	@Test
	void invalidOrTraversingNamesNeverReadTheFilesystemOrBuiltin() {
		AtomicInteger directoryReads = new AtomicInteger();
		StoryRepository guarded = new StoryRepository(() -> {
			directoryReads.incrementAndGet();
			return directory;
		}, name -> { fail("不应读取内置脚本"); return null; });
		for (String name : new String[]{null, "", "../secret", "..\\secret", "nested/story", "UPPER", "a:b"}) {
			assertNull(guarded.get(name));
			assertNull(guarded.reload(name));
		}
		assertEquals(0, directoryReads.get());
	}
}
