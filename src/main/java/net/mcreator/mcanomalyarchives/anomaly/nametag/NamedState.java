package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 「被命名过」这件事的落盘读写。
 *
 * - 实体：写 {@code persistentData}（存档自带，随实体保存）。
 * - 物品：写 {@code DataComponents.CUSTOM_DATA}（自由 NBT，**不需要注册自定义组件**，
 *   且原版会自动把它同步给客户端，所以 tooltip 能在客户端正确显示）。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NamedState {

	/** 统一前缀，避免和别的模组抢 CUSTOM_DATA / persistentData 的键。 */
	private static final String P = "McanomalyNameTag";

	/** 目标注册表对象，形如 {@code block:minecraft:gold_block}。 */
	private static final String K_TARGET = P + "Target";
	/** 玩家输入的原文本（提示语用）。 */
	private static final String K_DISPLAY = P + "Text";
	/** 剩余寿命（实体：可行动次数；物品：由 MAX_DAMAGE 管，这里只作提示）。 */
	private static final String K_REMAIN = P + "Remain";
	/** 不可逆标记：一旦命名，普通命名牌与百变命名牌都改不回来。 */
	private static final String K_LOCKED = P + "Locked";
	/** 尚未结算的跨类爆炸（"放置后才炸"）。 */
	private static final String K_UNSTABLE = P + "Unstable";
	/** 跨类"掠夺转化"的累积进度（正片：羊不是瞬间变金，而是历时数天）。 */
	private static final String K_PROGRESS = P + "Progress";

	private NamedState() {
	}

	// ===== 实体 =====

	public static boolean isNamed(Entity entity) {
		CompoundTag tag = entity.getPersistentData();
		return tag.contains(K_TARGET) && tag.getBoolean(K_LOCKED);
	}

	public static ResolvedName targetOf(Entity entity) {
		return ResolvedName.parse(entity.getPersistentData().getString(K_TARGET));
	}

	public static String displayOf(Entity entity) {
		return entity.getPersistentData().getString(K_DISPLAY);
	}

	public static int remainingOf(Entity entity) {
		return entity.getPersistentData().getInt(K_REMAIN);
	}

	public static void setRemaining(Entity entity, int value) {
		entity.getPersistentData().putInt(K_REMAIN, value);
	}

	public static void apply(Entity entity, ResolvedName target, String display, int remaining) {
		CompoundTag tag = entity.getPersistentData();
		tag.putString(K_TARGET, target.token());
		tag.putString(K_DISPLAY, display == null ? "" : display);
		tag.putInt(K_REMAIN, remaining);
		tag.putInt(K_PROGRESS, 0);
		tag.putBoolean(K_LOCKED, true);
	}

	/** 掠夺转化的累积进度。 */
	public static int progressOf(Entity entity) {
		return entity.getPersistentData().getInt(K_PROGRESS);
	}

	public static void addProgress(Entity entity, int delta) {
		entity.getPersistentData().putInt(K_PROGRESS, progressOf(entity) + delta);
	}

	// ===== 物品 =====

	private static CompoundTag itemTag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static void writeItemTag(ItemStack stack, CompoundTag tag) {
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static boolean isNamed(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CompoundTag tag = itemTag(stack);
		return tag.contains(K_TARGET) && tag.getBoolean(K_LOCKED);
	}

	public static ResolvedName targetOf(ItemStack stack) {
		return ResolvedName.parse(itemTag(stack).getString(K_TARGET));
	}

	public static String displayOf(ItemStack stack) {
		return itemTag(stack).getString(K_DISPLAY);
	}

	public static void apply(ItemStack stack, ResolvedName target, String display, int remaining) {
		CompoundTag tag = itemTag(stack);
		tag.putString(K_TARGET, target.token());
		tag.putString(K_DISPLAY, display == null ? "" : display);
		tag.putInt(K_REMAIN, remaining);
		tag.putBoolean(K_LOCKED, true);
		writeItemTag(stack, tag);
	}

	/** 标记为"撑不住的命名"：拿在手上没事，放到地上就炸（正片木棍→钻石块）。 */
	public static void markUnstable(ItemStack stack, ResolvedName wanted, String display, float power, int residueCount) {
		CompoundTag tag = itemTag(stack);
		tag.putBoolean(K_UNSTABLE, true);
		tag.putString(K_TARGET, wanted == null ? "" : wanted.token());
		tag.putString(K_DISPLAY, display == null ? "" : display);
		tag.putFloat(P + "Power", power);
		tag.putInt(P + "Residue", residueCount);
		writeItemTag(stack, tag);
	}

	public static boolean isUnstable(ItemStack stack) {
		return !stack.isEmpty() && itemTag(stack).getBoolean(K_UNSTABLE);
	}

	public static float unstablePower(ItemStack stack) {
		return itemTag(stack).getFloat(P + "Power");
	}

	public static int unstableResidue(ItemStack stack) {
		return itemTag(stack).getInt(P + "Residue");
	}
}
