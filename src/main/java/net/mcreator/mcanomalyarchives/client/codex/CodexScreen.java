package net.mcreator.mcanomalyarchives.client.codex;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.codex.AnomalyCodex;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * 见闻录界面（客户端）—— 用原版书的贴图，左右两页并排：
 * <pre>
 *   ┌─ 原版书 左页 ─┐ ┌─ 原版书 右页 ─┐
 *   │ 见闻录 4/12   │ │ 伪云    UO-001 │
 *   │ UO-001 伪云   │ │ 项目等级 R4     │
 *   │ UO-002 ???    │ │ 保密协议 B      │
 *   │ ...           │ │ 摘要…            │
 *   │ UO-012 幸运粉羊│ │ 已知特征…        │
 *   └───────────────┘ └────────────────┘
 * </pre>
 *
 * 贴图与版式直接沿用原版 {@code BookViewScreen}：
 * 贴图 {@code minecraft:textures/gui/book.png}（256×256），可见书体 192×192，
 * 页内文字区在书体坐标 (36,30) 处、114×128。
 *
 * 之所以之前"界面模糊"：原来用的是半透明深色面板盖在模糊过的世界上，
 * 世界模糊透出来整块界面就显得糊。现在主区域是**不透明**的书页贴图，字就实了。
 *
 * 缩放会按窗口大小自适应（0.7~1.8 倍），小窗口也不会顶出屏幕。
 *
 * 【工程层归属】客户端 codex 包（非 MCreator 生成区）。
 */
public class CodexScreen extends Screen {

	private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/book.png");

	/** 原版书的可见尺寸与页内文字区（单位：贴图像素，未缩放） */
	private static final int BOOK_SIZE = 192;
	private static final int TEXT_X = 36;
	private static final int TEXT_Y = 30;
	private static final int TEXT_W = 114;
	private static final int TEXT_H = 128;

	/** 书页上的文字颜色（原版书写界面就是黑字） */
	private static final int INK = 0x000000;
	private static final int INK_DIM = 0x606060;
	private static final int INK_TITLE = 0x2A1A08;
	private static final int INK_ACCENT = 0x8C3A66;
	private static final int HILITE_SELECTED = 0x30A08050;
	private static final int HILITE_HOVER = 0x18A08050;

	private int selected = 0;

	public CodexScreen() {
		super(Component.translatable(key("title")));
	}

	private static String key(String suffix) {
		return "codex." + McanomalyarchivesMod.MODID + "." + suffix;
	}

	// ===== 版式计算 =====

	private float scale() {
		float s = Math.min((this.width - 20) / 394.0F, (this.height - 40) / (float) BOOK_SIZE);
		return Mth.clamp(s, 0.7F, 1.8F);
	}

	private int pageW() {
		return Math.round(BOOK_SIZE * scale());
	}

	private int bookX(int page) {
		int bw = pageW();
		int gap = Math.round(10 * scale());
		int total = bw * 2 + gap;
		int ox = (this.width - total) / 2;
		return page == 0 ? ox : ox + bw + gap;
	}

	private int bookY() {
		return (this.height - pageW()) / 2;
	}

	/** 某页的页内文字区（屏幕坐标） */
	private int[] textArea(int page) {
		float s = scale();
		return new int[] {
				bookX(page) + Math.round(TEXT_X * s),
				bookY() + Math.round(TEXT_Y * s),
				Math.round(TEXT_W * s),
				Math.round(TEXT_H * s) };
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
				.bounds(this.width / 2 - 50, bookY() + pageW() + 4, 100, 20).build());
	}

	// ===== 绘制 =====

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(gui, mouseX, mouseY, partialTick);

		// 两页原版书（不透明贴图 → 不会像半透明面板那样糊）
		int bw = pageW();
		gui.blit(BOOK, bookX(0), bookY(), bw, bw, 0.0F, 0.0F, BOOK_SIZE, BOOK_SIZE, 256, 256);
		gui.blit(BOOK, bookX(1), bookY(), bw, bw, 0.0F, 0.0F, BOOK_SIZE, BOOK_SIZE, 256, 256);

		renderList(gui, mouseX, mouseY);
		renderDetail(gui);

		super.render(gui, mouseX, mouseY, partialTick);
	}

	/** 左页：一行一条异常（未解明显示 ???，但编号可见 → 有收集感） */
	private void renderList(GuiGraphics gui, int mouseX, int mouseY) {
		int[] area = textArea(0);
		int x = area[0], y = area[1], w = area[2];

		// 标题 + 进度
		gui.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, y, INK_TITLE);
		Component progress = Component.translatable(key("progress"), Integer.bitCount(CodexClientHandler.mask()),
				AnomalyCodex.TOTAL);
		gui.drawString(this.font, progress, x + w - this.font.width(progress), y, INK_DIM);

		int rowTop = y + 12;
		int rowH = Math.max(8, Math.min(11, (area[3] - 12) / AnomalyCodex.TOTAL));
		int mask = CodexClientHandler.mask();

		for (int i = 0; i < AnomalyCodex.TOTAL; i++) {
			AnomalyCodex.Entry entry = AnomalyCodex.get(i);
			boolean unlocked = (mask & (1 << i)) != 0;
			int ry = rowTop + i * rowH;
			boolean hovered = mouseX >= x - 2 && mouseX <= x + w + 2 && mouseY >= ry && mouseY < ry + rowH;

			if (i == selected) {
				gui.fill(x - 3, ry - 1, x + w + 3, ry + rowH - 2, HILITE_SELECTED);
			} else if (hovered) {
				gui.fill(x - 3, ry - 1, x + w + 3, ry + rowH - 2, HILITE_HOVER);
			}
			gui.drawString(this.font, entry.id(), x, ry, unlocked ? INK_ACCENT : INK_DIM);
			Component name = unlocked ? Component.translatable(entry.nameKey())
					: Component.translatable(key("locked"));
			gui.drawString(this.font, name, x + 42, ry, unlocked ? INK : INK_DIM);
		}
	}

	/** 右页：选中异常的档案详情 */
	private void renderDetail(GuiGraphics gui) {
		int[] area = textArea(1);
		int x = area[0], y = area[1], w = area[2], h = area[3];
		AnomalyCodex.Entry entry = AnomalyCodex.get(this.selected);
		int mask = CodexClientHandler.mask();
		boolean unlocked = (mask & (1 << this.selected)) != 0;

		if (!unlocked) {
			drawWrapped(gui, Component.translatable(key("locked_hint")), x, y + 10, w, INK_DIM);
			if (entry.unlock() == AnomalyCodex.Unlock.PLANNED) {
				drawWrapped(gui, Component.translatable(key("planned")), x, y + 10 + 3 * 10, w, INK_DIM);
			}
			return;
		}

		// 名称 + 编号
		gui.drawString(this.font, Component.translatable(entry.nameKey()).copy().withStyle(ChatFormatting.BOLD), x, y,
				INK_TITLE);
		gui.drawString(this.font, entry.id(), x + w - this.font.width(entry.id()), y, INK_ACCENT);
		int cy = y + 12;

		// 项目等级 / 保密协议
		gui.drawString(this.font, Component.translatable(key("level")).append(" ")
				.append(Component.translatable(entry.levelKey())), x, cy, INK_DIM);
		Component protocol = Component.translatable(key("protocol")).append(" ")
				.append(Component.translatable(entry.protocolKey()));
		gui.drawString(this.font, protocol, x + w - this.font.width(protocol), cy, INK_DIM);
		cy += 11;

		// 摘要
		cy = drawWrapped(gui, Component.translatable(entry.infoKey()), x, cy, w, INK) + 4;

		// 特征
		if (cy < y + h - 12) {
			gui.drawString(this.font, Component.translatable(key("traits")), x, cy, INK_DIM);
			cy += 10;
			drawWrapped(gui, Component.translatable(entry.traitKey()), x, cy, w, INK);
		}
	}

	/** 自动换行绘制（每行 9px），返回下一行 y */
	private int drawWrapped(GuiGraphics gui, Component text, int x, int y, int width, int color) {
		List<FormattedCharSequence> lines = this.font.split(text, width);
		for (FormattedCharSequence line : lines) {
			gui.drawString(this.font, line, x, y, color);
			y += 9;
		}
		return y;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int[] area = textArea(0);
		int x = area[0], y = area[1], w = area[2];
		int rowTop = y + 12;
		int rowH = Math.max(8, Math.min(11, (area[3] - 12) / AnomalyCodex.TOTAL));
		if (mouseX >= x - 3 && mouseX <= x + w + 3 && mouseY >= rowTop
				&& mouseY < rowTop + AnomalyCodex.TOTAL * rowH) {
			int idx = (int) ((mouseY - rowTop) / rowH);
			if (idx >= 0 && idx < AnomalyCodex.TOTAL) {
				this.selected = idx;
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** 打开界面（客户端） */
	public static void open() {
		Minecraft.getInstance().setScreen(new CodexScreen());
	}
}
