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
		if (target.kind() != ResolvedName.Kind.BLOCK) {
			// 实体 / 物品那两支都是**命名瞬间**就完成的（换 AI / 变成物品），
			// 这里只补 Goal 换不来的部分——鸡的下蛋计时器写在 aiStep 里，行为目标替换带不过来。
			tickSpecialBehavior(level, living, target);
			return;
		}
		tickBlockTransform(level, living, target);
	}

	/**
	 * 变成方块的那一支：唯一保留过程的。
	 *
	 * 立刻失去 AI（僵住）→ 沿时间轴推进、身体材质与方块贴图做真正的交叉溶解 →
	 * 材料够就走完、原地变成那个方块；不够就停在最后一步之前持续从周围掠夺（正片羊→金块）。
	 */
	private static void tickBlockTransform(ServerLevel level, LivingEntity living, ResolvedName target) {
		long now = level.getGameTime();
		EntityNaming.loseVitality(living);
		if (!NameTagTransform.isTransforming(living)) {
			startTransform(level, living, target, now);
		}
		// 夺取附近的同类材料：金块消除、金矿变石头、金装备没收。
		// 作者定的规则是"有就夺、没有就算了，但照样要换" —— 所以它**不是**完成条件。
		if (living.tickCount % NameTagCosts.DRAIN_INTERVAL_TICKS == 0) {
			EntityNaming.seizeNearby(level, living, target);
		}

		float progress = NameTagTransform.progressOf(living, now);
		int stage = NameTagTransform.stageOf(progress);
		if (stage > NameTagTransform.lastStage(living)) {
			NameTagTransform.setLastStage(living, stage);
			onStage(level, living, target, stage);
		}
		// 时间到就换，不再看材料够不够
		if (progress >= 1.0f) {
			complete(level, living, target);
		}
	}

	/** Goal 换不来的那点东西：靠我们自己的 tick 层补。 */
	private static void tickSpecialBehavior(ServerLevel level, LivingEntity living, ResolvedName target) {
		if (target.kind() != ResolvedName.Kind.ENTITY) {
			return;
		}
		if (EntityNaming.isSelfIdentity(living)) {
			// 名字就是它自己（牛→"牛"）：什么都没变
			return;
		}
		if (is(target.id(), "minecraft:chicken") && living.tickCount % EGG_INTERVAL == 0) {
			// 正片：牛被命名成"鸡"后开始下出深褐色的牛蛋，牛蛋可以孵出正常的牛幼仔
			layEgg(level, living);
		}
	}

	public static void startTransform(ServerLevel level, LivingEntity living, ResolvedName target, long now) {
		int duration = NameTagTransform.durationTicks(NamedState.displayOf(living),
				MaterialUnits.budgetOf(living), MaterialUnits.requirement(target));
		NameTagTransform.start(living, now, duration);
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

	/** 变方块这一支的收尾（实体/物品那两支都在命名瞬间就完成了，不走这里）。 */
	private static void complete(ServerLevel level, LivingEntity living, ResolvedName target) {
		EntityNaming.completeBlockConversion(level, living, target);
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

	private static boolean is(ResourceLocation id, String expected) {
		return id.toString().equals(expected);
	}
}
