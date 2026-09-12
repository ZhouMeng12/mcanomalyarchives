package net.mcreator.mcanomalyarchives.client.screen;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.BufferUtils;
import java.nio.DoubleBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.mcanomalyarchives.event.Phase5CameraHandler;
import net.mcreator.mcanomalyarchives.network.PetPurpleDogPacket;
import net.mcreator.mcanomalyarchives.network.RemovePurpleMonsterPacket;
import net.mcreator.mcanomalyarchives.network.AdvanceToPhase4Packet;
import net.mcreator.mcanomalyarchives.network.AdvanceToPhase2Packet;
import net.mcreator.mcanomalyarchives.network.GivePurplehandPacket;
import net.mcreator.mcanomalyarchives.event.PurpleMonsterPhase1ClientHandler;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public class PurpleMonsterScreen extends Screen {

	// Phase 1 textures
	private static final ResourceLocation BG_PHASE1 =
		ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/purplegui11.png");

	// Phase 2 textures
	private static final ResourceLocation BG_PHASE2 =
		ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/purplegui.png");
	private static final ResourceLocation BTN_GRAY = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/button_gray.png");
	private static final ResourceLocation BTN_GRAY_PRESSED = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/button_gray_pressed.png");
	private static final ResourceLocation BTN_PURPLE = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/button_purple.png");
	private static final ResourceLocation BTN_PURPLE_PRESSED = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/button_purple_pressed.png");

	// Phase 4 textures
	private static final ResourceLocation BG_PHASE4 =
		ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/purplegui31.png");
	private static final ResourceLocation BTN_SHORT = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/buttonshort.png");
	private static final ResourceLocation BTN_SHORT_PRESSED = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/buttonshortpressed.png");

	private static final int REF_WIDTH = 466;
	private static final int REF_HEIGHT = 650;

	// Phase 1 dimensions (purplegui11.png actual size: 466x411)
	private static final int PHASE1_WIDTH = 466;
	private static final int PHASE1_HEIGHT = 411;

	// Phase 1 button constants
	private static final int BTN1_REF_LEFT = 80;
	private static final int BTN1_REF_TOP = 220;
	private static final int BTN1_REF_WIDTH = 354;
	private static final int BTN1_GRAY_W = 390;
	private static final int BTN1_GRAY_H = 54;
	private static final int BTN1_PURPLE_W = 391;
	private static final int BTN1_PURPLE_H = 55;
	private static final int BTN1_REF_GAP = 32;
	private static final int TEXT1_REF_TOP = 147;
	private static final int TEXT1_REF_LEFT = 115;
	private static final int TEXT1_REF_WIDTH = 354;

	// Phase 2 button constants
	private static final int BTN_REF_LEFT = 58;
	private static final int BTN_REF_TOP = 265;
	private static final int BTN_REF_WIDTH = 390;
	private static final int BTN_GRAY_W = 390;
	private static final int BTN_GRAY_H = 54;
	private static final int BTN_PURPLE_W = 391;
	private static final int BTN_PURPLE_H = 55;
	private static final int BTN_REF_GAP = 28;
	private static final int SIGN_REF_TOP = 145;
	private static final int SIGN_REF_RIGHT = 60;

	// Phase 5 dimensions (purplegui41.png actual size: 635x691)
	private static final int PHASE5_WIDTH = 635;
	private static final int PHASE5_HEIGHT = 691;

	// Phase 5 textures
	private static final ResourceLocation BG_PHASE5 =
		ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/purplegui41.png");
	private static final ResourceLocation BTN_GRAY_SHORT = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/buttongrayshort.png");
	private static final ResourceLocation BTN_GRAY_SHORT_PRESSED = ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "textures/gui/purple_monster/buttongrayshortpressed.png");

	// Phase 4 button constants
	private static final int BTN4_REF_LEFT = 265;
	private static final int BTN4_REF_TOP = 248;
	private static final int BTN4_REF_WIDTH = 185;
	private static final int BTN4_SHORT_W = 185;
	private static final int BTN4_SHORT_H = 40;
	private static final int BTN4_REF_GAP = 11;
	private static final float BTN4_FONT_SIZE = 1.1f;

	// Phase 5 button constants
	private static final int BTN5_REF_LEFT = 71;
	private static final int BTN5_REF_TOP = 267;
	private static final int BTN5_REF_WIDTH = 128;
	private static final int BTN5_SHORT_W = 185;
	private static final int BTN5_SHORT_H = 40;
	private static final int BTN5_REF_GAP = 36;
	private static final float BTN5_FONT_SIZE = 0.65f;

	private final int phase;
	private final int purpleEntityId;
	private final int purpleDogEntityId;

	private float scale;
	private int xOffset;
	private int yOffset;

	// Phase 2 states
	private boolean grayBtnPressed = false;
	private boolean purpleBtnPressed = false;

	// Phase 4 states
	private boolean btn1Pressed = false;
	private boolean btn2Pressed = false;
	private boolean btn3Pressed = false;

	// Phase 5 states
	private boolean btn5PurplePressed = false;
	private boolean btn5GrayPressed = false;

	// Phase 5 mouse attraction
	private int phase5Ticks = 0;
	private boolean phase5AutoClicked = false;
	private boolean phase5BtnPressed = false;
	private int phase5BtnPressedTicks = 0;
	private static final int PHASE5_ATTRACT_START = 30;   // 30 tick 后开始吸引
	private static final float PHASE5_ATTRACT_SPEED = 0.12f; // 每 tick 插值系数
	private static final int PHASE5_CLICK_DELAY = 5; // 按钮按下后延迟5 tick再触发点击
	private static final float PHASE5_HITBOX_SCALE = 0.5f; // 判定范围放大倍数

	public PurpleMonsterScreen(int entityId, int phase, int purpleDogEntityId) {
		super(Component.literal("Purple Monster"));
		this.purpleEntityId = entityId;
		this.phase = phase;
		this.purpleDogEntityId = purpleDogEntityId;
	}

	@Override
	protected void init() {
		super.init();

		int refW = REF_WIDTH;
		int refH = REF_HEIGHT;
		float scaleMultiplier = 1.0f;

		if (phase == 1) {
			refW = PHASE1_WIDTH;
			refH = PHASE1_HEIGHT;
			scaleMultiplier = 0.66f;
		} else if (phase == 5) {
			refW = PHASE5_WIDTH;
			refH = PHASE5_HEIGHT;
			// 阶段5：界面缩放
			scaleMultiplier = 1.0f;
		}

		scale = (float) (this.height * 0.9) / refH;
		if (phase != 5 && scale * refW > this.width * 0.9) {
			scale = (float) (this.width * 0.9) / refW;
		}
		scale *= scaleMultiplier;

		int sw = (int) (refW * scale);
		int sh = (int) (refH * scale);

		if (phase == 4) {
			// 阶段4：GUI放在右半边，再往右偏移
			xOffset = this.width / 2 + (this.width / 2 - sw) / 2 + 30;
			yOffset = (this.height - sh) / 2;
		} else if (phase == 5) {
			// 阶段5：GUI放在屏幕右侧，往左移一点
			xOffset = this.width - sw - 15;
			yOffset = (this.height - sh) / 2;
		} else if (phase == 1) {
			// 阶段1：界面整体往左移，且位置上移
			xOffset = this.width / 18;
			yOffset = (this.height - sh) / 3;
		} else {
			xOffset = this.width / 10;
			yOffset = (this.height - sh) / 2;
		}
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int refW = REF_WIDTH;
		int refH = REF_HEIGHT;

		if (phase == 1) {
			refW = PHASE1_WIDTH;
			refH = PHASE1_HEIGHT;
		} else if (phase == 5) {
			refW = PHASE5_WIDTH;
			refH = PHASE5_HEIGHT;
		}

		int w = (int) (refW * scale);
		int h = (int) (refH * scale);

		if (phase == 4) {
			renderPhase4(guiGraphics, mouseX, mouseY, w, h);
		} else if (phase == 5) {
			renderPhase5(guiGraphics, mouseX, mouseY, w, h);
		} else if (phase == 1) {
			renderPhase1(guiGraphics, mouseX, mouseY, w, h);
		} else {
			renderPhase2(guiGraphics, mouseX, mouseY, w, h);
		}
	}

	// ========== Phase 1: 你怎么了？==========

	private void renderPhase1(GuiGraphics guiGraphics, int mouseX, int mouseY, int w, int h) {
		guiGraphics.blit(BG_PHASE1, xOffset, yOffset, 0, 0f, 0f, w, h, w, h);

		Minecraft mc = Minecraft.getInstance();

		// 文本区域
		int textX = xOffset + (int)(TEXT1_REF_LEFT * scale);
		int textY = yOffset + (int)(TEXT1_REF_TOP * scale);
		String textContent = "你怎么了？";
		guiGraphics.drawString(mc.font, textContent, textX + ((int)(TEXT1_REF_WIDTH * scale) - mc.font.width(textContent)) / 2, textY, 0xFFFFFFFF);

		// 按钮
		int btnX = xOffset + (int)(BTN1_REF_LEFT * scale);
		int btnW = (int)(BTN1_REF_WIDTH * scale);
		int btnH_gray = (int)(BTN1_GRAY_H * scale);
		int btnH_purple = (int)(BTN1_PURPLE_H * scale);
		int btnGap = (int)(BTN1_REF_GAP * scale);
		int btnY_gray = yOffset + (int)(BTN1_REF_TOP * scale);
		int btnY_purple = btnY_gray + btnH_gray + btnGap;

		ResourceLocation grayTex = grayBtnPressed ? BTN_GRAY_PRESSED : BTN_GRAY;
		guiGraphics.blit(grayTex, btnX, btnY_gray, 0, 0f, 0f, btnW, btnH_gray, btnW, btnH_gray);

		ResourceLocation purpleTex = purpleBtnPressed ? BTN_PURPLE_PRESSED : BTN_PURPLE;
		guiGraphics.blit(purpleTex, btnX, btnY_purple, 0, 0f, 0f, btnW, btnH_purple, btnW, btnH_purple);

		String grayText = "没事，我在坐着看云";
		String purpleText = "我感到好痛苦，救救我";
		guiGraphics.drawString(mc.font, grayText, btnX + (btnW - mc.font.width(grayText)) / 2, btnY_gray + (btnH_gray - mc.font.lineHeight) / 2, 0xFFFFFFFF);
		guiGraphics.drawString(mc.font, purpleText, btnX + (btnW - mc.font.width(purpleText)) / 2, btnY_purple + (btnH_purple - mc.font.lineHeight) / 2, 0xFF000000);
	}

	// ========== Phase 2: 想要复仇吗？==========

	private void renderPhase2(GuiGraphics guiGraphics, int mouseX, int mouseY, int w, int h) {
		guiGraphics.blit(BG_PHASE2, xOffset, yOffset, 0, 0f, 0f, w, h, w, h);

		Minecraft mc = Minecraft.getInstance();

		// 告示牌文字
		renderSignTextPhase2(guiGraphics, mc);

		// 按钮
		int btnX = xOffset + (int)(BTN_REF_LEFT * scale);
		int btnW = (int)(BTN_REF_WIDTH * scale);
		int btnH_gray = (int)(BTN_GRAY_H * scale);
		int btnH_purple = (int)(BTN_PURPLE_H * scale);
		int btnGap = (int)(BTN_REF_GAP * scale);
		int btnY_gray = yOffset + (int)(BTN_REF_TOP * scale);
		int btnY_purple = btnY_gray + btnH_gray + btnGap;

		ResourceLocation grayTex = grayBtnPressed ? BTN_GRAY_PRESSED : BTN_GRAY;
		guiGraphics.blit(grayTex, btnX, btnY_gray, 0, 0f, 0f, btnW, btnH_gray, btnW, btnH_gray);

		ResourceLocation purpleTex = purpleBtnPressed ? BTN_PURPLE_PRESSED : BTN_PURPLE;
		guiGraphics.blit(purpleTex, btnX, btnY_purple, 0, 0f, 0f, btnW, btnH_purple, btnW, btnH_purple);

		String grayText = "仇恨改变不了过去";
		String purpleText = "想，我要摧毁卡里尔·布朗";
		guiGraphics.drawString(mc.font, grayText, btnX + (btnW - mc.font.width(grayText)) / 2, btnY_gray + (btnH_gray - mc.font.lineHeight) / 2, 0xFFFFFFFF);
		guiGraphics.drawString(mc.font, purpleText, btnX + (btnW - mc.font.width(purpleText)) / 2, btnY_purple + (btnH_purple - mc.font.lineHeight) / 2, 0xFF000000);
	}

	private void renderSignTextPhase2(GuiGraphics guiGraphics, Minecraft mc) {
		int signX = xOffset + (int)(REF_WIDTH * scale) - (int)(SIGN_REF_RIGHT * scale) + 65;
		int signY = yOffset + (int)(SIGN_REF_TOP * scale) + 10;

		guiGraphics.pose().scale(0.7f, 0.7f, 1.0f);

		String line1 = "想要";
		String line2 = "复仇吗？";
		guiGraphics.drawString(mc.font, line1, signX - mc.font.width(line1) / 2, signY, 0xFF000000);
		guiGraphics.drawString(mc.font, line2, signX - mc.font.width(line2) / 2, signY + mc.font.lineHeight + 2, 0xFF000000);

		guiGraphics.pose().scale(1.0f / 0.7f, 1.0f / 0.7f, 1.0f);
	}

	// ========== Phase 4: 你觉得这只小动物怎么样？==========

	private void renderPhase4(GuiGraphics guiGraphics, int mouseX, int mouseY, int w, int h) {
		guiGraphics.blit(BG_PHASE4, xOffset, yOffset, 0, 0f, 0f, w, h, w, h);

		Minecraft mc = Minecraft.getInstance();

		int btnX = xOffset + (int)(BTN4_REF_LEFT * scale);
		int btnW = (int)(BTN4_REF_WIDTH * scale);
		int btnH = (int)(BTN4_SHORT_H * scale);
		int btnGap = (int)(BTN4_REF_GAP * scale);
		int btnY1 = yOffset + (int)(BTN4_REF_TOP * scale);
		int btnY2 = btnY1 + btnH + btnGap;
		int btnY3 = btnY2 + btnH + btnGap;

		ResourceLocation tex1 = btn1Pressed ? BTN_SHORT_PRESSED : BTN_SHORT;
		guiGraphics.blit(tex1, btnX, btnY1, 0, 0f, 0f, btnW, btnH, btnW, btnH);

		ResourceLocation tex2 = btn2Pressed ? BTN_SHORT_PRESSED : BTN_SHORT;
		guiGraphics.blit(tex2, btnX, btnY2, 0, 0f, 0f, btnW, btnH, btnW, btnH);

		ResourceLocation tex3 = btn3Pressed ? BTN_SHORT_PRESSED : BTN_SHORT;
		guiGraphics.blit(tex3, btnX, btnY3, 0, 0f, 0f, btnW, btnH, btnW, btnH);

		String[] btnTexts = {"丑陋", "可怜", "恐怖"};
		int[] btnYs = {btnY1, btnY2, btnY3};

		for (int i = 0; i < 3; i++) {
			String text = btnTexts[i];
			int textX = btnX + (btnW - mc.font.width(text)) / 2;
			int textY = btnYs[i] + (btnH - mc.font.lineHeight) / 2;

			guiGraphics.pose().scale(BTN4_FONT_SIZE, BTN4_FONT_SIZE, 1.0f);
			guiGraphics.drawString(mc.font, text, (int)(textX / BTN4_FONT_SIZE), (int)(textY / BTN4_FONT_SIZE), 0xFF000000);
			guiGraphics.pose().scale(1.0f / BTN4_FONT_SIZE, 1.0f / BTN4_FONT_SIZE, 1.0f);
		}
	}

	// ========== Phase 5: 让我们一起并肩作战，好吗？==========

	private void renderPhase5(GuiGraphics guiGraphics, int mouseX, int mouseY, int w, int h) {
		guiGraphics.blit(BG_PHASE5, xOffset, yOffset, 0, 0f, 0f, w, h, w, h);

		Minecraft mc = Minecraft.getInstance();

		// 按钮并列：紫色在左，灰色在右
		int btnX_purple = xOffset + (int)(BTN5_REF_LEFT * scale);
		int btnW = (int)(BTN5_REF_WIDTH * scale);
		int btnH = (int)(BTN5_SHORT_H * scale);
		int btnGap = (int)(BTN5_REF_GAP * scale);
		int btnY = yOffset + (int)(BTN5_REF_TOP * scale);
		int btnX_gray = btnX_purple + btnW + btnGap;

		// 紫色按钮（好）
		ResourceLocation purpleTex = btn5PurplePressed ? BTN_SHORT_PRESSED : BTN_SHORT;
		guiGraphics.blit(purpleTex, btnX_purple, btnY, 0, 0f, 0f, btnW, btnH, btnW, btnH);

		// 灰色按钮（不好）
		ResourceLocation grayTex = btn5GrayPressed ? BTN_GRAY_SHORT_PRESSED : BTN_GRAY_SHORT;
		guiGraphics.blit(grayTex, btnX_gray, btnY, 0, 0f, 0f, btnW, btnH, btnW, btnH);

		// 按钮文字
		guiGraphics.pose().scale(BTN5_FONT_SIZE, BTN5_FONT_SIZE, 1.0f);
		String purpleText = "好";
		String grayText = "不好";
		int textY = (int)((btnY + btnH / 2 - mc.font.lineHeight / 2) / BTN5_FONT_SIZE);
		guiGraphics.drawString(mc.font, purpleText,
			(int)((btnX_purple + (btnW - mc.font.width(purpleText)) / 2) / BTN5_FONT_SIZE), textY, 0xFF000000);
		guiGraphics.drawString(mc.font, grayText,
			(int)((btnX_gray + (btnW - mc.font.width(grayText)) / 2) / BTN5_FONT_SIZE), textY, 0xFFFFFFFF);
		guiGraphics.pose().scale(1.0f / BTN5_FONT_SIZE, 1.0f / BTN5_FONT_SIZE, 1.0f);
	}

	// ========== Phase 5 mouse attraction (tick) ==========

	@Override
	public void tick() {
		super.tick();
		if (phase != 5 || phase5AutoClicked || this.minecraft == null || this.minecraft.player == null) return;

		phase5Ticks++;
		if (phase5Ticks < PHASE5_ATTRACT_START) return;

		// 获取窗口尺寸
		var window = this.minecraft.getWindow();
		int screenWidth = window.getScreenWidth();
		int screenHeight = window.getScreenHeight();
		int guiWidth = window.getGuiScaledWidth();
		int guiHeight = window.getGuiScaledHeight();

		// 计算紫色按钮区域（GUI 坐标）
		int btnW = (int)(BTN5_REF_WIDTH * scale);
		int btnH = (int)(BTN5_SHORT_H * scale);
		int btnX = xOffset + (int)(BTN5_REF_LEFT * scale);
		int btnY = yOffset + (int)(BTN5_REF_TOP * scale);

		// GUI 坐标 → 屏幕坐标转换系数
		double guiToScreenX = (double) screenWidth / guiWidth;
		double guiToScreenY = (double) screenHeight / guiHeight;

		// 放大后的判定区域（屏幕像素坐标）- 方便鼠标进入
		int hitboxW = (int)(btnW * PHASE5_HITBOX_SCALE);
		int hitboxH = (int)(btnH * PHASE5_HITBOX_SCALE);
		double hitboxX1 = (btnX + btnW / 2 - hitboxW / 2) * guiToScreenX;
		double hitboxY1 = (btnY + btnH / 2 - hitboxH / 2) * guiToScreenY;
		double hitboxX2 = hitboxX1 + hitboxW * guiToScreenX;
		double hitboxY2 = hitboxY1 + hitboxH * guiToScreenY;

		// 获取当前鼠标位置（GLFW 返回屏幕像素坐标）
		DoubleBuffer xBuf = BufferUtils.createDoubleBuffer(1);
		DoubleBuffer yBuf = BufferUtils.createDoubleBuffer(1);
		GLFW.glfwGetCursorPos(window.getWindow(), xBuf, yBuf);
		double currentScreenX = xBuf.get(0);
		double currentScreenY = yBuf.get(0);

		// 检测鼠标是否已进入放大后的判定区域
		boolean inHitbox = currentScreenX >= hitboxX1 && currentScreenX <= hitboxX2
			&& currentScreenY >= hitboxY1 && currentScreenY <= hitboxY2;

		if (inHitbox) {
			if (!phase5BtnPressed) {
				// 刚进入判定区域，按下按钮（显示按下效果）
				phase5BtnPressed = true;
				btn5PurplePressed = true; // 显示按钮按下的视觉效果
				phase5BtnPressedTicks = 0;
			} else {
				// 已经按下，等待延迟后触发点击
				phase5BtnPressedTicks++;
				if (phase5BtnPressedTicks >= PHASE5_CLICK_DELAY) {
					phase5AutoClicked = true;
					btn5PurplePressed = false; // 取消按下效果
					onPhase5Good();
				}
			}
		} else {
			// 重置按下状态
			phase5BtnPressed = false;
			btn5PurplePressed = false;
			phase5BtnPressedTicks = 0;

			// 计算按钮中心（屏幕像素坐标）
			double targetScreenX = (btnX + btnW / 2) * guiToScreenX;
			double targetScreenY = (btnY + btnH / 2) * guiToScreenY;

			// 平滑移动鼠标向按钮中心
			double dx = targetScreenX - currentScreenX;
			double dy = targetScreenY - currentScreenY;
			double newX = currentScreenX + dx * PHASE5_ATTRACT_SPEED;
			double newY = currentScreenY + dy * PHASE5_ATTRACT_SPEED;
			GLFW.glfwSetCursorPos(window.getWindow(), newX, newY);
		}
	}

	// ========== Mouse events ==========

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (phase == 4) {
			return mouseClickedPhase4(mouseX, mouseY);
		} else if (phase == 5) {
			return mouseClickedPhase5(mouseX, mouseY);
		} else if (phase == 1) {
			return mouseClickedPhase1(mouseX, mouseY);
		}
		return mouseClickedPhase2(mouseX, mouseY);
	}

	private boolean mouseClickedPhase1(double mouseX, double mouseY) {
		int btnX = xOffset + (int)(BTN1_REF_LEFT * scale);
		int btnW = (int)(BTN1_REF_WIDTH * scale);
		int btnH_gray = (int)(BTN1_GRAY_H * scale);
		int btnH_purple = (int)(BTN1_PURPLE_H * scale);
		int btnGap = (int)(BTN1_REF_GAP * scale);
		int btnY_gray = yOffset + (int)(BTN1_REF_TOP * scale);
		int btnY_purple = btnY_gray + btnH_gray + btnGap;

		if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY_gray && mouseY <= btnY_gray + btnH_gray) {
			grayBtnPressed = true;
			return true;
		}
		if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY_purple && mouseY <= btnY_purple + btnH_purple) {
			purpleBtnPressed = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, 0);
	}

	private boolean mouseClickedPhase2(double mouseX, double mouseY) {
		int btnX = xOffset + (int)(BTN_REF_LEFT * scale);
		int btnW = (int)(BTN_REF_WIDTH * scale);
		int btnH_gray = (int)(BTN_GRAY_H * scale);
		int btnH_purple = (int)(BTN_PURPLE_H * scale);
		int btnGap = (int)(BTN_REF_GAP * scale);
		int btnY_gray = yOffset + (int)(BTN_REF_TOP * scale);
		int btnY_purple = btnY_gray + btnH_gray + btnGap;

		if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY_gray && mouseY <= btnY_gray + btnH_gray) {
			grayBtnPressed = true;
			return true;
		}
		if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY_purple && mouseY <= btnY_purple + btnH_purple) {
			purpleBtnPressed = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, 0);
	}

	private boolean mouseClickedPhase4(double mouseX, double mouseY) {
		int btnX = xOffset + (int)(BTN4_REF_LEFT * scale);
		int btnW = (int)(BTN4_REF_WIDTH * scale);
		int btnH = (int)(BTN4_SHORT_H * scale);
		int btnGap = (int)(BTN4_REF_GAP * scale);
		int btnY1 = yOffset + (int)(BTN4_REF_TOP * scale);
		int btnY2 = btnY1 + btnH + btnGap;
		int btnY3 = btnY2 + btnH + btnGap;

		if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY1 && mouseY <= btnY1 + btnH) {
			btn1Pressed = true;
			return true;
		}
		if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY2 && mouseY <= btnY2 + btnH) {
			btn2Pressed = true;
			return true;
		}
		if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY3 && mouseY <= btnY3 + btnH) {
			btn3Pressed = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, 0);
	}

	private boolean mouseClickedPhase5(double mouseX, double mouseY) {
		int btnX_purple = xOffset + (int)(BTN5_REF_LEFT * scale);
		int btnW = (int)(BTN5_REF_WIDTH * scale);
		int btnH = (int)(BTN5_SHORT_H * scale);
		int btnGap = (int)(BTN5_REF_GAP * scale);
		int btnY = yOffset + (int)(BTN5_REF_TOP * scale);
		int btnX_gray = btnX_purple + btnW + btnGap;

		if (mouseX >= btnX_purple && mouseX <= btnX_purple + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
			btn5PurplePressed = true;
			return true;
		}
		if (mouseX >= btnX_gray && mouseX <= btnX_gray + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
			btn5GrayPressed = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, 0);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (phase == 4) {
			return mouseReleasedPhase4(mouseX, mouseY);
		} else if (phase == 5) {
			return mouseReleasedPhase5(mouseX, mouseY);
		} else if (phase == 1) {
			return mouseReleasedPhase1(mouseX, mouseY);
		}
		return mouseReleasedPhase2(mouseX, mouseY);
	}

	private boolean mouseReleasedPhase1(double mouseX, double mouseY) {
		int btnX = xOffset + (int)(BTN1_REF_LEFT * scale);
		int btnW = (int)(BTN1_REF_WIDTH * scale);
		int btnH_gray = (int)(BTN1_GRAY_H * scale);
		int btnH_purple = (int)(BTN1_PURPLE_H * scale);
		int btnGap = (int)(BTN1_REF_GAP * scale);
		int btnY_gray = yOffset + (int)(BTN1_REF_TOP * scale);
		int btnY_purple = btnY_gray + btnH_gray + btnGap;

		if (grayBtnPressed && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY_gray && mouseY <= btnY_gray + btnH_gray) {
			onPhase1GrayClicked();
		}
		grayBtnPressed = false;

		if (purpleBtnPressed && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY_purple && mouseY <= btnY_purple + btnH_purple) {
			onPhase1PurpleClicked();
		}
		purpleBtnPressed = false;

		return super.mouseReleased(mouseX, mouseY, 0);
	}

	private boolean mouseReleasedPhase2(double mouseX, double mouseY) {
		int btnX = xOffset + (int)(BTN_REF_LEFT * scale);
		int btnW = (int)(BTN_REF_WIDTH * scale);
		int btnH_gray = (int)(BTN_GRAY_H * scale);
		int btnH_purple = (int)(BTN_PURPLE_H * scale);
		int btnGap = (int)(BTN_REF_GAP * scale);
		int btnY_gray = yOffset + (int)(BTN_REF_TOP * scale);
		int btnY_purple = btnY_gray + btnH_gray + btnGap;

		if (grayBtnPressed && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY_gray && mouseY <= btnY_gray + btnH_gray) {
			onPhase2GrayClicked();
		}
		grayBtnPressed = false;

		if (purpleBtnPressed && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY_purple && mouseY <= btnY_purple + btnH_purple) {
			onPhase2PurpleClicked();
		}
		purpleBtnPressed = false;

		return super.mouseReleased(mouseX, mouseY, 0);
	}

	private boolean mouseReleasedPhase4(double mouseX, double mouseY) {
		int btnX = xOffset + (int)(BTN4_REF_LEFT * scale);
		int btnW = (int)(BTN4_REF_WIDTH * scale);
		int btnH = (int)(BTN4_SHORT_H * scale);
		int btnGap = (int)(BTN4_REF_GAP * scale);
		int btnY1 = yOffset + (int)(BTN4_REF_TOP * scale);
		int btnY2 = btnY1 + btnH + btnGap;
		int btnY3 = btnY2 + btnH + btnGap;

		if (btn1Pressed && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY1 && mouseY <= btnY1 + btnH) {
			onPhase4Kill();
		}
		btn1Pressed = false;

		if (btn2Pressed && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY2 && mouseY <= btnY2 + btnH) {
			onPhase4Assimilate();
		}
		btn2Pressed = false;

		if (btn3Pressed && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY3 && mouseY <= btnY3 + btnH) {
			onPhase4Exit();
		}
		btn3Pressed = false;

		return super.mouseReleased(mouseX, mouseY, 0);
	}

	private boolean mouseReleasedPhase5(double mouseX, double mouseY) {
		int btnX_purple = xOffset + (int)(BTN5_REF_LEFT * scale);
		int btnW = (int)(BTN5_REF_WIDTH * scale);
		int btnH = (int)(BTN5_SHORT_H * scale);
		int btnGap = (int)(BTN5_REF_GAP * scale);
		int btnY = yOffset + (int)(BTN5_REF_TOP * scale);
		int btnX_gray = btnX_purple + btnW + btnGap;

		if (btn5PurplePressed && mouseX >= btnX_purple && mouseX <= btnX_purple + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
			onPhase5Good();
		}
		btn5PurplePressed = false;

		if (btn5GrayPressed && mouseX >= btnX_gray && mouseX <= btnX_gray + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
			onPhase5Bad();
		}
		btn5GrayPressed = false;

		return super.mouseReleased(mouseX, mouseY, 0);
	}

	// ========== Phase 1 button actions ==========

	private void onPhase1GrayClicked() {
		this.removePurpleEntity(false);
		this.onClose();
	}

	private void onPhase1PurpleClicked() {
		// 关闭 GUI，启动客户端状态机：播 model.hand → 换纹理 → 开阶段2
		if (this.minecraft != null && this.minecraft.player != null) {
			PurpleMonsterPhase1ClientHandler.startTransition(this.minecraft.player.getUUID(), this.purpleEntityId);
		}
		this.minecraft.setScreen(null);
	}

	// ========== Phase 2 button actions ==========

	private void onPhase2GrayClicked() {
		this.removePurpleEntity(false);
		this.onClose();
	}

	private void onPhase2PurpleClicked() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.connection.send(
				new ServerboundCustomPayloadPacket(new AdvanceToPhase4Packet(this.purpleEntityId)));
			// 锁定玩家移动，等待阶段4 GUI打开
			net.mcreator.mcanomalyarchives.event.PurpleTransitionClientHandler.lockPlayer(this.minecraft.player.getUUID());
		}
		this.minecraft.setScreen(null);
	}

	// ========== Phase 4 button actions ==========

	private void onPhase4Kill() {
		this.removePurpleEntity(true, true);
		this.onClose();
	}

	private void onPhase4Assimilate() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.connection.send(
				new ServerboundCustomPayloadPacket(new PetPurpleDogPacket(
					this.purpleEntityId, this.purpleDogEntityId)));
		}
		this.minecraft.setScreen(null);
	}

	private void onPhase4Exit() {
		this.removePurpleEntity(false);
		this.onClose();
	}

	// ========== Phase 5 button actions ==========

	private void onPhase5Good() {
		// 解锁视线
		if (this.minecraft != null && this.minecraft.player != null) {
			Phase5CameraHandler.stopLock(this.minecraft.player.getUUID());
			// 给玩家紫怪触手
			this.minecraft.player.connection.send(
				new ServerboundCustomPayloadPacket(new GivePurplehandPacket()));
		}
		this.removePurpleEntity(false);
		this.onClose();
	}

	private void onPhase5Bad() {
		if (this.minecraft != null && this.minecraft.player != null) {
			Phase5CameraHandler.stopLock(this.minecraft.player.getUUID());
		}
		this.removePurpleEntity(false);
		this.onClose();
	}

	// ========== Network ==========

	private void removePurpleEntity(boolean kill) {
		removePurpleEntity(kill, false);
	}

	private void removePurpleEntity(boolean kill, boolean killPlayer) {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.connection.send(
				new ServerboundCustomPayloadPacket(new RemovePurpleMonsterPacket(this.purpleEntityId, kill, this.purpleDogEntityId, killPlayer)));
		}
	}

	@Override
	public void onClose() {
		if (phase == 5 && this.minecraft != null && this.minecraft.player != null) {
			Phase5CameraHandler.stopLock(this.minecraft.player.getUUID());
		}
		if (phase != 1) {
			// 阶段1不能自动删除紫怪（需要保留到阶段2）
			this.removePurpleEntity(false);
		}
		super.onClose();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
