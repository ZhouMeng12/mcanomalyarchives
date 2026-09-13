package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.mcreator.mcanomalyarchives.anomaly.nametag.effects.EntityNaming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 被命名实体的**行为接管 + 转化推进**。
 *
 * 【作者定的机制】被命名的生物会**慢慢变成**对应的生物。所以这里做两件并行的事：
 * <ol>
 *   <li><b>行为立刻接管</b>（正片：牛被命名成"鸡"后马上不能挤奶、马上开始下牛蛋）；</li>
 *   <li><b>转化沿时间轴推进</b>（{@link NameTagTransform}），走完 100% 且材料够时，
 *       原地替换成目标事物（{@link EntityNaming#replaceWith} 等）。</li>
 * </ol>
 *
 * 【质料不够就卡住】需要掠夺材料的目标（正片羊→金块）会一直停到最后一步之前，
 * 直到从周围凑够材料 —— 玩家得自己把材料搬过去。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 */
public final class NameTagTicker {

	/** 每多少 tick 处理一次被命名实体（4 次/秒足够）。 */
	private static final int STRIDE = 5;
	/** 产蛋间隔。 */
	private static final int EGG_INTERVAL = 120;
	/** 挖掘的搜索半径。 */
	private static final int DIG_RADIUS = 10;
	/** 挖掘判定距离（到目标方块中心）。 */
	private static final double DIG_REACH_SQR = 6.25;

	private NameTagTicker() {
	}

	public static void init() {
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(NameTagTicker.class);
	}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof LivingEntity living)) {
			return;
		}
		if (living.level().isClientSide()) {
			return;
		}
		if (living.tickCount % STRIDE != 0 || living.isDeadOrDying()) {
			return;
		}
		if (!NamedState.isNamed(living)) {
			return;
		}
		ResolvedName target = NamedState.targetOf(living);
		if (target == null) {
			return;
		}
		if (!(living.level() instanceof ServerLevel level)) {
			return;
		}
		tick(level, living, target);
	}

	private static void tick(ServerLevel level, LivingEntity living, ResolvedName target) {
		long now = level.getGameTime();

		// 时间轴：走过的存档（或刚被别处贴上标记的）在这里补一条
		if (!NameTagTransform.isTransforming(living)) {
			startTransform(level, living, target, now);
		}

		// 1) 行为接管：立刻生效
		switch (target.kind()) {
			case ENTITY -> tickEntityIdentity(level, living, target);
			case ITEM -> tickItemIdentity(level, living, target);
			case BLOCK -> tickDrain(level, living, target);
		}

		// 2) 转化推进
		float progress = NameTagTransform.progressOf(living, now);
		int stage = NameTagTransform.stageOf(progress);
		if (stage > NameTagTransform.lastStage(living)) {
			NameTagTransform.setLastStage(living, stage);
			onStage(level, living, target, stage);
		}

		// 3) 时间走完 + 材料够 → 完成
		if (progress >= 1.0f && materialReady(living, target)) {
			complete(level, living, target);
		}
	}

	public static void startTransform(ServerLevel level, LivingEntity living, ResolvedName target, long now) {
		int duration = NameTagTransform.durationTicks(NamedState.displayOf(living),
				MaterialUnits.budgetOf(living), MaterialUnits.requirement(target));
		NameTagTransform.start(living, now, duration);
	}

	// ===== 材料是否够（不够就一直掠夺，卡在最后一步之前） =====

	private static boolean materialReady(LivingEntity living, ResolvedName target) {
		int need = MaterialUnits.requirement(target);
		if (need <= 0) {
			return true;
		}
		return NamedState.progressOf(living) >= need;
	}

	// ===== 行为接管（正片里"立刻"发生的那部分） =====

	private static void tickEntityIdentity(ServerLevel level, LivingEntity living, ResolvedName target) {
		if (EntityNaming.isSelfIdentity(living)) {
			// 名字就是它自己（牛→"牛"）：身份没被改写，行为照旧，也不该被转化掉
			return;
		}
		ResourceLocation id = target.id();
		if (is(id, "minecraft:chicken")) {
			if (living.tickCount % EGG_INTERVAL == 0) {
				layEgg(level, living);
			}
		}
	}

	private static void tickItemIdentity(ServerLevel level, LivingEntity living, ResolvedName target) {
		ItemStack model = new ItemStack(BuiltInRegistries.ITEM.get(target.id()));
		if (model.has(net.minecraft.core.component.DataComponents.TOOL)) {
			// 有行为的名字（工具）→ 行为移植：正片里猪被命名成"钻石镐"后开始挖矿
			dig(level, living);
			return;
		}
		if (is(target.id(), "minecraft:potato") || is(target.id(), "minecraft:poisonous_potato")
				|| is(target.id(), "minecraft:baked_potato")) {
			// 正片开场事故：宠物狗被命名成"土豆"后瞬间丧失所有动物活性、遗体长出土豆嫩芽
			EntityNaming.toPotato(level, living);
		}
	}

	/** 需要材料：持续从附近抽走目标材料，累积进度。 */
	private static void tickDrain(ServerLevel level, LivingEntity living, ResolvedName target) {
		if (living.tickCount % NameTagCosts.DRAIN_INTERVAL_TICKS != 0) {
			return;
		}
		if (materialReady(living, target)) {
			return;
		}
		BlockPos source = EntityNaming.findDrainable(level, living, target);
		if (source == null) {
			return; // 附近没材料：停在那儿等玩家搬过来
		}
		if (EntityNaming.drain(level, source)) {
			NamedState.addProgress(living, NameTagCosts.DRAIN_PROGRESS_PER_BLOCK);
		}
	}

	// ===== 阶段表现：让玩家看得出"正在变" =====

	private static void onStage(ServerLevel level, LivingEntity living, ResolvedName target, int stage) {
		BlockPos pos = living.blockPosition();
		switch (stage) {
			case 1 -> {
				// 25%：开始不适——抽搐、惨叫
				level.sendParticles(ParticleTypes.SMOKE, living.getX(), living.getY() + living.getBbHeight() * 0.5,
						living.getZ(), 10, 0.3, 0.4, 0.3, 0.01);
				level.playSound(null, pos, SoundEvents.GENERIC_HURT, SoundSource.NEUTRAL, 0.7f, 0.6f);
			}
			case 2 -> {
				// 50%：挣扎加剧
				level.sendParticles(ParticleTypes.LARGE_SMOKE, living.getX(), living.getY() + living.getBbHeight() * 0.5,
						living.getZ(), 14, 0.3, 0.5, 0.3, 0.02);
				level.playSound(null, pos, SoundEvents.GENERIC_HURT, SoundSource.NEUTRAL, 0.8f, 0.5f);
			}
			case 3 -> {
				// 75%：身上开始出现目标材料的痕迹
				level.sendParticles(materialParticle(target), living.getX(), living.getY() + living.getBbHeight() * 0.6,
						living.getZ(), 18, 0.35, 0.5, 0.35, 0.03);
				level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.6f, 0.7f);
			}
			default -> {
			}
		}
	}

	private static net.minecraft.core.particles.SimpleParticleType materialParticle(ResolvedName target) {
		return switch (target.kind()) {
			case BLOCK, ITEM -> ParticleTypes.CRIT;
			case ENTITY -> ParticleTypes.END_ROD;
		};
	}

	// ===== 完成 =====

	private static void complete(ServerLevel level, LivingEntity living, ResolvedName target) {
		switch (target.kind()) {
			case ENTITY -> {
				if (!EntityNaming.replaceWith(level, living, target.id())) {
					// 目标不是生物（比如指向了刷怪蛋之类）→ 退化成"析出材料"
					EntityNaming.convertToMaterial(level, living, target);
				}
			}
			case BLOCK -> EntityNaming.completeBlockConversion(level, living, target);
			case ITEM -> EntityNaming.convertToMaterial(level, living, target);
		}
	}

	// ===== 具体动作 =====

	/** 下一个"蛋"：名字跟着原生物走（牛 → 牛蛋），正片里这些蛋能孵出正常的牛幼仔。 */
	private static void layEgg(ServerLevel level, LivingEntity living) {
		ItemStack egg = new ItemStack(Items.EGG);
		String original = living.getType().getDescription().getString();
		egg.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				Component.translatable(NameTagNotifier.EGG, original));
		ItemEntity drop = new ItemEntity(level, living.getX(), living.getY() + 0.3, living.getZ(), egg);
		drop.setDeltaMovement(0.0, 0.1, 0.0);
		level.addFreshEntity(drop);
		level.playSound(null, living.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.7f, 1.0f);
	}

	/** 走到最近的石头/矿石旁挖掉它——正片里的猪"不顾一切寻找附近的石头和矿石"。 */
	private static void dig(ServerLevel level, LivingEntity living) {
		BlockPos pos = findDigTarget(level, living);
		if (pos == null) {
			return;
		}
		if (living instanceof Mob mob) {
			mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
		}
		if (living.position().distanceToSqr(Vec3.atCenterOf(pos)) <= DIG_REACH_SQR) {
			level.destroyBlock(pos, false); // 正片：它只是挖，不是替你收集
		}
	}

	private static BlockPos findDigTarget(ServerLevel level, LivingEntity living) {
		BlockPos origin = living.blockPosition();
		BlockPos min = origin.offset(-DIG_RADIUS, -3, -DIG_RADIUS);
		BlockPos max = origin.offset(DIG_RADIUS, 2, DIG_RADIUS);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			Block block = level.getBlockState(pos).getBlock();
			if (block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.COBBLESTONE
					|| block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static boolean is(ResourceLocation id, String expected) {
		return id.toString().equals(expected);
	}
}
