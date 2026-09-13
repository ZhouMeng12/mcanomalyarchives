package net.mcreator.mcanomalyarchives.codex;

import net.mcreator.mcanomalyarchives.McanomalyarchivesMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 见闻录（图鉴）系统 —— UO 系列档案。
 *
 * 【设定来源】编号与名称取自《MC诡异见闻录》wiki 的 UO 系列文档
 * （UO-001 ~ UO-011），UO-012 幸运粉羊是本模组自己按 Sven 同名视频实现的。
 * 文案全部走翻译键，中英各一份（见 lang / zhcn.csv）。
 *
 * 【工程层归属】codex 包（非 MCreator 生成区）。
 * 承载它的物品（见闻录）是 MCreator 元素，本类只负责数据与解锁状态。
 *
 * 解锁状态存玩家 {@code persistentData} 的位掩码里（12 条只要 int 的 12 个 bit）：
 * - 不占数据组件、不需要注册 AttachmentType，存档自带；
 * - 死亡重生时由 {@link #copyOnRespawn} 手动搬运（PlayerEvent.Clone 带过来的只有原版字段）。
 */
public final class AnomalyCodex {

	/** 解锁途径 */
	public enum Unlock {
		/** 附近出现该实体 */
		ENTITY,
		/** 背包里拥有该物品 */
		ITEM,
		/** 尚未在模组里实装（只作为档案条目存在） */
		PLANNED
	}

	/**
	 * 一条档案。
	 *
	 * @param id          编号（UO-001 …）
	 * @param nameKey     名称翻译键
	 * @param infoKey     摘要翻译键（一段话）
	 * @param traitKey    特征翻译键（"·"分隔的几条）
	 * @param levelKey    项目等级翻译键（wiki 原文的"项目等级"：R0~R4 / 无效化 / 斥锁级）
	 * @param protocolKey 保密协议等级翻译键（A/B/C/S）
	 * @param unlock      解锁途径
	 * @param match       解锁匹配的注册名（实体或物品 id；PLANNED 留空）
	 */
	public record Entry(String id, String nameKey, String infoKey, String traitKey, String levelKey, String protocolKey,
			Unlock unlock, String match) {
	}

	private static final String KEY_PREFIX = "codex." + McanomalyarchivesMod.MODID + ".";

	/**
	 * 全部档案，顺序即编号顺序。
	 *
	 * 编号 / 名称 / 项目等级 / 保密协议等级 / 特征全部取自 wiki 的 UO 系列文档
	 * （UO-011 的"项目等级 R2、保密协议 C"等字段照抄原文；wiki 用的是"项目等级"而非"威胁等级"）。
	 * UO-012 是作者按 Sven 同名视频实现的，wiki 上还没有它的页面，项目等级 R4 由作者指定。
	 */
	public static final List<Entry> ENTRIES = List.of(
			new Entry("UO-001", key("uo001.name"), key("uo001.info"), key("uo001.trait"), key("uo001.level"),
					key("uo001.protocol"), Unlock.ENTITY, "mcanomalyarchives:stange_cloud"),
			new Entry("UO-002", key("uo002.name"), key("uo002.info"), key("uo002.trait"), key("uo002.level"),
					key("uo002.protocol"), Unlock.PLANNED, ""),
			new Entry("UO-003", key("uo003.name"), key("uo003.info"), key("uo003.trait"), key("uo003.level"),
					key("uo003.protocol"), Unlock.ITEM, "mcanomalyarchives:corn_poppy"),
			new Entry("UO-004", key("uo004.name"), key("uo004.info"), key("uo004.trait"), key("uo004.level"),
					key("uo004.protocol"), Unlock.ITEM, "mcanomalyarchives:strange_fishing_rod"),
			new Entry("UO-005", key("uo005.name"), key("uo005.info"), key("uo005.trait"), key("uo005.level"),
					key("uo005.protocol"), Unlock.ENTITY, "mcanomalyarchives:purple_monster"),
			new Entry("UO-006", key("uo006.name"), key("uo006.info"), key("uo006.trait"), key("uo006.level"),
					key("uo006.protocol"), Unlock.PLANNED, ""),
			new Entry("UO-007", key("uo007.name"), key("uo007.info"), key("uo007.trait"), key("uo007.level"),
					key("uo007.protocol"), Unlock.PLANNED, ""),
			new Entry("UO-008", key("uo008.name"), key("uo008.info"), key("uo008.trait"), key("uo008.level"),
					key("uo008.protocol"), Unlock.PLANNED, ""),
			new Entry("UO-009", key("uo009.name"), key("uo009.info"), key("uo009.trait"), key("uo009.level"),
					key("uo009.protocol"), Unlock.PLANNED, ""),
			new Entry("UO-010", key("uo010.name"), key("uo010.info"), key("uo010.trait"), key("uo010.level"),
					key("uo010.protocol"), Unlock.PLANNED, ""),
			new Entry("UO-011", key("uo011.name"), key("uo011.info"), key("uo011.trait"), key("uo011.level"),
					key("uo011.protocol"), Unlock.ENTITY, "mcanomalyarchives:strange_tree"),
			new Entry("UO-012", key("uo012.name"), key("uo012.info"), key("uo012.trait"), key("uo012.level"),
					key("uo012.protocol"), Unlock.ENTITY, "mcanomalyarchives:pink_sheep"));

	public static final int TOTAL = ENTRIES.size();

	/** 玩家 persistentData 里的键 */
	private static final String TAG_MASK = "McanomalyCodexMask";
	/** 单次扫描的检测半径 */
	public static final double SCAN_RADIUS = 48.0;

	private AnomalyCodex() {
	}

	private static String key(String suffix) {
		return KEY_PREFIX + suffix;
	}

	public static Entry get(int index) {
		return ENTRIES.get(Math.max(0, Math.min(TOTAL - 1, index)));
	}

	// ===== 解锁状态 =====

	public static int getMask(ServerPlayer player) {
		return player.getPersistentData().getInt(TAG_MASK);
	}

	public static boolean isUnlocked(ServerPlayer player, int index) {
		return (getMask(player) & (1 << index)) != 0;
	}

	/** 已解明数量 */
	public static int count(ServerPlayer player) {
		return Integer.bitCount(getMask(player));
	}

	/**
	 * 解锁一条档案。
	 *
	 * @return true = 这次是新解锁（调用方可以放提示音/弹成就）
	 */
	public static boolean unlock(ServerPlayer player, int index, ServerLevel level) {
		if (index < 0 || index >= TOTAL)
			return false;
		int mask = getMask(player);
		if ((mask & (1 << index)) != 0)
			return false;
		player.getPersistentData().putInt(TAG_MASK, mask | (1 << index));
		CodexNotifier.onUnlocked(player, index);
		return true;
	}

	/** 死亡重生时搬运（PlayerEvent.Clone 里调用） */
	public static void copyOnRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
		newPlayer.getPersistentData().putInt(TAG_MASK, oldPlayer.getPersistentData().getInt(TAG_MASK));
	}

	// ===== 扫描（自动解锁）=====

	/**
	 * 对一名玩家扫描一次：附近有该异常实体（或背包里有该物品）就解锁。
	 * 由 {@link CodexScanner} 每 2 秒跑一次，不做每 tick 扫描。
	 */
	public static void scan(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level))
			return;
		AABB box = player.getBoundingBox().inflate(SCAN_RADIUS);
		for (int i = 0; i < TOTAL; i++) {
			Entry entry = ENTRIES.get(i);
			if (entry.unlock() == Unlock.PLANNED || isUnlocked(player, i))
				continue;
			if (entry.unlock() == Unlock.ITEM) {
				Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.match()));
				if (item != null && item != net.minecraft.world.item.Items.AIR && player.getInventory().contains(new ItemStack(item))) {
					unlock(player, i, level);
				}
			} else if (entry.unlock() == Unlock.ENTITY) {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entry.match()));
				if (type != null && !level.getEntitiesOfClass(Entity.class, box, e -> e.getType() == type).isEmpty()) {
					unlock(player, i, level);
				}
			}
		}
	}

	/** 数字下标转条目的工具（UI 列表用） */
	public static int indexOf(String id) {
		for (int i = 0; i < TOTAL; i++) {
			if (ENTRIES.get(i).id().equals(id))
				return i;
		}
		return -1;
	}
}
