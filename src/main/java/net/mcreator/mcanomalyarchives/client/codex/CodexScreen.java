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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 见闻录界面（客户端）。
 *
 * 布局：左侧 12 条 UO 档案的列表（未解明显示 ???），右侧显示选中条目的详情。
 * 数据来自服务端同步的位掩码（{@link CodexClientHandler#mask}），
 * 文案全部走翻译键（codex.mcanomalyarchives.*）。
 *
 * 【工程层归属】客户端 codex 包（非 MCreator 生成区）。
 */
public class CodexScreen extends Screen {

	private static final int PANEL_W = 132;
	private static final int ROW_H = 14;
	private static final int BG = 0xE0101014;
	private static final int PANEL = 0xFF1A1A22;
	private static final int LINE = 0xFF2E2E3A;
	private static final int SEL = 0xFF2A3550;
	private static final int HOVER = 0xFF23232E;
	private static final int TXT = 0xFFD8D8E0;
	private static final int TXT_DIM = 0xFF7A7A88;
	private static final int ACCENT = 0xFFB06090;

	private int selected = 0;
	private int listX;
	private int listY;
	private int detailX;

	public CodexScreen() {
		super(Component.translatable(key("title")));
	}

	private static String key(String suffix) {
		return "codex." + McanomalyarchivesMod.MODID + "." + suffix;
	}

	@Override
	protected void init() {
		this.listX = 12;
		this.listY = 34;
		this.detailX = this.listX + PANEL_W + 12;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
				.bounds(this.width - 12 - 60, this.height - 28, 60, 18).build());
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(gui, mouseX, mouseY, partialTick);
		gui.fill(0, 0, this.width, this.height, BG);

		int mask = CodexClientHandler.mask();
		int found = Integer.bitCount(mask);

		// 标题 + 进度
		gui.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.listX, 10, TXT);
		gui.drawString(this.font,
				Component.translatable(key("progress"), found, AnomalyCodex.TOTAL).withStyle(ChatFormatting.GRAY),
				this.listX + this.font.width(this.title) + 10, 10, TXT_DIM);

		// 左：条目列表
		int listH = AnomalyCodex.TOTAL * ROW_H + 6;
		gui.fill(this.listX - 4, this.listY - 6, this.listX + PANEL_W, this.listY + listH, PANEL);
		for (int i = 0; i < AnomalyCodex.TOTAL; i++) {
			AnomalyCodex.Entry entry = AnomalyCodex.get(i);
			boolean unlocked = (mask & (1 << i)) != 0;
			int rowY = this.listY + i * ROW_H;
			boolean hovered = mouseX >= this.listX - 4 && mouseX <= this.listX + PANEL_W && mouseY >= rowY
					&& mouseY < rowY + ROW_H;
			if (i == this.selected) {
				gui.fill(this.listX - 4, rowY, this.listX + PANEL_W, rowY + ROW_H, SEL);
			} else if (hovered) {
				gui.fill(this.listX - 4, rowY, this.listX + PANEL_W, rowY + ROW_H, HOVER);
			}
			gui.drawString(this.font, entry.id(), this.listX, rowY + 3, unlocked ? ACCENT : TXT_DIM);
			MutableComponent name = unlocked ? Component.translatable(entry.nameKey())
					: Component.translatable(key("locked"));
			gui.drawString(this.font, name, this.listX + 46, rowY + 3, unlocked ? TXT : TXT_DIM);
		}

		// 右：详情
		renderDetail(gui, mask);

		super.render(gui, mouseX, mouseY, partialTick);
	}

	private void renderDetail(GuiGraphics gui, int mask) {
		int x = this.detailX;
		int y = this.listY;
		int w = this.width - x - 12;
		AnomalyCodex.Entry entry = AnomalyCodex.get(this.selected);
		boolean unlocked = (mask & (1 << this.selected)) != 0;

		gui.fill(x - 6, y - 6, x + w, this.height - 36, PANEL);

		if (!unlocked) {
			gui.drawString(this.font, entry.id() + "  " + Component.translatable(key("locked")).getString(), x, y, ACCENT);
			drawWrapped(gui, Component.translatable(key("locked_hint")), x, y + 22, w - 12, TXT_DIM);
			if (entry.unlock() == AnomalyCodex.Unlock.PLANNED) {
				drawWrapped(gui, Component.translatable(key("planned")), x, y + 46, w - 12, TXT_DIM);
			}
			return;
		}

		// 名称 + 编号
		gui.drawString(this.font, Component.translatable(entry.nameKey()).copy().withStyle(ChatFormatting.BOLD), x, y, TXT);
		gui.drawString(this.font, entry.id(), x + w - this.font.width(entry.id()) - 12, y, ACCENT);
		int cy = y + 16;

		// 项目等级 / 保密协议
		gui.drawString(this.font, Component.translatable(key("level")).append(" ").copy()
				.append(Component.translatable(entry.levelKey())).withStyle(ChatFormatting.GRAY), x, cy, TXT_DIM);
		gui.drawString(this.font, Component.translatable(key("protocol")).append(" ").copy()
				.append(Component.translatable(entry.protocolKey())).withStyle(ChatFormatting.GRAY), x + 110, cy, TXT_DIM);
		cy += 14;
		gui.fill(x, cy, x + w - 12, cy + 1, LINE);
		cy += 8;

		// 摘要
		cy = drawWrapped(gui, Component.translatable(entry.infoKey()), x, cy, w - 12, TXT) + 8;

		// 特征
		gui.drawString(this.font, Component.translatable(key("traits")).withStyle(ChatFormatting.GRAY), x, cy, TXT_DIM);
		cy += 12;
		drawWrapped(gui, Component.translatable(entry.traitKey()), x, cy, w - 12, TXT);
	}

	/** 自动换行绘制，返回下一行 y */
	private int drawWrapped(GuiGraphics gui, Component text, int x, int y, int width, int color) {
		List<FormattedCharSequence> lines = this.font.split(text, width);
		for (FormattedCharSequence line : lines) {
			gui.drawString(this.font, line, x, y, color);
			y += 10;
		}
		return y;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int listH = AnomalyCodex.TOTAL * ROW_H + 6;
		if (mouseX >= this.listX - 4 && mouseX <= this.listX + PANEL_W && mouseY >= this.listY
				&& mouseY <= this.listY + listH) {
			int idx = (int) ((mouseY - this.listY) / ROW_H);
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
