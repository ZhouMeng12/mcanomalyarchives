package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.mcreator.mcanomalyarchives.anomaly.nametag.effects.EntityNaming;
import net.mcreator.mcanomalyarchives.anomaly.nametag.effects.ItemNaming;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 百变命名牌的全部入口。
 *
 * 【正片确认的三条使用路径（直接决定实现方式）】
 * <ul>
 *   <li><b>实体</b>：拿命名牌直接对生物使用（【旁白 1:26-1:29】牛→鸡）→ 右键实体。</li>
 *   <li><b>物品 / 方块</b>：正片明确"直接命名全部失败"，直到"通过<b>铁砧结合命名牌</b>"才成功
 *       （【旁白 2:11-2:24】）→ 铁砧事件。</li>
 *   <li><b>名字的来源</b>：玩家在铁砧输入框里打出的文本。所以本模组<b>不需要做任何本地化解析</b>
 *       ——服务端拿到的就是字符串。</li>
 * </ul>
 *
 * 【不可逆】正片：【旁白 1:51-1:56】"尝试让 D196 使用普通命名牌替换掉该名字，结果失败。"
 * 这是整个机制的压力来源：玩家每次动手前都得想清楚。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameTagHandler {

	private NameTagHandler() {
	}

	public static void init() {
		NeoForge.EVENT_BUS.register(NameTagHandler.class);
	}

	private static boolean isOurTag(ItemStack stack) {
		return !stack.isEmpty() && stack.is(McanomalyarchivesModItems.BAIBIAN_NAME_TAG.get());
	}

	/** 牌上写的名字（铁砧改名写进 CUSTOM_NAME）。 */
	private static String writtenName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? null : NameResolver.normalize(name.getString());
	}

	private static void consume(Player player, ItemStack stack) {
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		player.getCooldowns().addCooldown(stack.getItem(), NameTagCosts.USE_COOLDOWN_TICKS);
	}

	private static void deny(PlayerInteractEvent.EntityInteract event, ServerPlayer player, String messageKey, Object... args) {
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.FAIL);
		player.getCooldowns().addCooldown(event.getItemStack().getItem(), NameTagCosts.USE_COOLDOWN_TICKS);
		NameTagNotifier.actionBar(player, messageKey, args);
	}

	// ==================== 右键实体 ====================

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		Entity target = event.getTarget();
		ItemStack held = event.getItemStack();

		if (target instanceof LivingEntity living && NamedState.isNamed(living)) {
			// 不可逆：普通命名牌换不回名字
			if (held.is(Items.NAME_TAG)) {
				deny(event, player, NameTagNotifier.LOCKED);
				return;
			}
			// 名字已经写死：原版产出（挤奶/剪毛/上鞍）要被改写的身份接管
			if (isHarvestTool(held) && !isOwnIdentity(living)) {
				deny(event, player, NameTagNotifier.NO_OUTPUT);
				return;
			}
			if (isOurTag(held)) {
				deny(event, player, NameTagNotifier.LOCKED);
				return;
			}
		}

		if (!isOurTag(held)) {
			return;
		}
		if (!(target instanceof LivingEntity living) || target instanceof Player) {
			deny(event, player, NameTagNotifier.NOT_FOR_PLAYER);
			return;
		}
		String raw = writtenName(held);
		if (raw == null || raw.isEmpty()) {
			deny(event, player, NameTagNotifier.NEED_NAME);
			return;
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		nameEntity(player, living, raw, held);
	}

	/** 名字就是它自己原本的类型（牛→"牛"）：身份没变，产出照旧。 */
	private static boolean isOwnIdentity(LivingEntity living) {
		return EntityNaming.isSelfIdentity(living);
	}

	private static void nameEntity(ServerPlayer player, LivingEntity living, String raw, ItemStack held) {
		ServerLevel level = (ServerLevel) living.level();
		switch (NameRules.special(raw)) {
			case ERASE -> {
				EntityNaming.erase(level, living);
				consume(player, held);
				NameTagNotifier.actionBar(player, NameTagNotifier.ERASED);
			}
			case EARTH, GARBAGE -> NameTagNotifier.actionBar(player, NameTagNotifier.PLANNED);
			case NONE -> {
				ResolvedName resolved = NameResolver.resolve(raw, ResolvedName.Kind.ENTITY);
				if (resolved == null) {
					NameTagNotifier.actionBar(player, NameTagNotifier.UNKNOWN, raw);
					return;
				}
				if (resolved.kind() == ResolvedName.Kind.ENTITY) {
					EntityNaming.apply(living, resolved, raw);
					consume(player, held);
					NameTagNotifier.actionBar(player, NameTagNotifier.APPLIED, resolved.defaultDisplay());
					return;
				}
				// 跨类：先做质料守恒结算（正片：实体是唯一能"拖"的载体）
				MaterialUnits.Outcome outcome = EntityNaming.settle(level, living, resolved);
				switch (outcome) {
					case EXPLODE -> {
						explodeNaming(level, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), living, resolved);
						consume(player, held);
						NameTagNotifier.actionBar(player, NameTagNotifier.EXPLODED);
					}
					case SUCCESS -> {
						// 质料够：立刻完全转换（方块名 → 直接变成那个方块）
						if (resolved.kind() == ResolvedName.Kind.BLOCK) {
							EntityNaming.completeBlockConversion(level, living, resolved);
						} else {
							EntityNaming.apply(living, resolved, raw);
						}
						consume(player, held);
						NameTagNotifier.actionBar(player, NameTagNotifier.APPLIED, resolved.defaultDisplay());
					}
					default -> {
						// 质料不够：进入延迟掠夺转化（正片羊→金块，历时数天、内部完全中空）
						EntityNaming.apply(living, resolved, raw);
						consume(player, held);
						NameTagNotifier.actionBar(player, NameTagNotifier.DRAINING, resolved.defaultDisplay());
					}
				}
			}
		}
	}

	/** 跨类结算失败的爆炸：威力与质料差额挂钩，中心留下"等量转换"的极小残渣。 */
	private static void explodeNaming(ServerLevel level, double x, double y, double z, Entity cause, ResolvedName wanted) {
		int budget = cause instanceof LivingEntity living ? MaterialUnits.budgetOf(living) : 1;
		int need = MaterialUnits.requirement(wanted);
		float power = Math.min(NameTagCosts.EXPLOSION_MAX_POWER, MaterialUnits.explosionPower(budget, need));
		level.explode(null, x, y, z, power,
				NameTagCosts.EXPLOSION_BREAKS_TERRAIN ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
		int residue = MaterialUnits.residueCount(budget, need);
		ItemStack drop = MaterialUnits.residueStack(wanted, residue);
		if (!drop.isEmpty()) {
			ItemEntity item = new ItemEntity(level, x, y, z, drop);
			level.addFreshEntity(item);
		}
		if (cause instanceof LivingEntity living && living.isAlive()) {
			living.hurt(living.damageSources().genericKill(), Float.MAX_VALUE);
		}
	}

	private static boolean isHarvestTool(ItemStack stack) {
		return stack.is(Items.BUCKET) || stack.is(Items.SHEARS) || stack.is(Items.SADDLE)
				|| stack.is(Items.BOWL) || stack.is(Items.MILK_BUCKET)
				|| stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE);
	}

	// ==================== 铁砧：物品 / 方块的命名 ====================

	@SubscribeEvent
	public static void onAnvilUpdate(AnvilUpdateEvent event) {
		ItemStack left = event.getLeft();
		ItemStack right = event.getRight();
		if (!isOurTag(right)) {
			return; // 右槽没放命名牌：原版逻辑
		}
		if (left.isEmpty() || isOurTag(left)) {
			return; // 只是给命名牌本身改名，交给原版
		}
		String raw = writtenName(right);
		if (raw == null || raw.isEmpty()) {
			// 正片的原始流程：空白命名牌放进右槽，玩家在铁砧输入框里直接打出名字
			// （【旁白 2:17-2:24】"通过铁砧结合命名牌将一把木铲命名成下界合金镐"）。
			String typed = NameResolver.normalize(event.getName());
			String leftName = NameResolver.normalize(left.getHoverName().getString());
			if (typed == null || typed.isEmpty() || typed.equals(leftName)) {
				// 铁砧输入框默认显示的就是左边物品的名字——玩家没动手就别消耗他的牌
				return;
			}
			raw = typed;
		}
		Player player = event.getPlayer();

		if (NamedState.isNamed(left)) {
			event.setCanceled(true);
			NameTagNotifier.actionBar(player, NameTagNotifier.LOCKED);
			return;
		}

		ItemStack output;
		switch (NameRules.special(raw)) {
			case ERASE, EARTH, GARBAGE -> {
				// 物品侧的「无 / 地球 / 乱码」尚未实装（正片里乱码产物与 UO-002 同源，属于后续内容）
				event.setCanceled(true);
				NameTagNotifier.actionBar(player, NameTagNotifier.PLANNED);
				return;
			}
			case NONE -> {
				ResolvedName resolved = NameResolver.resolve(raw, ResolvedName.Kind.ITEM);
				if (resolved == null) {
					event.setCanceled(true);
					NameTagNotifier.actionBar(player, NameTagNotifier.UNKNOWN, raw);
					return;
				}
				if (resolved.kind() == ResolvedName.Kind.ITEM) {
					output = ItemNaming.transfer(left, resolved, raw);
					if (output.isEmpty()) {
						// 同类型、但源物品没有任何可转让的内核（纯材料，例如"钻石"）：
						// 走质料守恒结算，而不是白送 —— 否则"木棍 → 下界合金锭"就成了复制器
						output = crossTypeItem(left, resolved, raw, player);
						if (output.isEmpty()) {
							return;
						}
					}
				} else {
					output = crossTypeItem(left, resolved, raw, player);
					if (output.isEmpty()) {
						return; // 已经给出提示（撑不住 → 变成了不稳定产物）
					}
				}
			}
			default -> {
				return;
			}
		}

		output.set(DataComponents.CUSTOM_NAME, Component.literal(raw));
		event.setOutput(output);
		event.setCost(1);
		event.setMaterialCost(1);
	}

	/**
	 * 物品 ← 方块名 / 实体名：走质料守恒。
	 *
	 * 物品是"质料预算最小"的载体，所以跨类几乎必然质量不足 → 爆炸 + 极小残渣
	 * ——正是正片木棍→钻石块那一节。质料够时按"完全转换"处理，直接变成那个方块的物品形态。
	 */
	private static ItemStack crossTypeItem(ItemStack left, ResolvedName resolved, String raw, Player player) {
		int budget = MaterialUnits.budgetOf(left);
		int need = MaterialUnits.requirement(resolved);
		if (budget >= need && resolved.kind() == ResolvedName.Kind.BLOCK) {
			Block block = BuiltInRegistries.BLOCK.get(resolved.id());
			if (block != null && block.asItem() != Items.AIR) {
				ItemStack out = new ItemStack(block.asItem());
				ItemNaming.applyCost(out, raw);
				NamedState.apply(out, resolved, raw, NameTagCosts.durabilityFor(raw));
				return out;
			}
		}
		// 撑不住：产出一个"不稳定"的产物，放到地上或右键使用时爆炸
		ItemStack unstable = left.copy();
		NamedState.markUnstable(unstable, resolved, raw, MaterialUnits.explosionPower(budget, need),
				MaterialUnits.residueCount(budget, need));
		unstable.set(DataComponents.CUSTOM_NAME, Component.literal(raw));
		NameTagNotifier.actionBar(player, NameTagNotifier.UNSTABLE);
		return unstable;
	}

	// ==================== 不稳定产物：放到地上就炸 ====================

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!NamedState.isUnstable(event.getItemStack())) {
			return;
		}
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.FAIL);
		detonate(player, event.getItemStack(), event.getPos().getX() + 0.5, event.getPos().getY() + 1.0, event.getPos().getZ() + 0.5);
	}

	@SubscribeEvent
	public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!NamedState.isUnstable(event.getItemStack())) {
			return;
		}
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.FAIL);
		detonate(player, event.getItemStack(), player.getX(), player.getY() + 0.5, player.getZ());
	}

	private static void detonate(ServerPlayer player, ItemStack stack, double x, double y, double z) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		float power = Math.max(1.0f, Math.min(NameTagCosts.EXPLOSION_MAX_POWER, NamedState.unstablePower(stack)));
		level.explode(null, x, y, z, power,
				NameTagCosts.EXPLOSION_BREAKS_TERRAIN ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);

		ResolvedName wanted = NamedState.targetOf(stack);
		int residue = Math.max(1, NamedState.unstableResidue(stack));
		ItemStack drop = wanted == null ? ItemStack.EMPTY : MaterialUnits.residueStack(wanted, residue);
		if (!drop.isEmpty()) {
			level.addFreshEntity(new ItemEntity(level, x, y, z, drop));
		}
		consume(player, stack);
		NameTagNotifier.actionBar(player, NameTagNotifier.EXPLODED);
	}

	// ==================== tooltip ====================

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.getItemStack();
		if (NamedState.isUnstable(stack)) {
			event.getToolTip().add(Component.translatable(NameTagNotifier.UNSTABLE));
			return;
		}
		if (!NamedState.isNamed(stack)) {
			return;
		}
		int remaining = stack.getMaxDamage() > 0 ? stack.getMaxDamage() - stack.getDamageValue() : 0;
		event.getToolTip().add(Component.translatable(NameTagNotifier.TOOLTIP, NamedState.displayOf(stack), remaining));
	}

	/** 供自检/日志用：把名字直接解析一次，不产生任何副作用。 */
	public static String debugResolve(String rawName) {
		ResolvedName hit = NameResolver.resolve(rawName, null);
		return hit == null ? "null" : hit.token();
	}
}
