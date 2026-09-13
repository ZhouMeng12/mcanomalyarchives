package net.mcreator.mcanomalyarchives.events;

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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

/**
 * UO-012 幸运粉羊 · 目击幸运（Phase B）。
 * 玩家"目击确认"粉羊时（由 PinkSheepLookHandler.encounterCallback 触发）：
 * 1. 直接给予原版幸运效果（luck，60s）；
 * 2. 从随机奇遇池抽一件"看似巧合"的好事在玩家附近上演。
 * 同一次目击（同一羊 encounter）只发一次；羊转移/重生后可再得。
 */
public class PinkSheepLuckyHandler {

	// 目击确认后发幸运，LookHandler 保证 per-encounter 一次
	public static void init() {
		PinkSheepLookHandler.encounterCallback = PinkSheepLuckyHandler::grantEncounterLuck;
	}

	private static void grantEncounterLuck(ServerPlayer player, PinkSheepEntity sheep) {
		if (player == null || !player.isAlive())
			return;
		// 成就：幸运粉羊（目击并收到它的祝福）
		net.mcreator.mcanomalyarchives.anomaly.pinksheep.PinkSheepAdvancements.grantSighting(player);
		// 1. 原版幸运效果（60s = 1200 tick）
		player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 0));
		// 2. 随机奇遇
		rollFortunateEvent(player, sheep.level());
	}

	// ===== 随机奇遇池（每次 roll 一件）=====
	private static void rollFortunateEvent(ServerPlayer player, Level level) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		double r = player.getRandom().nextDouble();
		if (r < 0.30) {
			// 飞来正面喷溅药水（随机正面效果）
			flyingBuffPotion(player, serverLevel);
		} else if (r < 0.50) {
			// 经验雨
			experienceRain(player, serverLevel);
		} else if (r < 0.65) {
			// 附近作物瞬熟（简化：给玩家短暂急迫+幸运闪光代替，避免大范围改方块）
			quickGrowFlash(player, serverLevel);
		} else if (r < 0.80) {
			// 附近一只怪物被"天雷"劈中
			strikeNearbyMonster(player, serverLevel);
		} else {
			// 随机投喂一件小奖励（铁锭/金锭/钻石/青金石/绿宝石）
			droppedTreasure(player, serverLevel);
		}
	}

	/** 随机正面效果的喷溅药水从高处飞来砸中玩家（幸运是"奇遇"，不是 luck 本身） */
	private static void flyingBuffPotion(ServerPlayer player, ServerLevel level) {
		var effects = new net.minecraft.core.Holder<?>[] {
				MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_BOOST, MobEffects.DIG_SPEED,
				MobEffects.JUMP, MobEffects.REGENERATION, MobEffects.FIRE_RESISTANCE
		};
		@SuppressWarnings("unchecked")
		net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> pick =
				(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) effects[player.getRandom().nextInt(effects.length)];
		ItemStack potion = new ItemStack(Items.SPLASH_POTION);
		PotionContents p = new PotionContents(java.util.Optional.of(Potions.WATER), java.util.Optional.empty(),
				java.util.List.of(new MobEffectInstance(pick, 600, 0)));
		potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, p);

		net.minecraft.world.entity.projectile.ThrownPotion thrown = new net.minecraft.world.entity.projectile.ThrownPotion(
				level, player);
		thrown.setItem(potion);
		// 从玩家斜上方 10 格 "恰好"飞向玩家（模拟远处丢来）
		double sx = player.getX() + (player.getRandom().nextDouble() - 0.5) * 6;
		double sy = player.getY() + 8 + player.getRandom().nextDouble() * 4;
		double sz = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 6;
		thrown.setPos(sx, sy, sz);
		// 速度指向玩家位置
		double dx = player.getX() - sx, dy = (player.getY() + 1) - sy, dz = player.getZ() - sz;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		thrown.setDeltaMovement(dx / len * 1.4, dy / len * 1.4, dz / len * 1.4);
		level.addFreshEntity(thrown);
	}

	private static void experienceRain(ServerPlayer player, ServerLevel level) {
		for (int i = 0; i < 12; i++) {
			double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 4;
			double y = player.getY() + 1 + player.getRandom().nextDouble() * 2;
			double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 4;
			ExperienceOrb orb = new ExperienceOrb(level, x, y, z,
					player.getRandom().nextInt(3) + 1);
			level.addFreshEntity(orb);
		}
		player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.2F);
	}

	private static void quickGrowFlash(ServerPlayer player, ServerLevel level) {
		// 视觉闪光 + 给 5s 急迫（表现"好运使万事顺利"）
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(),
				30, 1.5, 1.5, 1.5, 0.1);
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1));
		player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.4F);
	}

	private static void strikeNearbyMonster(ServerPlayer player, ServerLevel level) {
		// 找玩家 24 格内一只敌对怪，召雷劈它
		var monsters = level.getEntitiesOfClass(Monster.class,
				player.getBoundingBox().inflate(24), m -> m.isAlive() && m.distanceToSqr(player) < 24 * 24);
		if (!monsters.isEmpty()) {
			var target = monsters.get(player.getRandom().nextInt(monsters.size()));
			var bolt = EntityType.LIGHTNING_BOLT.create(level);
			if (bolt != null) {
				bolt.moveTo(target.getX(), target.getY(), target.getZ());
				bolt.setVisualOnly(true); // 只劈怪不炸玩家
				level.addFreshEntity(bolt);
			}
		} else {
			droppedTreasure(player, level); // 没怪就改掉宝
		}
	}

	private static void droppedTreasure(ServerPlayer player, ServerLevel level) {
		ItemStack[] pool = {
				new ItemStack(Items.IRON_INGOT, player.getRandom().nextInt(3) + 1),
				new ItemStack(Items.GOLD_INGOT, player.getRandom().nextInt(2) + 1),
				new ItemStack(Items.DIAMOND, 1),
				new ItemStack(Items.EMERALD, player.getRandom().nextInt(3) + 1),
				new ItemStack(Items.LAPIS_LAZULI, player.getRandom().nextInt(8) + 2),
		};
		ItemStack drop = pool[player.getRandom().nextInt(pool.length)];
		player.drop(drop, false, false);
		player.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.3F);
	}
}
