package net.mcreator.mcanomalyarchives.client.codex;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;
import net.mcreator.mcanomalyarchives.codex.AnomalyCodex;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 见闻录界面（客户端）—— 原版书贴图，左右两页：
 * <pre>
 *   ┌─ 原版书 左页 ─┐ ┌─ 原版书 右页 ─┐
 *   │ 见闻录 4/12   │ │ 伪云    UO-001 │
 *   │ UO-001 伪云   │ │ 项目等级 R4     │
 *   │ UO-002 ???    │ │ 保密协议 B      │
 *   │ ...           │ │ 摘要…            │
 *   │ UO-012 幸运粉羊│ │ 已知特征…   ‹ 1/2 ›
 *   └───────────────┘ └────────────────┘
 * </pre>
 *
 * 要点：
 * <ul>
 * <li>贴图与版式沿用原版 {@code BookViewScreen}：{@code textures/gui/book.png}（256×256，
 * 可见书体 192×192），页内文字区在书体坐标 (36,30) 处、114×128。</li>
 * <li>翻页用**原版 {@link PageButton}**（23×13、{@code widget/page_forward} 系列 sprite、
 * 自带 {@code BOOK_PAGE_TURN} 音效），位置也照原版的 (43,157)/(116,157) 比例放。</li>
 * <li>只有当选中档案的内容超过一页时才显示翻页按钮与页码指示；一页装得下就完全隐藏。</li>
 * <li>没有"完成"按钮：原版书写界面也是 ESC 关闭。</li>
 * </ul>
 *
 * 【工程层归属】客户端 codex 包（非 MCreator 生成区）。
 */
public class CodexScreen extends Screen {

	private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("textures/gui/book.png");

	/** 原版书的尺寸与页内文字区（贴图像素，未缩放） */
	private static final int BOOK_SIZE = 192;
	private static final int TEXT_X = 36;
	private static final int TEXT_Y = 30;
	private static final int TEXT_W = 114;
	private static final int TEXT_H = 128;
	/** 原版翻页按钮在书体坐标里的位置（BookViewScreen 用的是 (43,159)/(116,159)，书体 y 从 2 起算） */
	private static final int BTN_BACK_X = 43;
	private static final int BTN_FORWARD_X = 116;
	private static final int BTN_Y = 157;
	/** 页码指示相对书体底部的抬升 */
	private static final int PAGE_INDICATOR_UP = 30;

	private static final int LINE_H = 9;

	private static final int INK = 0x000000;
	private static final int INK_DIM = 0x5A5A5A;
	private static final int INK_TITLE = 0x2A1A08;
	private static final int INK_ACCENT = 0x8C3A66;
	private static final int HILITE_SELECTED = 0x30A08050;
	private static final int HILITE_HOVER = 0x18A08050;

	private int selected = 0;
	private int page = 0;
	private PageButton backButton;
	private PageButton forwardButton;

	public CodexScreen() {
		super(Component.translatable(key("title")));
	}

	private static String key(String suffix) {
		return "codex." + McanomalyarchivesMod.MODID + "." + suffix;
	}

	// ===== 版式 =====

	private float scale() {
		float s = Math.min((this.width - 20) / 394.0F, (this.height - 40) / (float) BOOK_SIZE);
		// 下限 0.9：再小的话左页 12 行（每行 9px）会挤在一起
		return Mth.clamp(s, 0.9F, 1.6F);
	}

	private int pageSize() {
		return Math.round(BOOK_SIZE * scale());
	}

	private int bookX(int pageIndex) {
		int bw = pageSize();
		int gap = Math.round(10 * scale());
		int total = bw * 2 + gap;
		int ox = (this.width - total) / 2;
		return pageIndex == 0 ? ox : ox + bw + gap;
	}

	private int bookY() {
		return (this.height - pageSize()) / 2;
	}

	/** 第 pageIndex 页的页内文字区（屏幕坐标）：{x, y, w, h} */
	private int[] textArea(int pageIndex) {
		float s = scale();
		return new int[] {
				bookX(pageIndex) + Math.round(TEXT_X * s),
				bookY() + Math.round(TEXT_Y * s),
				Math.round(TEXT_W * s),
				Math.round(TEXT_H * s) };
	}

	/** 每页能放几行 */
	private int linesPerPage() {
		return Math.max(4, textArea(1)[3] / LINE_H);
	}

	@Override
	protected void init() {
		float s = scale();
		int y = bookY() + Math.round(BTN_Y * s);
		int rightPageX = bookX(1);
		// 原版翻页按钮：位置照原版在书体里的比例
		this.backButton = this.addRenderableWidget(new PageButton(rightPageX + Math.round(BTN_BACK_X * s), y, false,
				b -> this.turnPage(-1), true));
		this.forwardButton = this.addRenderableWidget(new PageButton(rightPageX + Math.round(BTN_FORWARD_X * s), y, true,
				b -> this.turnPage(1), true));
		// 关掉线性过滤：书页会被放大 0.9~1.6 倍，默认的模糊过滤会让它发虚
		Minecraft.getInstance().getTextureManager().getTexture(BOOK).setFilter(false, false);
	}

	private void turnPage(int delta) {
		this.page = Math.max(0, this.page + delta);
	}

	// ===== 绘制 =====

	/**
	 * 背景：世界模糊 + 压暗 + 两页书。
	 *
	 * <p>关键：**书页必须画在这里**（原版 BookViewScreen 就是这么做的）。
	 * {@code Screen.render} 自己会调用本方法，之后才渲染控件；
	 * 如果在 {@code render()} 里手动再调一次，那层模糊与菜单背景贴图会盖到书页上面 ——
	 * 之前"模糊出现在页面前面"就是这个原因（而且框架那一次调用还会再糊一遍）。
	 */
	@Override
	public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		this.renderBlurredBackground(partialTick);   // 先把世界糊掉（和开箱子一样）
		this.renderTransparentBackground(gui);       // 再压暗
		int bw = pageSize();
		gui.blit(BOOK, bookX(0), bookY(), bw, bw, 0.0F, 0.0F, BOOK_SIZE, BOOK_SIZE, 256, 256);
		gui.blit(BOOK, bookX(1), bookY(), bw, bw, 0.0F, 0.0F, BOOK_SIZE, BOOK_SIZE, 256, 256);
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		// 先把分页算出来并决定按钮显隐，再让 super 去画（否则按钮显隐会慢一帧）
		List<FormattedCharSequence> lines = buildDetailLines();
		int perPage = linesPerPage();
		int pageCount = Math.max(1, (lines.size() + perPage - 1) / perPage);
		this.page = Mth.clamp(this.page, 0, pageCount - 1);
		boolean paged = pageCount > 1;
		this.backButton.visible = paged && this.page > 0;
		this.forwardButton.visible = paged && this.page < pageCount - 1;

		// super.render 会：调上面的 renderBackground（画书）→ 再渲染翻页按钮
		super.render(gui, mouseX, mouseY, partialTick);

		// 书页之上的内容：目录与档案正文
		renderList(gui, mouseX, mouseY);
		renderDetail(gui, lines, perPage, pageCount);
		if (paged) {
			renderPageIndicator(gui, pageCount);
		}
	}

	/** 左页：一行一条异常（未解明显示 ???，编号仍可见） */
	private void renderList(GuiGraphics gui, int mouseX, int mouseY) {
		int[] area = textArea(0);
		int x = area[0], y = area[1], w = area[2];

		gui.drawString(this.font, this.title, x, y, INK_TITLE, false);
		Component progress = Component.translatable(key("progress"), Integer.bitCount(CodexClientHandler.mask()),
				AnomalyCodex.TOTAL);
		gui.drawString(this.font, progress, x + w - this.font.width(progress), y, INK_DIM, false);

		int rowTop = y + 12;
		int rowH = Math.min(LINE_H, Math.max(7, (area[3] - 12) / AnomalyCodex.TOTAL));
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
			gui.drawString(this.font, entry.id(), x, ry, unlocked ? INK_ACCENT : INK_DIM, false);
			Component name = unlocked ? Component.translatable(entry.nameKey())
					: Component.translatable(key("locked"));
			gui.drawString(this.font, name, x + 42, ry, unlocked ? INK : INK_DIM, false);
		}
	}

	/**
	 * 把选中档案排成"行"（供分页）：名称/等级/协议 头部 + 摘要 + 特征。
	 * 排版是纯函数，每帧重算（只有一条档案，开销可以忽略）。
	 */
	private List<FormattedCharSequence> buildDetailLines() {
		int w = textArea(1)[2];
		List<FormattedCharSequence> out = new ArrayList<>();
		AnomalyCodex.Entry entry = AnomalyCodex.get(this.selected);
		int mask = CodexClientHandler.mask();

		if ((mask & (1 << this.selected)) == 0) {
			out.addAll(this.font.split(Component.translatable(key("locked_hint")), w));
			if (entry.unlock() == AnomalyCodex.Unlock.PLANNED) {
				out.add(Component.empty().getVisualOrderText());
				out.addAll(this.font.split(Component.translatable(key("planned")), w));
			}
			return out;
		}

		out.add(Component.translatable(entry.nameKey()).getVisualOrderText());
		out.add(Component.empty().getVisualOrderText());
		out.addAll(this.font.split(Component.translatable(key("level")).append(" ")
				.append(Component.translatable(entry.levelKey())).append("    ")
				.append(Component.translatable(key("protocol")).append(" "))
				.append(Component.translatable(entry.protocolKey())).withStyle(ChatFormatting.DARK_GRAY), w));
		out.add(Component.empty().getVisualOrderText());
		out.addAll(this.font.split(Component.translatable(entry.infoKey()), w));
		out.add(Component.empty().getVisualOrderText());
		out.add(Component.translatable(key("traits")).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
		out.addAll(this.font.split(Component.translatable(entry.traitKey()), w));
		return out;
	}

	/** 右页：当前页的正文（颜色由组件自带的样式决定，这里给墨色打底） */
	private void renderDetail(GuiGraphics gui, List<FormattedCharSequence> lines, int perPage, int pageCount) {
		int[] area = textArea(1);
		int x = area[0], y = area[1], w = area[2];
		int from = this.page * perPage;
		int to = Math.min(lines.size(), from + perPage);
		AnomalyCodex.Entry entry = AnomalyCodex.get(this.selected);
		boolean unlocked = (CodexClientHandler.mask() & (1 << this.selected)) != 0;

		int cy = y;
		for (int i = from; i < to; i++) {
			gui.drawString(this.font, lines.get(i), x, cy, INK, false);
			cy += LINE_H;
		}

		// 第一页右上角标编号
		if (this.page == 0 && unlocked) {
			gui.drawString(this.font, entry.id(), x + w - this.font.width(entry.id()), y, INK_ACCENT, false);
		}
	}

	/** 页码指示（原版书写界面底部那个 "1/2"） */
	private void renderPageIndicator(GuiGraphics gui, int pageCount) {
		Component indicator = Component.literal((this.page + 1) + "/" + pageCount);
		int cx = bookX(1) + pageSize() / 2 - this.font.width(indicator) / 2;
		int cy = bookY() + pageSize() - Math.round(PAGE_INDICATOR_UP * scale());
		gui.drawString(this.font, indicator, cx, cy, INK_DIM, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int[] area = textArea(0);
		int x = area[0], y = area[1], w = area[2];
		int rowTop = y + 12;
		int rowH = Math.min(LINE_H, Math.max(7, (area[3] - 12) / AnomalyCodex.TOTAL));
		if (mouseX >= x - 3 && mouseX <= x + w + 3 && mouseY >= rowTop
				&& mouseY < rowTop + AnomalyCodex.TOTAL * rowH) {
			int idx = (int) ((mouseY - rowTop) / rowH);
			if (idx >= 0 && idx < AnomalyCodex.TOTAL) {
				if (idx != this.selected) {
					this.selected = idx;
					this.page = 0; // 换条目回到第一页
				}
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
