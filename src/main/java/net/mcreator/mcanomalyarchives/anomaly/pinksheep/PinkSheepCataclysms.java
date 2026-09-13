package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.mcreator.mcanomalyarchives.entity.PinkSheepEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * UO-012 幸运粉羊 · 最高档灾厄的【扩展池】。
 *
 * 最高档（Lv3，触发条件：贴身 3 格内、或攻击/远程命中粉羊）原本只有
 * 陨石雨与天基屠龙炮两种，见
 * {@link net.mcreator.mcanomalyarchives.events.PinkSheepCalamityHandler}。
 * 这里再补 4 种，风格统一为"无预警、当场要命、事后有痕迹"：
 *
 * <ol>
 * <li>{@link #arrowStorm} 万箭穿心：14 支高速箭从四面八方同时射向玩家</li>
 * <li>{@link #anvilRain} 天罚铁砧：24 块铁砧从头顶砸下（不留方块，只留血）</li>
 * <li>{@link #witherStorm} 凋灵风暴：1 凋灵 + 4 下界合金凋灵骷髅</li>
 * <li>{@link #enderAmbush} 末影伏击：6 只末影人围成一圈同时锁定</li>
 * </ol>
 *
 * 设计约束（沿用之前的决定）：
 * - 不给玩家任何预警时间，所以这些方法自己不出"准备音"（落地/出现音属于演出的一部分）；
 * - 伤害要能穿过常规防护：靠"原始伤害足够高 + 多段命中"而不是无视护甲，
 *   唯一无视减伤的是陨石那套（那是单独的伤害类型）。
 *
 * 【工程层归属】anomaly 包（非 MCreator 生成区）。
 */
public final class PinkSheepCataclysms {

	private PinkSheepCataclysms() {
	}

	// ===== 1. 万箭穿心 =====

	/**
	 * 以玩家为中心，半径 6 格均匀站 14 支箭，全部指向玩家胸口，无重力、超高初速。
	 * 不设 owner（设了就打不到玩家），伤害靠 速度 5 × baseDamage 30 = 150 原始伤害。
	 */
	public static void arrowStorm(ServerPlayer player, ServerLevel level) {
		final int count = 14;
		final double radius = 6.0;
		Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		double offset = player.getRandom().nextDouble() * Math.PI * 2; // 每轮随机错开角度
		for (int i = 0; i < count; i++) {
			double angle = offset + (Math.PI * 2 * i / count);
			Vec3 spawn = new Vec3(player.getX() + Math.cos(angle) * radius, player.getY() + 1.0 + (i % 3) * 0.6,
					player.getZ() + Math.sin(angle) * radius);
			var arrow = EntityType.ARROW.create(level);
			if (arrow == null)
				continue;
			arrow.setPos(spawn.x, spawn.y, spawn.z);
			arrow.setDeltaMovement(center.subtract(spawn).normalize().scale(5.0));
			arrow.setNoGravity(true);
			arrow.setBaseDamage(30.0);
			level.addFreshEntity(arrow);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 2.0F, 0.6F);
	}

	// ===== 2. 天罚铁砧 =====

	/**
	 * 玩家头顶 18 格、7×7 范围里砸下 24 块铁砧。
	 * 每块按"每格坠落 20 点、上限 400"结算伤害 —— 常规盔甲挡不住，且多块会连续落。
	 * disableDrop()：落地不留铁砧方块，避免把玩家家拆了。
	 */
	public static void anvilRain(ServerPlayer player, ServerLevel level) {
		final int count = 24;
		for (int i = 0; i < count; i++) {
			double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 7.0;
			double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 7.0;
			double y = player.getY() + 14.0 + player.getRandom().nextDouble() * 8.0;
			BlockPos pos = BlockPos.containing(x, y, z);
			FallingBlockEntity anvil = FallingBlockEntity.fall(level, pos, Blocks.ANVIL.defaultBlockState());
			anvil.setHurtsEntities(20.0F, 400);
			anvil.disableDrop(); // 只砸人，不留方块
			anvil.setDeltaMovement((player.getRandom().nextDouble() - 0.5) * 0.05, -0.6,
					(player.getRandom().nextDouble() - 0.5) * 0.05);
			level.addFreshEntity(anvil);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0F, 0.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0F, 0.7F);
	}

	// ===== 3. 凋灵风暴 =====

	/** 1 只凋灵 + 4 只全套下界合金、带速度的凋灵骷髅，全部锁定玩家 */
	public static void witherStorm(ServerPlayer player, ServerLevel level) {
		WitherBoss wither = EntityType.WITHER.create(level);
		if (wither != null) {
			spawnRing(player, level, wither, 6.0);
			wither.setTarget(player);
		}
		for (int i = 0; i < 4; i++) {
			WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level);
			if (skeleton == null)
				continue;
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
			skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
			skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			skeleton.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 0));
			spawnRing(player, level, skeleton, 9.0);
			skeleton.setTarget(player);
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 60, 3.0, 1.5,
				3.0, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.8F);
	}

	// ===== 4. 末影伏击 =====

	/** 6 只末影人围成一圈，全部锁定玩家（末影人会瞬移贴脸，压迫感来自"到处都是"） */
	public static void enderAmbush(ServerPlayer player, ServerLevel level) {
		final int count = 6;
		for (int i = 0; i < count; i++) {
			EnderMan enderman = EntityType.ENDERMAN.create(level);
			if (enderman == null)
				continue;
			spawnRing(player, level, enderman, 10.0);
			enderman.setTarget(player);
		}
		level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 120, 4.0, 2.0, 4.0,
				0.5);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 2.0F, 0.6F);
	}

	// ===== 工具 =====

	/**
	 * 把附近的粉羊先转移走。
	 *
	 * 为什么必须有：贴身 3 格触发的最高档灾厄里，粉羊**不会**自己转移（只有被攻击时才转移），
	 * 而万箭穿心/铁砧雨这类范围打击会顺手把它打死（粉羊只有 20 血，一箭 150 原始伤害）——
	 * 结果就是"遭遇还没开始，本体先没了"。所以最高档灾厄开打前统一把它请走。
	 */
	public static void clearNearbySheep(ServerPlayer player, ServerLevel level) {
		for (PinkSheepEntity sheep : level.getEntitiesOfClass(PinkSheepEntity.class,
				player.getBoundingBox().inflate(16.0))) {
			if (sheep.isAlive())
				PinkSheepMechanics.teleportAway(sheep);
		}
	}

	/** 在玩家周围 ring 半径处放一只怪（高度取玩家脚下的地表，避免埋进地里） */
	private static void spawnRing(ServerPlayer player, ServerLevel level, Mob mob, double ring) {
		double angle = player.getRandom().nextDouble() * Math.PI * 2;
		double x = player.getX() + Math.cos(angle) * ring;
		double z = player.getZ() + Math.sin(angle) * ring;
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
				net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
		mob.moveTo(x + 0.5, Math.max(y, player.getY()), z + 0.5, player.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
	}

	/** 供灾厄分发处使用：这一档一共有几种（含陨石雨与天基屠龙炮） */
	public static final int VARIANT_COUNT = 6;

	/** 抽一种最高档灾厄的编号（0/1 留给陨石雨与天基屠龙炮） */
	public static int rollVariant(ServerPlayer player) {
		return player.getRandom().nextInt(VARIANT_COUNT);
	}

	/** 编号 2..5 在这里执行；0/1 由 PinkSheepCalamityHandler 自己处理 */
	public static boolean run(ServerPlayer player, ServerLevel level, int variant) {
		switch (variant) {
			case 2 -> arrowStorm(player, level);
			case 3 -> anvilRain(player, level);
			case 4 -> witherStorm(player, level);
			case 5 -> enderAmbush(player, level);
			default -> {
				return false;
			}
		}
		return true;
	}
}
