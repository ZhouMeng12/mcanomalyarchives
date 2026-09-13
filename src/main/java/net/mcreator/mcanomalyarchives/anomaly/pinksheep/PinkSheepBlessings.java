package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * UO-012 幸运粉羊 · 目击幸运的【扩展奇遇池】。
 *
 * 基础的 5 个奇遇（喷溅药水 / 经验雨 / 瞬熟闪光 / 天雷劈怪 / 投喂宝物）留在
 * {@link net.mcreator.mcanomalyarchives.events.PinkSheepLuckyHandler} 里，这里补 5 个：
 *
 * <ol>
 * <li>{@link #luckyOreVein} 爆破矿脉：脚下第 2、3 格换成 TNT，炸开地面把你送下去 ——
 * 下面整片地层已经全是矿（先炸后埋，见方法注释）</li>
 * <li>{@link #feast} 丰饶：饥饿与生命补满 + 熟食</li>
 * <li>{@link #flockOfSheep} 同类聚集：身边冒出一小群羊</li>
 * <li>{@link #dawnBreak} 黎明降临：夜里直接天亮（怪物开始烧）</li>
 * <li>{@link #monstersIntoSheep} 敌意消解：附近的敌对怪被"变成"羊</li>
 * <li>{@link #fallingChestMinecart} 天降箱子矿车：一辆装满矿石的矿车从天上砸下来</li>
 * </ol>
 *
 * 【工程层归属】anomaly 包（非 MCreator 生成区）。
 */
public final class PinkSheepBlessings {

	private PinkSheepBlessings() {
	}

	// ===== 1. 爆破矿脉（TNT 掉进矿洞）=====

	/** 预埋矿脉的密度（只作用于天然石层） */
	private static final float ORE_CHANCE = 0.18F;

	/** 只破坏方块、不伤害任何实体的爆炸计算器："幸运"事件不能把玩家自己炸伤 */
	private static final ExplosionDamageCalculator NO_ENTITY_DAMAGE = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return false;
		}

		@Override
		public float getEntityDamageAmount(Explosion explosion, Entity entity) {
			return 0.0F;
		}
	};

	/**
	 * 脚下第 2、3 格换成 TNT，炸开地面把你送下去 —— 而下面整片地层已经全是矿。
	 *
	 * <p>顺序很关键：<b>先炸、后埋矿</b>。反过来的话爆炸会把刚埋好的矿一起炸没，
	 * 玩家掉下去只看到一个空坑。所以：
	 * <ol>
	 * <li>脚下第 2、3 格换成 TNT 方块（真的放，回头挖还能看见自己踩过 TNT）；</li>
	 * <li>用【不伤害实体】的爆炸掀开地面（{@link #NO_ENTITY_DAMAGE}）——
	 * 这是幸运事件，只保留破坏方块；</li>
	 * <li>再往周围的石头/深板岩里埋矿（<b>只替换天然石层</b>，绝不动玩家建筑）；</li>
	 * <li>玩家掉进坑里，四面的墙全是矿。</li>
	 * </ol>
	 */
	public static void luckyOreVein(ServerPlayer player, ServerLevel level) {
		BlockPos feet = player.blockPosition();
		BlockPos tntTop = feet.below(2);   // 脚下第 2 格
		BlockPos tntDeep = feet.below(3);  // 脚下第 3 格

		// 1) 换成 TNT
		level.setBlockAndUpdate(tntTop, Blocks.TNT.defaultBlockState());
		level.setBlockAndUpdate(tntDeep, Blocks.TNT.defaultBlockState());
		level.playSound(null, feet, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.6F, 1.0F);

		// 2) 只炸方块、不伤实体的爆炸（半径 5 → 掀开约 4~5 格深）
		level.explode(null, null, NO_ENTITY_DAMAGE, tntTop.getX() + 0.5, tntTop.getY() + 0.5, tntTop.getZ() + 0.5,
				5.0F, false, Level.ExplosionInteraction.TNT);
		level.playSound(null, feet, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.4F, 0.9F);

		// 3) 炸完再埋矿：11×11、脚下 -2 ~ -12，只替换 stone/deepslate
		int seeded = 0;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				for (int dy = -2; dy >= -12; dy--) {
					BlockPos pos = feet.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					boolean deepslate = state.is(Blocks.DEEPSLATE);
					if (!deepslate && !state.is(Blocks.STONE))
						continue; // 只动天然石层
					if (player.getRandom().nextFloat() >= ORE_CHANCE)
						continue;
					level.setBlockAndUpdate(pos, pickOre(player, deepslate).defaultBlockState());
					seeded++;
				}
			}
		}
		if (seeded < 5) {
			// 在空中/建筑里（脚下不是天然地层）→ 退化成投喂，别让"幸运"落空
			player.drop(new ItemStack(Items.DIAMOND, 1), false, false);
			return;
		}

		// 4) 掉下去之后的"发现"演出
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, feet.getX() + 0.5, feet.getY() - 1.5, feet.getZ() + 0.5, 80,
				3.0, 2.0, 3.0, 0.15);
		level.playSound(null, feet, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.4F);
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

	// ===== 2. 丰饶 =====

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

	// ===== 3. 同类聚集 =====

	/** 身边冒出一小群羊 —— 粉羊的"同类"来打个招呼（就是普通羊，纯氛围 + 羊毛来源） */
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
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			sheep.moveTo(x + 0.5, Math.max(y, player.getY()), z + 0.5, player.getRandom().nextFloat() * 360.0F, 0.0F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sheep.getX(), sheep.getY() + 0.6, sheep.getZ(), 12, 0.4,
					0.4, 0.4, 0.05);
			level.addFreshEntity(sheep);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.SHEEP_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.2F);
	}

	// ===== 4. 黎明降临 =====

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
			double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			level.sendParticles(ParticleTypes.END_ROD, x, y + 1.5, z, 1, 0.1, 0.4, 0.1, 0.01);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.4F, 1.1F);
	}

	// ===== 5. 敌意消解 =====

	/**
	 * 附近的敌对怪被"变成"羊（最多 3 只）：最像粉羊作风的幸运 —— 你的敌人不该存在。
	 * 排除凋灵/监守者（太强，不该被一句话抹掉），也不动被命名牌命名的怪（可能是谁的宠物）。
	 */
	public static void monstersIntoSheep(ServerPlayer player, ServerLevel level) {
		List<Monster> monsters = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(16.0),
				m -> m.isAlive() && m.getCustomName() == null
						&& !(m instanceof net.minecraft.world.entity.boss.wither.WitherBoss)
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
			// 附近没怪 → 换成丰饶，别让"幸运"落空
			feast(player, level);
		}
	}

	// ===== 6. 天降箱子矿车 =====

	/**
	 * 一辆装满矿石的箱子矿车从 28 格高空砸到你面前。
	 * 矿车自己不受摔落伤害（{@code Minecart#causeFallDamage} 返回 false），
	 * 所以它只会"咚"一声落地、等人来开。
	 */
	public static void fallingChestMinecart(ServerPlayer player, ServerLevel level) {
		MinecartChest cart = EntityType.CHEST_MINECART.create(level);
		if (cart == null)
			return;
		double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 3.0;
		double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 3.0;
		double y = player.getY() + 28.0;
		cart.setPos(x, y, z);
		cart.setDeltaMovement(0.0, -0.35, 0.0);
		fillOreChest(cart, player);
		cart.setCustomName(Component.translatable("entity.mcanomalyarchives.pink_sheep_gift"));
		level.addFreshEntity(cart);
		level.sendParticles(ParticleTypes.CLOUD, x, y, z, 40, 1.0, 0.6, 1.0, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.8, 0.4, 0.8, 0.02);
		level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.2F,
				0.9F);
	}

	/** 往箱子矿车里塞矿石（随机槽位、随机数量，7~9 种） */
	private static void fillOreChest(MinecartChest cart, ServerPlayer player) {
		List<ItemStack> pool = new ArrayList<>(List.of(
				new ItemStack(Items.COAL, 16 + player.getRandom().nextInt(17)),
				new ItemStack(Items.RAW_IRON, 8 + player.getRandom().nextInt(9)),
				new ItemStack(Items.RAW_GOLD, 6 + player.getRandom().nextInt(7)),
				new ItemStack(Items.REDSTONE, 12 + player.getRandom().nextInt(13)),
				new ItemStack(Items.LAPIS_LAZULI, 8 + player.getRandom().nextInt(9)),
				new ItemStack(Items.COPPER_INGOT, 12 + player.getRandom().nextInt(13)),
				new ItemStack(Items.IRON_INGOT, 4 + player.getRandom().nextInt(5)),
				new ItemStack(Items.DIAMOND, 2 + player.getRandom().nextInt(4)),
				new ItemStack(Items.EMERALD, 3 + player.getRandom().nextInt(6))));
		Collections.shuffle(pool, new Random(player.getRandom().nextLong()));
		int count = Math.min(pool.size(), 7 + player.getRandom().nextInt(3));
		Set<Integer> slots = new LinkedHashSet<>();
		while (slots.size() < count)
			slots.add(player.getRandom().nextInt(cart.getContainerSize()));
		int i = 0;
		for (int slot : slots) {
			cart.setItem(slot, pool.get(i % pool.size()));
			i++;
		}
	}
}
