package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * 质料守恒：跨类命名"能不能转 / 会不会炸"的结算依据。
 *
 * 【设定依据】正片 BV1YiGt6vEbK 自己给了统一的物理解释，不是我们编的：
 * - 【旁白 3:28-3:40】木棍→钻石块爆炸后"在爆炸中心区域找到了一例极其微小的钻石块颗粒"，
 *   推论木棍"进行了原子重组，**等量的转换**成了这一小粒钻石块"。
 * - 【旁白 3:43-3:45】"莫非该形式的转换遵循一定的**物质守恒**吗？"
 * - 【旁白 4:24-4:34】羊→金块不是瞬间完成，而是"通过未知途径**不停吸收周围的金元素**"，
 *   最终"内部结构竟呈现**完全中空**"——它自己不够，就去抢环境里的。
 *
 * 所以给目标一个抽象「质料」值、给名字一个「需求」值，做一次减法：
 * <pre>
 *   差额 = 需求 − 自带质料
 *   差额 ≤ 0 → 成功（多余质料以崩解/极小爆炸释放）
 *   差额 > 0 → 先尝试从周围掠夺；掠夺不到 → 爆炸，只留下等量转换的极小残渣
 * </pre>
 * 这一条规则自动解释了正片里全部三种跨类结果，也解释了为什么"物品做目标最容易炸"
 * ——**物品的质料预算最小**（一根木棍 ≈ 1，一个钻石块 ≈ 900）。
 *
 * 【量级而非真实 kg】相对大小对就够了：玩家能清晰感知"木棍变钻石块必炸""石头变钻石反而有余"。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class MaterialUnits {

	/** 保守兜底：没进表的方块按 1 立方米石头算，物品按"一小件"算。 */
	private static final int FALLBACK_BLOCK = 100;
	private static final int FALLBACK_ITEM = 1;

	/** 实体的质料直接由碰撞箱体积换算（1 立方米 ≈ 100 单位），免维护。 */
	private static final double ENTITY_SCALE = 100.0;

	private static final Map<String, Integer> OVERRIDES = new HashMap<>();

	static {
		// 正片里出现过的、以及玩家最爱试的那些
		OVERRIDES.put("item:minecraft:stick", 1);
		OVERRIDES.put("item:minecraft:wooden_shovel", 1);
		OVERRIDES.put("item:minecraft:netherite_pickaxe", 2);
		OVERRIDES.put("item:minecraft:netherite_ingot", 60);
		OVERRIDES.put("item:minecraft:diamond", 30);
		OVERRIDES.put("item:minecraft:gold_ingot", 40);
		OVERRIDES.put("block:minecraft:stone", 100);
		OVERRIDES.put("block:minecraft:cobblestone", 100);
		OVERRIDES.put("block:minecraft:iron_block", 500);
		OVERRIDES.put("block:minecraft:diamond_block", 900);
		OVERRIDES.put("block:minecraft:gold_block", 1200);
		OVERRIDES.put("block:minecraft:water", 100);
		OVERRIDES.put("block:minecraft:lava", 100);
		OVERRIDES.put("entity:minecraft:sheep", 100);
		OVERRIDES.put("entity:minecraft:pig", 70);
		OVERRIDES.put("entity:minecraft:cow", 110);
		OVERRIDES.put("entity:minecraft:chicken", 10);
		OVERRIDES.put("entity:minecraft:ender_dragon", 9000);
	}

	private MaterialUnits() {
	}

	/** 这个名字代表的东西需要多少质料。 */
	public static int requirement(ResolvedName name) {
		return unitsOf(name.kind(), name.id());
	}

	public static int unitsOf(ResolvedName.Kind kind, ResourceLocation id) {
		Integer override = OVERRIDES.get(kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + id);
		if (override != null) {
			return override;
		}
		return switch (kind) {
			case ITEM -> FALLBACK_ITEM;
			case BLOCK -> blockUnits(id);
			case ENTITY -> entityUnits(id);
		};
	}

	private static int blockUnits(ResourceLocation id) {
		Block block = BuiltInRegistries.BLOCK.get(id);
		if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
			return FALLBACK_BLOCK;
		}
		// 用"一个方块体积"做基准，玩家放下来的就是这一格
		return FALLBACK_BLOCK;
	}

	private static int entityUnits(ResourceLocation id) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
		if (type == null) {
			return FALLBACK_BLOCK;
		}
		return (int) Math.max(1, Math.round(type.getDimensions().width() * type.getDimensions().width()
				* type.getDimensions().height() * ENTITY_SCALE));
	}

	/** 目标自身能提供多少质料。 */
	public static int budgetOf(Entity entity) {
		if (entity instanceof LivingEntity living) {
			double w = living.getBbWidth();
			double h = living.getBbHeight();
			return (int) Math.max(1, Math.round(w * w * h * ENTITY_SCALE));
		}
		return FALLBACK_ITEM;
	}

	public static int budgetOf(ItemStack stack) {
		return unitsOf(ResolvedName.Kind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/** 结算结果。 */
	public enum Outcome {
		/** 质料够（含过剩）：正常转化 */
		SUCCESS,
		/** 质料不足但环境里有可掠夺的材料：延迟转化（正片羊→金块） */
		DRAIN,
		/** 质料不足且掠夺不到：爆炸 + 等量转换的极小残渣（正片木棍→钻石块） */
		EXPLODE
	}

	/**
	 * 只做"够不够"的判断；"能不能掠夺到"由调用方扫完环境后再补一档。
	 *
	 * @param budget      目标自带质料
	 * @param requirement 名字需要的质料
	 */
	public static Outcome settle(int budget, int requirement) {
		if (budget >= requirement) {
			return Outcome.SUCCESS;
		}
		return Outcome.DRAIN; // 默认给一次"去抢"的机会，抢不到由调用方降级成 EXPLODE
	}

	/** 爆炸威力：与差额挂钩，但夹在合理区间里（对应"能量以爆炸释放"）。 */
	public static float explosionPower(int budget, int requirement) {
		int deficit = Math.max(0, requirement - budget);
		return Math.min(6.0f, 1.0f + deficit / 200.0f);
	}

	/** 等量转换后剩下的残渣数量：质料越悬殊，残渣越少（正片是"极其微小的一粒"）。 */
	public static int residueCount(int budget, int requirement) {
		if (requirement <= 0) {
			return 1;
		}
		double ratio = (double) budget / (double) requirement;
		return Math.max(1, Math.min(8, (int) Math.round(ratio * 4.0)));
	}

	/** 金属/宝石方块 → 它的基础材料，用来表示"极其微小的那一粒"。 */
	private static final Map<String, String> RESIDUE = new HashMap<>();

	/**
	 * 掠夺来源：目标材料本身，以及它的矿石/粗矿形态。
	 * 正片里羊是从"收容所附近含金元素的设备"里抽走零件的——只要附近有金，就该能抽，
	 * 不该苛刻到"必须正好有一块金块"。
	 */
	private static final Map<String, java.util.List<String>> DRAIN_SOURCES = new HashMap<>();

	static {
		RESIDUE.put("minecraft:diamond_block", "minecraft:diamond");
		RESIDUE.put("minecraft:gold_block", "minecraft:gold_ingot");
		RESIDUE.put("minecraft:iron_block", "minecraft:iron_ingot");
		RESIDUE.put("minecraft:netherite_block", "minecraft:netherite_ingot");
		RESIDUE.put("minecraft:emerald_block", "minecraft:emerald");
		RESIDUE.put("minecraft:lapis_block", "minecraft:lapis_lazuli");
		RESIDUE.put("minecraft:redstone_block", "minecraft:redstone");
		RESIDUE.put("minecraft:copper_block", "minecraft:copper_ingot");
		RESIDUE.put("minecraft:coal_block", "minecraft:coal");
		RESIDUE.put("minecraft:amethyst_block", "minecraft:amethyst_shard");

		DRAIN_SOURCES.put("minecraft:gold_block", java.util.List.of(
				"minecraft:gold_block", "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
				"minecraft:raw_gold_block", "minecraft:nether_gold_ore"));
		DRAIN_SOURCES.put("minecraft:iron_block", java.util.List.of(
				"minecraft:iron_block", "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
				"minecraft:raw_iron_block"));
		DRAIN_SOURCES.put("minecraft:diamond_block", java.util.List.of(
				"minecraft:diamond_block", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
		DRAIN_SOURCES.put("minecraft:emerald_block", java.util.List.of(
				"minecraft:emerald_block", "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore"));
		DRAIN_SOURCES.put("minecraft:copper_block", java.util.List.of(
				"minecraft:copper_block", "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
				"minecraft:raw_copper_block"));
		DRAIN_SOURCES.put("minecraft:coal_block", java.util.List.of(
				"minecraft:coal_block", "minecraft:coal_ore", "minecraft:deepslate_coal_ore"));
		DRAIN_SOURCES.put("minecraft:netherite_block", java.util.List.of("minecraft:netherite_block"));
		DRAIN_SOURCES.put("minecraft:lapis_block", java.util.List.of(
				"minecraft:lapis_block", "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore"));
		DRAIN_SOURCES.put("minecraft:redstone_block", java.util.List.of(
				"minecraft:redstone_block", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"));
	}

	/** 可以被掠夺的方块集合（目标材料本身 + 它的矿石形态）。 */
	public static java.util.Set<net.minecraft.world.level.block.Block> drainSources(ResolvedName name) {
		java.util.Set<net.minecraft.world.level.block.Block> out = new java.util.HashSet<>();
		java.util.List<String> list = DRAIN_SOURCES.get(name.id().toString());
		if (list == null) {
			// 没登记的方块：就只有它自己
			if (name.kind() == ResolvedName.Kind.BLOCK) {
				out.add(BuiltInRegistries.BLOCK.get(name.id()));
			}
			return out;
		}
		for (String id : list) {
			ResourceLocation loc = ResourceLocation.tryParse(id);
			if (loc != null && BuiltInRegistries.BLOCK.containsKey(loc)) {
				out.add(BuiltInRegistries.BLOCK.get(loc));
			}
		}
		return out;
	}

	/** 爆炸中心找到的那点东西（正片：一例极其微小的钻石块颗粒）。 */
	public static net.minecraft.world.item.ItemStack residueStack(ResolvedName name, int count) {
		ResourceLocation id = name.id();
		if (name.kind() == ResolvedName.Kind.BLOCK) {
			String mapped = RESIDUE.get(id.toString());
			ResourceLocation itemId = mapped != null ? ResourceLocation.parse(mapped)
					: BuiltInRegistries.ITEM.getKey(BuiltInRegistries.BLOCK.get(id).asItem());
			if (BuiltInRegistries.ITEM.containsKey(itemId)) {
				return new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(itemId), count);
			}
		}
		if (name.kind() == ResolvedName.Kind.ITEM && BuiltInRegistries.ITEM.containsKey(id)) {
			return new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(id), count);
		}
		return net.minecraft.world.item.ItemStack.EMPTY;
	}
}
