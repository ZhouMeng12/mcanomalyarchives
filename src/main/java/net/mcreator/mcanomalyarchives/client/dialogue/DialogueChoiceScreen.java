package net.mcreator.mcanomalyarchives.client.dialogue;

import net.mcreator.mcanomalyarchives.network.DialogueChoiceAnswerPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 对话中的选择界面：**屏幕中间几个按钮**。
 *
 * 刻意做得"不像一个界面"：
 * <ul>
 * <li>{@link #renderBackground} 重写为空 —— 不压暗、不糊世界（否则会像开箱子那样盖住画面）；</li>
 * <li>{@link #isPauseScreen()} 返回 false —— 游戏不会暂停，气泡/世界照常；</li>
 * <li>只画一行提问 + 居中按钮，其余什么都不加。</li>
 * </ul>
 *
 * 点按钮 → 回传 {@link DialogueChoiceAnswerPacket}；按 ESC 视为取消（剧情停下，
 * 玩家可以重新右键斯万再来一遍）。
 *
 * 【工程层归属】客户端 dialogue 包（非 MCreator 生成区）。
 */
public class DialogueChoiceScreen extends Screen {

	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 6;

	private final int choiceId;
	private final String prompt;
	private final List<String> options;
	/** 防止重复回传（点完按钮会先关界面，但 ESC 与点击可能同帧） */
	private boolean answered;

	public DialogueChoiceScreen(int choiceId, String prompt, List<String> options) {
		super(Component.literal(prompt));
		this.choiceId = choiceId;
		this.prompt = prompt;
		this.options = options;
	}

	/** 由网络包调用（客户端主线程） */
	public static void open(int choiceId, String prompt, List<String> options) {
		Minecraft.getInstance().setScreen(new DialogueChoiceScreen(choiceId, prompt, options));
	}

	@Override
	protected void init() {
		int count = this.options.size();
		int totalH = count * BUTTON_HEIGHT + (count - 1) * BUTTON_GAP;
		// 按钮组整体居中（略低于正中，给提问留位置）
		int startY = this.height / 2 - totalH / 2 + 10;
		for (int i = 0; i < count; i++) {
			final int index = i;
			this.addRenderableWidget(Button.builder(Component.literal(this.options.get(i)), b -> this.answer(index))
					.bounds(this.width / 2 - BUTTON_WIDTH / 2, startY + i * (BUTTON_HEIGHT + BUTTON_GAP), BUTTON_WIDTH,
							BUTTON_HEIGHT)
					.build());
		}
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 不画任何背景：直接让世界/气泡透出来，只叠按钮和一行提问
		super.render(gui, mouseX, mouseY, partialTick);

		// 提问放在按钮组上方，居中（带一点描边，保证在任何背景上都看得清）
		List<FormattedCharSequence> lines = this.font.split(Component.literal(this.prompt), 260);
		int y = this.height / 2 - (this.options.size() * (BUTTON_HEIGHT + BUTTON_GAP)) / 2 - lines.size() * 10;
		for (FormattedCharSequence line : lines) {
			int x = (this.width - this.font.width(line)) / 2;
			gui.drawString(this.font, line, x + 1, y + 1, 0x000000, false);
			gui.drawString(this.font, line, x, y, 0xFFFFFF, false);
			y += 10;
		}
	}

	/** 重写为空：不要压暗、不要模糊、不要菜单背景贴图 */
	@Override
	public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 故意留空
	}

	private void answer(int index) {
		if (this.answered) {
			return;
		}
		this.answered = true;
		PacketDistributor.sendToServer(DialogueChoiceAnswerPacket.answer(this.choiceId, index));
		this.onClose();
	}

	@Override
	public void onClose() {
		if (!this.answered) {
			this.answered = true;
			// ESC / 关闭 = 取消：服务端会把这次选择丢掉，剧情停下，可以重新开始
			PacketDistributor.sendToServer(DialogueChoiceAnswerPacket.answer(this.choiceId, -1));
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
