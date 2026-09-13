package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.mcreator.mcanomalyarchives.anomaly.nametag.effects.EntityNaming;
import net.mcreator.mcanomalyarchives.anomaly.nametag.effects.ItemNaming;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.network.NamedTransformPacket;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

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
			if (isOurTag(held)) {
				deny(event, player, NameTagNotifier.LOCKED);
				return;
			}
			// 名字已经写死：原版产出（挤奶/剪毛/上鞍）改由"名字"决定
			// ——正片：牛被命名成"鸡"之后"无法再挤出牛奶"
			if (isHarvestTool(held)) {
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				harvest(player, living, held, event.getHand());
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

	/**
	 * 被命名过的生物被"利用"：能不能挤奶、能不能剪毛，由**名字**决定，不由它原本是什么决定。
	 * 牛被命名成"绵羊"就剪得出羊毛；牛被命名成"鸡"就挤不出奶。
	 */
	private static void harvest(ServerPlayer player, LivingEntity living, ItemStack held, net.minecraft.world.InteractionHand hand) {
		if (!(living.level() instanceof ServerLevel level)) {
			return;
		}
		switch (EntityNaming.harvestFor(NamedState.targetOf(living), held)) {
			case MILK -> {
				ItemStack filled = net.minecraft.world.item.ItemUtils.createFilledResult(held, player, new ItemStack(Items.MILK_BUCKET));
				player.setItemInHand(hand, filled);
				level.playSound(null, living.blockPosition(), net.minecraft.sounds.SoundEvents.COW_MILK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
			}
			case WOOL -> {
				ItemStack wool = new ItemStack(Items.WHITE_WOOL, 1 + level.random.nextInt(3));
				if (!player.getInventory().add(wool)) {
					player.drop(wool, false);
				}
				held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
				level.playSound(null, living.blockPosition(), net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
			}
			case STEW -> {
				ItemStack filled = net.minecraft.world.item.ItemUtils.createFilledResult(held, player, new ItemStack(Items.MUSHROOM_STEW));
				player.setItemInHand(hand, filled);
				level.playSound(null, living.blockPosition(), net.minecraft.sounds.SoundEvents.MOOSHROOM_SHEAR, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
			}
			case NONE -> {
				NameTagNotifier.actionBar(player, NameTagNotifier.NO_OUTPUT);
				return;
			}
		}
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
				consume(player, held);
				switch (resolved.kind()) {
					case ENTITY -> {
						// 作者定：**瞬间换 AI/类，但模型与材质不变**。
						// 做法是保留本体、把它整个行为目标换成名字所指生物的那一套（EntityAiSwap），
						// 所以它的样子一点没变，一动起来却是那只生物。
						EntityNaming.apply(living, resolved, raw);
						if (living instanceof net.minecraft.world.entity.PathfinderMob pathfinder) {
							EntityAiSwap.swap(pathfinder, resolved);
						}
						NamedTransformPacket.sendToWatchers(living);
					}
					case ITEM -> {
						// 作者定：命名成物品就直接失去 AI、变成能拿起来用的东西，不换材质。
						if (resolved.id().equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("potato"))) {
							// 正片开场事故：狗被命名成"土豆"后失去活性、遗体长出土豆嫩芽
							EntityNaming.toPotato(level, living);
						} else {
							EntityNaming.convertToMaterial(level, living, resolved);
						}
					}
					case BLOCK -> {
						// 唯一保留过程的：慢慢变成那个方块（身体材质与方块贴图做真正的交叉溶解）
						EntityNaming.apply(living, resolved, raw);
						EntityNaming.loseVitality(living);
						NameTagTicker.startTransform(level, living, resolved, level.getGameTime());
						NamedTransformPacket.sendToWatchers(living);
					}
				}
				NameTagNotifier.actionBar(player, NameTagNotifier.APPLIED, resolved.defaultDisplay());
			}
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
				// 名字得有"物品形态"才谈得上转换（水/岩浆/火这类没有物品形态的方块不行）
				if (MaterialUnits.itemFormOf(resolved).isEmpty()) {
					event.setCanceled(true);
					NameTagNotifier.actionBar(player, NameTagNotifier.PLANNED);
					return;
				}
				// 一律"完全转换"：本体换成名字所指的物品，外观由客户端画回源物品
				output = convertOrUnstable(left, resolved, raw, player);
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
	 * 完全转换，或者"撑不住"。
	 *
	 * 撑得住（质料够）→ 产物就是**真正的目标物品**：这块石头之后就是钻石，
	 * 能合成钻石装备、能进信标、**而且不能再当方块放下去**——因为物品本体已经是钻石了。
	 * 外观不变这一条由客户端 mixin 把它画回源物品的模型。
	 *
	 * 撑不住（质料不够）→ 正片木棍→钻石块那一幕：产物是个"不稳定"的东西，
	 * 拿在手上没事，一放到地上/一用就炸，爆炸中心只留下等量转换的极小残渣。
	 */
	private static ItemStack convertOrUnstable(ItemStack left, ResolvedName resolved, String raw, Player player) {
		if (MaterialUnits.canHold(left, resolved)) {
			return ItemNaming.convert(left, resolved, raw);
		}
		ItemStack unstable = left.copy();
		int budget = MaterialUnits.budgetOf(left);
		int need = MaterialUnits.requirement(resolved);
		NamedState.markUnstable(unstable, resolved, raw, MaterialUnits.explosionPower(budget, need),
				MaterialUnits.residueCount(budget, need));
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

	/*
	 * 这里原本有一个 ItemTooltipEvent 处理器，给命名牌加"先过铁砧"的用法提示、
	 * 给被命名的物品加"命名：X · 剩余 N 次"的读数。
	 * 已按作者要求删除：玩法靠玩家自己发现，界面不解释。
	 */

	// ==================== 转化被打断：掉的不是它原本的东西 ====================

	/**
	 * 掉落物也跟着名字走（作者："变生物的话掉落物也要变"）。
	 *
	 * <ul>
	 *   <li><b>变成方块</b>那一支（半路被打断）：掉它正在变成的那个方块，
	 *       矿物方块按进度折算成锭 + 粒（见 {@link MaterialUnits#dropsFor}）。</li>
	 *   <li><b>变成生物</b>那一支：把**名字所指生物的战利品表**掷一遍 ——
	 *       所以一只叫"僵尸"的牛掉的是腐肉，而不是牛肉皮革。
	 *       掷法与 {@code LivingEntity.dropFromLootTable} 完全一致（含抢夺附魔要用的那些上下文参数）。</li>
	 * </ul>
	 */
	@SubscribeEvent
	public static void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
		LivingEntity entity = event.getEntity();
		if (!NamedState.isNamed(entity) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ResolvedName target = NamedState.targetOf(entity);
		if (target == null) {
			return;
		}
		if (target.kind() == ResolvedName.Kind.BLOCK) {
			if (!NameTagTransform.isTransforming(entity)) {
				return;
			}
			float progress = NameTagTransform.progressOf(entity, level.getGameTime());
			event.getDrops().clear(); // 先把原生物那套掉落（猪肉之类）清掉
			for (ItemStack stack : MaterialUnits.dropsFor(target, progress)) {
				event.getDrops().add(new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack));
			}
			return;
		}
		if (target.kind() == ResolvedName.Kind.ENTITY) {
			List<ItemStack> loot = rollLootTableOf(level, entity, target, event.getSource());
			if (loot == null) {
				return;
			}
			event.getDrops().clear();
			for (ItemStack stack : loot) {
				if (!stack.isEmpty()) {
					event.getDrops().add(new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack));
				}
			}
		}
	}

	/** 掷一遍"名字所指生物"的战利品表；照抄原版 {@code dropFromLootTable} 的上下文构造。 */
	private static List<ItemStack> rollLootTableOf(ServerLevel level, LivingEntity dying, ResolvedName target,
			net.minecraft.world.damagesource.DamageSource source) {
		net.minecraft.world.entity.EntityType<?> type =
				net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(target.id());
		if (type == null) {
			return null;
		}
		net.minecraft.world.level.storage.loot.LootTable table =
				level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable());
		net.minecraft.world.level.storage.loot.LootParams.Builder builder =
				new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
						.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, dying)
						.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, dying.position())
						.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, source)
						.withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ATTACKING_ENTITY, source.getEntity())
						.withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
		if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
			builder = builder
					.withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER, player)
					.withLuck(player.getLuck());
		}
		net.minecraft.world.level.storage.loot.LootParams params = builder.create(
				net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
		return new java.util.ArrayList<>(table.getRandomItems(params));
	}

	// ==================== 名字带来的固有性质 ====================

	/** 免疫火焰与岩浆（烈焰人、岩浆怪、凋灵那类）。 */
	@SubscribeEvent
	public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
		LivingEntity entity = event.getEntity();
		if (!NamedState.isNamed(entity)) {
			return;
		}
		if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
			return;
		}
		if (CreatureTraits.has(entity, CreatureTraits.Trait.FIRE_IMMUNE)) {
			event.setCanceled(true);
		}
	}

	/**
	 * 亡灵：治疗药水伤害它、伤害药水治疗它。
	 *
	 * 原版是写在 {@code Mob.isInvertedHealAndHarm()} 里、由药水自己读的，
	 * 我们换不了那个方法，所以在"药水将要生效"时把它换成相反的那一瓶。
	 * 用一个静态集合防止相互转换形成死循环。
	 */
	private static final java.util.Set<Integer> INVERTING = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@SubscribeEvent
	public static void onEffectApplicable(
			net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {
		LivingEntity entity = event.getEntity();
		if (!NamedState.isNamed(entity) || INVERTING.contains(entity.getId())) {
			return;
		}
		if (!CreatureTraits.has(entity, CreatureTraits.Trait.INVERTED_POTION)) {
			return;
		}
		var instance = event.getEffectInstance();
		boolean heal = instance.getEffect().is(net.minecraft.world.effect.MobEffects.HEAL);
		boolean harm = instance.getEffect().is(net.minecraft.world.effect.MobEffects.HARM);
		if (!heal && !harm) {
			return;
		}
		event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		INVERTING.add(entity.getId());
		try {
			entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					heal ? net.minecraft.world.effect.MobEffects.HARM : net.minecraft.world.effect.MobEffects.HEAL,
					instance.getDuration(), instance.getAmplifier()));
		} finally {
			INVERTING.remove(entity.getId());
		}
	}

	// ==================== 牛蛋：砸出来的是下蛋那只生物的幼体 ====================

	/**
	 * 正片【旁白 1:42-1:46】：牛被命名成"鸡"后下的牛蛋，"**这些牛蛋可以孵出正常的牛幼仔**"。
	 *
	 * 原版蛋砸出来是**小鸡**，所以要拦掉：右键投掷时直接生成**下蛋那只生物的幼体**
	 * （{@code AgeableMob.setBaby}）；那只生物要是没有幼体形态（僵尸、末影人这类），
	 * 就生成它本身。所以一只叫"鸡"的牛下的"牛蛋"，砸出来是**牛犊**。
	 */
	@SubscribeEvent
	public static void onEggThrow(PlayerInteractEvent.RightClickItem event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		ItemStack stack = event.getItemStack();
		ResourceLocation species = eggSpecies(stack);
		if (species == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		net.minecraft.world.entity.EntityType<?> type =
				net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(species);
		Entity spawned = type == null ? null : type.create(level);
		if (spawned == null) {
			return;
		}
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		spawned.moveTo(player.getX(), player.getEyeY() - 0.4, player.getZ(), player.getYRot(), 0.0f);
		if (spawned instanceof net.minecraft.world.entity.AgeableMob ageable) {
			ageable.setBaby(true); // 幼体
		}
		// 往视线方向弹出去一点，像刚孵出来
		spawned.setDeltaMovement(player.getLookAngle().scale(0.25).add(0.0, 0.2, 0.0));
		level.addFreshEntity(spawned);
		level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.CHICKEN_EGG,
				net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.2f);
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		player.getCooldowns().addCooldown(stack.getItem(), 10);
	}

	/** 这张蛋是"谁下的"；不是我们标记过的蛋就返回 null（原版蛋照旧孵小鸡）。 */
	private static ResourceLocation eggSpecies(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.EGG)) {
			return null;
		}
		net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(NameTagNotifier.EGG_SPECIES_KEY)) {
			return null;
		}
		return ResourceLocation.tryParse(data.copyTag().getString(NameTagNotifier.EGG_SPECIES_KEY));
	}

	// ==================== 变成物品的那一支：右键拿起 ====================

	/**
	 * 生物变成物品之后，那个物品实体是"它变成的东西"，不是普通掉落物——
	 * 作者要求可以**右键拿起**，拿起来之后就是一件正常的物品，该怎么用怎么用。
	 */
	@SubscribeEvent
	public static void onPickUpTransformed(PlayerInteractEvent.EntityInteract event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!(event.getTarget() instanceof ItemEntity item)) {
			return;
		}
		if (!item.getPersistentData().getBoolean(NamedState.TAG_FROM_TRANSFORM)) {
			return;
		}
		ItemStack stack = item.getItem();
		if (stack.isEmpty()) {
			return;
		}
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		ItemStack taken = stack.copy();
		if (!player.getInventory().add(taken)) {
			player.drop(taken, false);
		}
		item.discard();
		player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
				net.minecraft.sounds.SoundSource.PLAYERS, 0.2f, 1.6f);
	}

	// ==================== 中途进来的玩家也要看得到转化过程 ====================

	@SubscribeEvent
	public static void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
		if (event.getTarget() instanceof LivingEntity living && NamedState.isNamed(living)
				&& event.getEntity() instanceof ServerPlayer player) {
			NamedTransformPacket.sendTo(player, living);
		}
	}

	/** 供自检/日志用：把名字直接解析一次，不产生任何副作用。 */
	public static String debugResolve(String rawName) {
		ResolvedName hit = NameResolver.resolve(rawName, null);
		return hit == null ? "null" : hit.token();
	}
}
