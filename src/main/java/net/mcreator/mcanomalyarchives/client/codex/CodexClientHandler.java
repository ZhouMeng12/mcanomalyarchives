package net.mcreator.mcanomalyarchives.client.codex;

/**
 * 见闻录：客户端侧的状态缓存。
 *
 * 解锁状态是服务端权威的，通过 {@code CodexSyncPacket} 同步到这里；
 * 界面打开时读这个缓存（不需要每次开界面都请求一次）。
 *
 * 【工程层归属】客户端 codex 包（非 MCreator 生成区）。
 */
public final class CodexClientHandler {

	private static volatile int mask = 0;

	private CodexClientHandler() {
	}

	/** 当前已解明位掩码 */
	public static int mask() {
		return mask;
	}

	/** 由网络包调用（客户端主线程） */
	public static void onSync(int newMask, boolean openScreen) {
		mask = newMask;
		if (openScreen) {
			CodexScreen.open();
		}
	}
}
