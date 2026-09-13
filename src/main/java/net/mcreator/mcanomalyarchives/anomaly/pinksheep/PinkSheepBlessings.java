package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * UO-012 幸运粉羊 · 目击幸运的【扩展奇遇池】。
 *
 * 原本的 5 个奇遇（喷溅药水 / 经验雨 / 瞬熟闪光 / 天雷劈怪 / 投喂宝物）留在
 * {@link net.mcreator.mcanomalyarchives.events.PinkSheepLuckyHandler} 里，
 * 这里补 6 个"更有故事感"的：
 *
 * <ol>
 * <li>{@link #luckyOreVein} 幸运矿脉：脚下的石头里"凭空"出现一簇矿（只替换石头/深板岩，不动建筑）</li>
 * <li>{@link #guardianCharm} 护身符：吸收 + 抗性提升，一小段"怎么都打不死"的时间</li>
 * <li>{@link #feast} 丰饶：直接把饥饿与生命补满，再塞几份熟食</li>
 * <li>{@link #flockOfSheep} 同类聚集：身边冒出一小群羊（粉羊的"同类"来打个招呼）</li>
 * <li>{@link #dawnBreak} 黎明降临：如果是夜里，天直接亮了（怪物开始烧）</li>
 * <li>{@link #monstersIntoSheep} 敌意消解：附近的敌对怪被"变成"羊 —— 最像粉羊作风的一个</li>
 * </ol>
 *
 * 【工程层归属】anomaly 包（非 MCreator 生成区）。
 */
public final class PinkSheepBlessings {

	private PinkSheepBlessings() {
	}

	// ===== 1. 幸运矿脉 =====

	/** 在玩家附近的石头/深板岩里生成一簇矿（只替换石头与深板岩，绝不破坏玩家建筑） */
	public static void luckyOreVein(ServerPlayer player, ServerLevel level) {
		BlockPos base = player.blockPosition();
		int placed = 0;
		for (int attempt = 0; attempt < 60 && placed < 6; attempt++) {
			BlockPos pos = base.offset(player.getRandom().nextInt(11) - 5, player.getRandom().nextInt(8) - 5,
					player.getRandom().nextInt(11) - 5);
			BlockState state = level.getBlockState(pos);
			boolean deepslate = state.is(Blocks.DEEPSLATE);
			if (!deepslate && !state.is(Blocks.STONE))
				continue; // 只动天然石头
			Block ore = pickOre(player, deepslate);
			level.setBlockAndUpdate(pos, ore.defaultBlockState());
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6,
					0.3, 0.3, 0.3, 0.02);
			placed++;
		}
		if (placed > 0) {
			level.playSound(null, base, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
		} else {
			// 附近全是人工方块（比如在空中/在建筑里）→ 退化成投喂宝物，别让"幸运"落空
			player.drop(new ItemStack(Items.IRON_INGOT, 2), false, false);
		}
	}

	private static Block pickOre(ServerPlayer player, boolean deepslate) {
		int r = player.getRandom().nextInt(10);
		if (r < 3)
			return deepslate ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
		if (r < 5)
			return deepslate ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
		if (r < 7)
			return deepslate ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
		if (r < 9)
			return deepslate ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
		return deepslate ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
	}

	// ===== 2. 护身符 =====

	/** 吸收 + 抗性提升：一段"挨打也不太疼"的时间（不直接回血，留给后面的丰饶） */
	public static void guardianCharm(ServerPlayer player, ServerLevel level) {
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 1));        // 4 颗吸收心，60s
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0));  // 抗性 I，15s
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.8,
				1.0, 0.8, 0.3);
		level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	// ===== 3. 丰饶 =====

	/** 饥饿与生命直接补满，再塞几份熟食（"运气好到刚好有饭吃"） */
	public static void feast(ServerPlayer player, ServerLevel level) {
		player.getFoodData().eat(20, 1.0F);
		player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 0));
		player.heal(6.0F);
		ItemStack[] food = { new ItemStack(Items.COOKED_BEEF, 4), new ItemStack(Items.BREAD, 6),
				new ItemStack(Items.GOLDEN_CARROT, 3), new ItemStack(Items.COOKED_SALMON, 4) };
		player.drop(food[player.getRandom().nextInt(food.length)], false, false);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 20, 1.0, 1.0,
				1.0, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	// ===== 4. 同类聚集 =====

	/** 身边冒出一小群羊 —— 粉羊的"同类"来打个招呼（就是普通羊，不掉好东西，纯氛围 + 羊毛来源） */
	public static void flockOfSheep(ServerPlayer player, ServerLevel level) {
		int count = 3 + player.getRandom().nextInt(3);
		for (int i = 0; i < count; i++) {
			Sheep sheep = EntityType.SHEEP.create(level);
			if (sheep == null)
				continue;
			double angle = player.getRandom().nextDouble() * Math.PI * 2;
			double dist = 3.0 + player.getRandom().nextDouble() * 4.0;
			double x = player.getX() + Math.cos(angle) * dist;
			double z = player.getZ() + Math.sin(angle) * dist;
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
					net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
			sheep.moveTo(x + 0.5, Math.max(y, player.getY()), z + 0.5, player.getRandom().nextFloat() * 360.0F, 0.0F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sheep.getX(), sheep.getY() + 0.6, sheep.getZ(), 12, 0.4,
					0.4, 0.4, 0.05);
			level.addFreshEntity(sheep);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.SHEEP_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.2F);
	}

	// ===== 5. 黎明降临 =====

	/** 如果是夜里，天直接亮到清晨 —— 怪物开始燃烧，算是最实用的一种"幸运" */
	public static void dawnBreak(ServerPlayer player, ServerLevel level) {
		long time = level.getDayTime();
		long dayPart = time % 24000L;
		if (dayPart >= 13000L) {
			level.setDayTime(time - dayPart + 1000L); // 推到当天清晨
		}
		for (int i = 0; i < 60; i++) {
			double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 14;
			double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 14;
			double y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
					net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
			level.sendParticles(ParticleTypes.END_ROD, x, y + 1.5, z, 1, 0.1, 0.4, 0.1, 0.01);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.4F, 1.1F);
	}

	// ===== 6. 敌意消解 =====

	/** 附近的敌对怪被"变成"羊（最多 3 只）：最像粉羊作风的幸运 —— 你的敌人不该存在 */
	public static void monstersIntoSheep(ServerPlayer player, ServerLevel level) {
		List<Monster> monsters = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(16.0),
				m -> m.isAlive() && !(m instanceof net.minecraft.world.entity.boss.wither.WitherBoss)
						&& !(m instanceof net.minecraft.world.entity.monster.warden.Warden));
		int done = 0;
		for (Monster monster : monsters) {
			if (done >= 3)
				break;
			double x = monster.getX(), y = monster.getY(), z = monster.getZ();
			monster.discard(); // 不是"杀死"，是"它本来就不该在这儿"
			Sheep sheep = EntityType.SHEEP.create(level);
			if (sheep == null)
				continue;
			sheep.moveTo(x, y, z, player.getRandom().nextFloat() * 360.0F, 0.0F);
			level.sendParticles(ParticleTypes.CLOUD, x, y + 0.6, z, 20, 0.4, 0.6, 0.4, 0.08);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.8, z, 10, 0.4, 0.4, 0.4, 0.05);
			level.addFreshEntity(sheep);
			done++;
		}
		if (done > 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.SHEEP_AMBIENT, SoundSource.NEUTRAL, 1.2F, 0.8F);
		} else {
			// 附近没怪 → 换成护身符，别让"幸运"落空
			guardianCharm(player, level);
		}
	}

}
