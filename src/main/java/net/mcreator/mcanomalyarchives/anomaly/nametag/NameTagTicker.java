package net.mcreator.mcanomalyarchives.anomaly.nametag;

import net.mcreator.mcanomalyarchives.anomaly.nametag.effects.EntityNaming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
 * 被命名实体的"行为覆盖层"——每个 tick 驱动它们的产出、行动与寿命。
 *
 * 【工程层归属】anomaly.nametag 包（非 MCreator 生成区）。
 *
 * 为什么用 {@link EntityTickEvent.Post} 而不是自己扫实体列表：它是逐实体触发的，
 * 我们只要在最前面用廉价条件挡掉绝大多数实体即可。代价是每 tick 都会走一遍，
 * 所以第一道判据必须是 {@code tickCount % STRIDE}（不碰 NBT）。
 */
public final class NameTagTicker {

	/** 每多少 tick 处理一次被命名实体（4 次/秒足够）。 */
	private static final int STRIDE = 5;
	/** 没有具体产出的身份：维持这种"被改写过的存在"也要付出代价。 */
	private static final int IDLE_LIFESPAN_INTERVAL = 200;
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
		switch (target.kind()) {
			case BLOCK -> tickBlockIdentity(level, living, target);
			case ITEM -> tickItemIdentity(level, living, target);
			case ENTITY -> tickEntityIdentity(level, living, target);
		}
	}

	// ===== 实体 ← 实体名：行为/产出覆盖 =====

	private static void tickEntityIdentity(ServerLevel level, LivingEntity living, ResolvedName target) {
		if (EntityNaming.isSelfIdentity(living)) {
			// 名字就是它自己（牛→"牛"）：身份没被改写，不该按"改写过的存在"收寿命
			return;
		}
		ResourceLocation id = target.id();
		if (is(id, "minecraft:chicken")) {
			// 正片：牛被命名成"鸡"后开始下出深褐色的牛蛋，牛蛋可以孵出正常的牛幼仔
			if (living.tickCount % EGG_INTERVAL == 0) {
				layEgg(level, living);
				consumeAction(level, living);
			}
			return;
		}
		if (is(id, "minecraft:cow")) {
			// 奶牛的产出（挤奶）走交互拦截，这里只维持存在
			idleCost(level, living);
			return;
		}
		idleCost(level, living);
	}

	// ===== 实体 ← 物品名：行为移植（正片猪→钻石镐） =====

	private static void tickItemIdentity(ServerLevel level, LivingEntity living, ResolvedName target) {
		ItemStack model = new ItemStack(BuiltInRegistries.ITEM.get(target.id()));
		if (model.has(DataComponents.TOOL)) {
			// 有行为的名字（工具）→ 行为移植：正片里猪被命名成"钻石镐"后开始挖矿，挖到死
			dig(level, living);
			return;
		}
		if (is(target.id(), "minecraft:potato") || is(target.id(), "minecraft:poisonous_potato")
				|| is(target.id(), "minecraft:baked_potato")) {
			// 正片开场事故：宠物狗被命名成"土豆"后瞬间丧失所有动物活性、遗体长出土豆嫩芽
			EntityNaming.toPotato(level, living);
			return;
		}
		// 没有行为的名字（材料）→ 完全转换：它就是那个东西本身
		EntityNaming.convertToMaterial(level, living, target);
	}

	// ===== 实体 ← 方块名：质料守恒 · 延迟掠夺转化（正片羊→金块） =====

	private static void tickBlockIdentity(ServerLevel level, LivingEntity living, ResolvedName target) {
		int need = MaterialUnits.requirement(target);
		if (living.tickCount % NameTagCosts.DRAIN_INTERVAL_TICKS != 0) {
			return;
		}
		if (NamedState.progressOf(living) >= need) {
			EntityNaming.completeBlockConversion(level, living, target);
			return;
		}
		BlockPos source = EntityNaming.findDrainable(level, living, target);
		if (source == null) {
			// 抢不到材料：正片里这一步会演变成爆炸；这里温和降级为"继续等"，不再额外惩罚
			spawnDrainHint(level, living);
			return;
		}
		if (EntityNaming.drain(level, source)) {
			NamedState.addProgress(living, NameTagCosts.DRAIN_PROGRESS_PER_BLOCK);
		}
	}

	// ===== 具体动作 =====

	/** 下一个"蛋"：名字跟着原生物走（牛 → 牛蛋），正片里这些蛋能孵出正常的牛幼仔。 */
	private static void layEgg(ServerLevel level, LivingEntity living) {
		ItemStack egg = new ItemStack(Items.EGG);
		String original = living.getType().getDescription().getString();
		egg.set(DataComponents.CUSTOM_NAME, Component.translatable("nametag.mcanomalyarchives.egg", original));
		ItemEntity drop = new ItemEntity(level, living.getX(), living.getY() + 0.3, living.getZ(), egg);
		drop.setDeltaMovement(0.0, 0.1, 0.0);
		level.addFreshEntity(drop);
		level.playSound(null, living.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.7f, 1.0f);
	}

	/** 走到最近的石头/矿石旁挖掉它——正片里的猪"不顾一切寻找附近的石头和矿石"。 */
	private static void dig(ServerLevel level, LivingEntity living) {
		BlockPos pos = findDigTarget(level, living);
		if (pos == null) {
			idleCost(level, living);
			return;
		}
		if (living instanceof Mob mob) {
			mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
		}
		if (living.position().distanceToSqr(Vec3.atCenterOf(pos)) <= DIG_REACH_SQR) {
			level.destroyBlock(pos, false); // 正片：它只是挖，不是替你收集
			consumeAction(level, living);
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

	/** 每次"按新身份行动"扣一点寿命，扣完就死（正片：牛在多次产蛋后痛苦地猝死）。 */
	private static void consumeAction(ServerLevel level, LivingEntity living) {
		EntityNaming.spendAction(level, living);
	}

	private static void idleCost(ServerLevel level, LivingEntity living) {
		if (living.tickCount % IDLE_LIFESPAN_INTERVAL == 0) {
			consumeAction(level, living);
		}
	}

	private static void spawnDrainHint(ServerLevel level, LivingEntity living) {
		if (living.tickCount % 100 == 0) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
					living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
		}
	}

	private static boolean is(ResourceLocation id, String expected) {
		return id.toString().equals(expected);
	}
}
