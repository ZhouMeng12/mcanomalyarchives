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
	/**
	 * 工具 / 护甲按"一整件做出来的东西"算。
	 *
	 * 这个数字是**为了保住正片那一幕**：木铲(10) → 下界合金镐(10) 必须成功
	 * （【旁白 2:19-2:37】），而木棍(1) → 钻石块(900) 必须爆炸（【旁白 3:16-3:45】）。
	 * 如果工具也按 1 算，木棍就能白变一把钻石镐；如果按 2 算，正片的木铲那一次又会炸。
	 */
	private static final int TOOL_UNITS = 10;

	/** 实体的质料直接由碰撞箱体积换算（1 立方米 ≈ 100 单位），免维护。 */
	private static final double ENTITY_SCALE = 100.0;

	private static final Map<String, Integer> OVERRIDES = new HashMap<>();

	static {
		// 正片里出现过的、以及玩家最爱试的那些
		// 注意：工具/护甲**不要**在这里写死，交给 itemUnits() 的 TOOL_UNITS 规则，
		// 否则"木铲(1) → 下界合金镐(10)"就会被判成质料不足而爆炸，正片那一幕就演不出来了。
		OVERRIDES.put("item:minecraft:stick", 1);
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
			case ITEM -> itemUnits(id);
			case BLOCK -> blockUnits(id);
			case ENTITY -> entityUnits(id);
		};
	}

	private static int itemUnits(ResourceLocation id) {
		net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
		if (item == null || item == net.minecraft.world.item.Items.AIR) {
			return FALLBACK_ITEM;
		}
		// 工具/护甲是一整件成品，比一根木棍重
		if (item.getDefaultInstance().isDamageableItem() || item instanceof net.minecraft.world.item.ArmorItem) {
			return TOOL_UNITS;
		}
		return FALLBACK_ITEM;
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
		// 方块物品要按"它代表的那个方块"算质料：一块石头重 100，不是 1
		if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
			ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
			if (blockId != null) {
				return unitsOf(ResolvedName.Kind.BLOCK, blockId);
			}
		}
		return unitsOf(ResolvedName.Kind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/**
	 * 名字对应的"物品形态"：名字是物品就用它自己，是方块就用方块物品
	 * （钻石块 → 钻石块物品）。没有物品形态的方块（水/岩浆/火）返回空。
	 */
	public static ItemStack itemFormOf(ResolvedName name) {
		return switch (name.kind()) {
			case ITEM -> BuiltInRegistries.ITEM.containsKey(name.id())
					? new ItemStack(BuiltInRegistries.ITEM.get(name.id()))
					: ItemStack.EMPTY;
			case BLOCK -> {
				Block block = BuiltInRegistries.BLOCK.get(name.id());
				if (block == null || block.asItem() == net.minecraft.world.item.Items.AIR) {
					yield ItemStack.EMPTY;
				}
				yield new ItemStack(block.asItem());
			}
			// 实体没有物品形态（刷怪蛋不算——那只是"召唤它的工具"，不是它本身）
			case ENTITY -> ItemStack.EMPTY;
		};
	}

	/** 目标的质料是否够承载这个名字。 */
	public static boolean canHold(ItemStack target, ResolvedName name) {
		return budgetOf(target) >= requirement(name);
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
	 *
	 * 作者 2026-09-13 把规则改具体了：**周围 64×64 范围内有金子就夺取
	 * （金块与金装备直接消除、金矿变成石头），没有就算了、但还是照常换**。
	 * 所以下面不再是"找一块够用的"，而是按**材料族**把整片区域扫一遍、见到就拿。
	 */
	/**
	 * 一个材料族：直接消除的方块、要替换成什么的矿石、要没收的物品、装备的材质，
	 * 以及**被打断转化时按进度折算**用的"主单位/粒"（作者 2026-09-13 定的掉落规则）。
	 */
	public record Family(java.util.Set<net.minecraft.world.level.block.Block> removes,
			Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> replaces,
			java.util.Set<net.minecraft.world.item.Item> items,
			@org.jetbrains.annotations.Nullable net.minecraft.world.item.Tier tier,
			@org.jetbrains.annotations.Nullable net.minecraft.world.item.ArmorMaterial armor,
			@org.jetbrains.annotations.Nullable net.minecraft.world.item.Item unit,
			@org.jetbrains.annotations.Nullable net.minecraft.world.item.Item nugget) {
	}

	private static final Map<String, Family> FAMILIES = new HashMap<>();

	/** 粗矿块 / 矿石也认到同一个材料族（给羊命名"粗金块"也该按金族算）。 */
	private static final Map<String, String> FAMILY_ALIAS = new HashMap<>();

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

		// ===== 材料族：按作者的新规则，见到就夺 =====
		family("minecraft:gold_block",
				new String[]{"gold_block", "raw_gold_block"},
				new String[][]{{"gold_ore", "stone"}, {"deepslate_gold_ore", "deepslate"}, {"nether_gold_ore", "netherrack"}},
				new String[]{"gold_ingot", "gold_nugget", "raw_gold", "golden_horse_armor"},
				net.minecraft.world.item.Tiers.GOLD, net.minecraft.world.item.ArmorMaterials.GOLD.value(),
				"gold_ingot", "gold_nugget");
		family("minecraft:iron_block",
				new String[]{"iron_block", "raw_iron_block"},
				new String[][]{{"iron_ore", "stone"}, {"deepslate_iron_ore", "deepslate"}},
				new String[]{"iron_ingot", "raw_iron", "iron_horse_armor", "iron_bars", "anvil", "cauldron", "hopper"},
				net.minecraft.world.item.Tiers.IRON, net.minecraft.world.item.ArmorMaterials.IRON.value(),
				"iron_ingot", "iron_nugget");
		family("minecraft:diamond_block",
				new String[]{"diamond_block"},
				new String[][]{{"diamond_ore", "stone"}, {"deepslate_diamond_ore", "deepslate"}},
				new String[]{"diamond", "diamond_horse_armor"},
				net.minecraft.world.item.Tiers.DIAMOND, net.minecraft.world.item.ArmorMaterials.DIAMOND.value(),
				"diamond", null);
		family("minecraft:netherite_block",
				new String[]{"netherite_block"},
				new String[][]{},
				new String[]{"netherite_ingot", "netherite_scrap"},
				net.minecraft.world.item.Tiers.NETHERITE, net.minecraft.world.item.ArmorMaterials.NETHERITE.value(),
				"netherite_ingot", null);
		family("minecraft:emerald_block",
				new String[]{"emerald_block"},
				new String[][]{{"emerald_ore", "stone"}, {"deepslate_emerald_ore", "deepslate"}},
				new String[]{"emerald"}, null, null, "emerald", null);
		family("minecraft:copper_block",
				new String[]{"copper_block", "raw_copper_block"},
				new String[][]{{"copper_ore", "stone"}, {"deepslate_copper_ore", "deepslate"}},
				new String[]{"copper_ingot", "raw_copper"}, null, null, "copper_ingot", null);
		family("minecraft:coal_block",
				new String[]{"coal_block"},
				new String[][]{{"coal_ore", "stone"}, {"deepslate_coal_ore", "deepslate"}},
				new String[]{"coal"}, null, null, "coal", null);
		family("minecraft:lapis_block",
				new String[]{"lapis_block"},
				new String[][]{{"lapis_ore", "stone"}, {"deepslate_lapis_ore", "deepslate"}},
				new String[]{"lapis_lazuli"}, null, null, "lapis_lazuli", null);
		family("minecraft:redstone_block",
				new String[]{"redstone_block"},
				new String[][]{{"redstone_ore", "stone"}, {"deepslate_redstone_ore", "deepslate"}},
				new String[]{"redstone"}, null, null, "redstone", null);

		// 粗矿块 / 矿石 → 归到同一个材料族
		FAMILY_ALIAS.put("minecraft:raw_gold_block", "minecraft:gold_block");
		FAMILY_ALIAS.put("minecraft:gold_ore", "minecraft:gold_block");
		FAMILY_ALIAS.put("minecraft:deepslate_gold_ore", "minecraft:gold_block");
		FAMILY_ALIAS.put("minecraft:nether_gold_ore", "minecraft:gold_block");
		FAMILY_ALIAS.put("minecraft:raw_iron_block", "minecraft:iron_block");
		FAMILY_ALIAS.put("minecraft:iron_ore", "minecraft:iron_block");
		FAMILY_ALIAS.put("minecraft:deepslate_iron_ore", "minecraft:iron_block");
		FAMILY_ALIAS.put("minecraft:diamond_ore", "minecraft:diamond_block");
		FAMILY_ALIAS.put("minecraft:deepslate_diamond_ore", "minecraft:diamond_block");
		FAMILY_ALIAS.put("minecraft:emerald_ore", "minecraft:emerald_block");
		FAMILY_ALIAS.put("minecraft:deepslate_emerald_ore", "minecraft:emerald_block");
		FAMILY_ALIAS.put("minecraft:raw_copper_block", "minecraft:copper_block");
		FAMILY_ALIAS.put("minecraft:copper_ore", "minecraft:copper_block");
		FAMILY_ALIAS.put("minecraft:deepslate_copper_ore", "minecraft:copper_block");
		FAMILY_ALIAS.put("minecraft:coal_ore", "minecraft:coal_block");
		FAMILY_ALIAS.put("minecraft:deepslate_coal_ore", "minecraft:coal_block");
		FAMILY_ALIAS.put("minecraft:lapis_ore", "minecraft:lapis_block");
		FAMILY_ALIAS.put("minecraft:deepslate_lapis_ore", "minecraft:lapis_block");
		FAMILY_ALIAS.put("minecraft:redstone_ore", "minecraft:redstone_block");
		FAMILY_ALIAS.put("minecraft:deepslate_redstone_ore", "minecraft:redstone_block");
	}

	private static void family(String targetBlock, String[] removes, String[][] replaces, String[] items,
			@org.jetbrains.annotations.Nullable net.minecraft.world.item.Tier tier,
			@org.jetbrains.annotations.Nullable net.minecraft.world.item.ArmorMaterial armor,
			@org.jetbrains.annotations.Nullable String unit,
			@org.jetbrains.annotations.Nullable String nugget) {
		java.util.Set<net.minecraft.world.level.block.Block> removeSet = new java.util.HashSet<>();
		for (String id : removes) {
			net.minecraft.world.level.block.Block b = block("minecraft:" + id);
			if (b != null) {
				removeSet.add(b);
			}
		}
		Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> replaceMap = new java.util.HashMap<>();
		for (String[] pair : replaces) {
			net.minecraft.world.level.block.Block from = block("minecraft:" + pair[0]);
			net.minecraft.world.level.block.Block to = block("minecraft:" + pair[1]);
			if (from != null && to != null) {
				replaceMap.put(from, to);
			}
		}
		java.util.Set<net.minecraft.world.item.Item> itemSet = new java.util.HashSet<>();
		for (String id : items) {
			ResourceLocation loc = ResourceLocation.tryParse("minecraft:" + id);
			if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
				itemSet.add(BuiltInRegistries.ITEM.get(loc));
			}
		}
		FAMILIES.put(targetBlock, new Family(removeSet, replaceMap, itemSet, tier, armor,
				unit == null ? null : BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:" + unit)),
				nugget == null ? null : BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:" + nugget))));
	}

	private static net.minecraft.world.level.block.Block block(String id) {
		ResourceLocation loc = ResourceLocation.tryParse(id);
		return loc != null && BuiltInRegistries.BLOCK.containsKey(loc) ? BuiltInRegistries.BLOCK.get(loc) : null;
	}

	/** 名字对应的材料族；不是金属/宝石类方块就返回 null（那就不掠夺，直接换）。 */
	public static Family familyOf(ResolvedName name) {
		if (name == null || name.kind() != ResolvedName.Kind.BLOCK) {
			return null;
		}
		String id = name.id().toString();
		Family direct = FAMILIES.get(id);
		if (direct != null) {
			return direct;
		}
		String alias = FAMILY_ALIAS.get(id);
		return alias == null ? null : FAMILIES.get(alias);
	}

	/**
	 * **转化被打断时的掉落**（作者 2026-09-13 定的规则）。
	 *
	 * <p>混到一半把它打死，掉的是它**正在变成的那个方块**；如果那是矿物方块，
	 * 就按"变的百分比"折算成**锭 + 粒**（1 方块 = 9 锭 = 81 粒；没有粒的矿物按 9 折算）。
	 * 这样"变了一半的金块"就是 4 锭 5 粒，而不是白赚一整块金。
	 *
	 * <p>非矿物方块没有更小的单位，就掉 1 个那个方块。
	 */
	public static java.util.List<ItemStack> dropsFor(ResolvedName name, float progress) {
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		if (name == null) {
			return out;
		}
		float p = Math.max(0.0f, Math.min(1.0f, progress));
		Family family = familyOf(name);
		if (family == null || family.unit() == null) {
			ItemStack whole = itemFormOf(name);
			if (!whole.isEmpty()) {
				out.add(whole.copyWithCount(1));
			}
			return out;
		}
		if (family.nugget() != null) {
			// 1 方块 = 9 锭 = 81 粒
			int totalNuggets = Math.max(1, Math.round(81.0f * p));
			int units = totalNuggets / 9;
			int nuggets = totalNuggets % 9;
			if (units > 0) {
				out.add(new ItemStack(family.unit(), units));
			}
			if (nuggets > 0) {
				out.add(new ItemStack(family.nugget(), nuggets));
			}
		} else {
			// 1 方块 = 9 个单位（钻石、煤、红石……没有粒）
			out.add(new ItemStack(family.unit(), Math.max(1, Math.round(9.0f * p))));
		}
		return out;
	}

	/** 这件物品算不算"要没收的同类材料/装备"。 */
	public static boolean isSeizable(ItemStack stack, Family family) {
		return family != null && !stack.isEmpty() && isSeizable(stack.getItem(), family);
	}

	public static boolean isSeizable(net.minecraft.world.item.Item item, Family family) {
		if (family == null) {
			return false;
		}
		if (family.items().contains(item)) {
			return true;
		}
		if (family.tier() != null && item instanceof net.minecraft.world.item.TieredItem tiered
				&& tiered.getTier() == family.tier()) {
			return true; // 金镐、金剑……一整族装备
		}
		return family.armor() != null && item instanceof net.minecraft.world.item.ArmorItem armorItem
				&& armorItem.getMaterial().value() == family.armor();
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
