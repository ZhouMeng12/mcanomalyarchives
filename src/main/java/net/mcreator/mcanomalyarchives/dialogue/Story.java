package net.mcreator.mcanomalyarchives.dialogue;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧情脚本：数据模型 + 解析器。
 *
 * <p>剧情写在外部文本文件里（默认 {@code config/mcanomalyarchives/stories/…txt}），
 * 改台词不用重新编译、不用重启（由 {@link StoryLoader} 按文件修改时间自动重载）。
 *
 * <h2>语法</h2>
 * <pre>
 * # 以 # 开头是注释，空行忽略
 * [label: start]                      段落标签（剧情从这里或从跳转进入）
 * 斯万: ……你手里那本，是空白的。        一条台词（"名字:" 前缀会被丢掉，只用来方便阅读）
 * 斯万[图:mcanomalyarchives:item/x]: 带图台词
 * -&gt; cloud                             跳转到标签
 * [choice]                            选项块：紧跟一行 ? 提问，若干行 - 选项 -&gt; 标签
 * ? 你想先听哪一件？
 * - 天上那些云 -&gt; cloud
 * - 会走路的树 -&gt; tree
 * [wait: 40]                          停顿 40 tick
 * [take_book]                         收走一本普通的书
 * [give_codex]                        给出《见闻录》
 * [sound:minecraft:block.enchantment_table.use]   播放音效
 * [unlock:UO-001]                     解锁见闻录里的一条档案
 * [end]                               结束（不写也行，段落跑完自动结束）
 * </pre>
 *
 * 【工程层归属】dialogue 包（非 MCreator 生成区）。
 */
public final class Story {

	public enum Kind {
		/** 一条台词 */
		LINE,
		/** 跳转 */
		JUMP,
		/** 选项 */
		CHOICE,
		/** 动作（take_book / give_codex / sound / unlock） */
		ACTION,
		/** 停顿 */
		WAIT,
		/** 显式结束 */
		END
	}

	/** 选项里的一项 */
	public record Option(String text, String target) {
	}

	/**
	 * 一个节点。
	 *
	 * @param kind   类型
	 * @param text   台词文本 / 跳转目标 / 动作名
	 * @param image  图片路径（仅 LINE 用，可空）
	 * @param prompt 提问文本（仅 CHOICE 用）
	 * @param options 选项（仅 CHOICE 用）
	 * @param ticks  停顿 tick（仅 WAIT 用）
	 */
	public record Node(Kind kind, String text, @Nullable String image, String prompt, List<Option> options,
			int ticks) {
	}

	/** 起始标签 */
	public static final String START_LABEL = "start";

	private final Map<String, List<Node>> labels = new LinkedHashMap<>();

	public Map<String, List<Node>> labels() {
		return this.labels;
	}

	/** 取某段落的节点列表（不存在返回空表，运行时会当成结束） */
	public List<Node> nodes(String label) {
		return this.labels.getOrDefault(label, List.of());
	}

	public boolean isUsable() {
		return !this.labels.isEmpty();
	}

	// ===== 解析 =====

	/**
	 * 解析剧情文本。宽容优先：看不懂的行只记一条日志跳过，绝不让整份脚本作废
	 * （作者写错一个字就整段剧情不播，是最糟糕的体验）。
	 */
	public static Story parse(String raw, String sourceName) {
		Story story = new Story();
		String current = null;
		List<Node> nodes = null;
		// 选项块临时状态
		boolean inChoice = false;
		String choicePrompt = null;
		List<Option> choiceOptions = new ArrayList<>();

		for (String rawLine : raw.split("\\R")) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			// 选项块内的行
			if (inChoice) {
				if (line.startsWith("?")) {
					choicePrompt = line.substring(1).trim();
					continue;
				}
				if (line.startsWith("-")) {
					String body = line.substring(1).trim();
					int arrow = body.lastIndexOf("->");
					if (arrow > 0) {
						choiceOptions.add(new Option(body.substring(0, arrow).trim(), body.substring(arrow + 2).trim()));
					} else {
						choiceOptions.add(new Option(body, ""));
					}
					continue;
				}
				// 选项块结束：先把 CHOICE 节点补上，再按普通行继续处理
				if (nodes != null) {
					nodes.add(new Node(Kind.CHOICE, "", null, choicePrompt == null ? "……" : choicePrompt,
							List.copyOf(choiceOptions), 0));
				}
				inChoice = false;
				choicePrompt = null;
				choiceOptions = new ArrayList<>();
			}

			// 标签
			if (line.startsWith("[label:")) {
				String name = line.substring(7).replace("]", "").trim();
				if (name.isEmpty()) {
					DialogueLog.warn(sourceName, "空的 label 名，已跳过：" + rawLine);
					continue;
				}
				current = name;
				nodes = story.labels.computeIfAbsent(name, k -> new ArrayList<>());
				continue;
			}

			if (nodes == null) {
				// 还没有任何 label：容忍作者直接从台词开始，自动建一个 start 段
				current = START_LABEL;
				nodes = story.labels.computeIfAbsent(current, k -> new ArrayList<>());
			}

			// 选项块开始
			if (line.equals("[choice]")) {
				inChoice = true;
				continue;
			}

			// [end]
			if (line.equals("[end]")) {
				nodes.add(new Node(Kind.END, "", null, "", List.of(), 0));
				continue;
			}

			// [wait: N]
			if (line.startsWith("[wait:")) {
				String num = line.substring(6).replace("]", "").trim();
				try {
					nodes.add(new Node(Kind.WAIT, "", null, "", List.of(), Math.max(1, Integer.parseInt(num))));
				} catch (NumberFormatException e) {
					DialogueLog.warn(sourceName, "[wait:] 里的不是数字，已忽略：" + rawLine);
				}
				continue;
			}

			// 其它 [动作] / [动作:参数]
			if (line.startsWith("[") && line.endsWith("]")) {
				String body = line.substring(1, line.length() - 1).trim();
				nodes.add(new Node(Kind.ACTION, body, null, "", List.of(), 0));
				continue;
			}

			// 跳转
			if (line.startsWith("->")) {
				nodes.add(new Node(Kind.JUMP, line.substring(2).trim(), null, "", List.of(), 0));
				continue;
			}

			// 台词：去掉 "名字:" 前缀，保留 [图:...]
			String text = line;
			String image = null;
			int openImage = text.indexOf("[图:");
			if (openImage >= 0) {
				int close = text.indexOf(']', openImage);
				if (close > openImage) {
					image = text.substring(openImage + 3, close).trim();
					text = text.substring(0, openImage) + text.substring(close + 1);
				}
			}
			// 第一个冒号前是说话人（半角或全角都认）
			int colon = firstColon(text);
			if (colon > 0 && colon <= 12) { // 前缀太长就不当说话人（避免把台词里的冒号误判）
				text = text.substring(colon + 1).trim();
			}
			text = text.trim();
			if (text.isEmpty()) {
				continue;
			}
			nodes.add(new Node(Kind.LINE, text, image, "", List.of(), 0));
		}

		// 收尾：文件以选项块结束
		if (inChoice && nodes != null) {
			nodes.add(new Node(Kind.CHOICE, "", null, choicePrompt == null ? "……" : choicePrompt,
					List.copyOf(choiceOptions), 0));
		}

		// 校验跳转目标：指向不存在的标签时给出明确提示（否则玩家会觉得"剧情卡住"）
		for (Map.Entry<String, List<Node>> entry : story.labels.entrySet()) {
			for (Node node : entry.getValue()) {
				if (node.kind() == Kind.JUMP && !story.labels.containsKey(node.text())) {
					DialogueLog.warn(sourceName, "跳转目标不存在：" + entry.getKey() + " -> " + node.text());
				}
				if (node.kind() == Kind.CHOICE) {
					for (Option option : node.options()) {
						if (!story.labels.containsKey(option.target())) {
							DialogueLog.warn(sourceName, "选项目标不存在：" + option.text() + " -> " + option.target());
						}
					}
				}
			}
		}
		return story;
	}

	private static int firstColon(String text) {
		int half = text.indexOf(':');
		int full = text.indexOf('：');
		if (half < 0) {
			return full;
		}
		if (full < 0) {
			return half;
		}
		return Math.min(half, full);
	}
}
