package net.mcreator.mcanomalyarchives.anomaly.pinksheep;

import net.mcreator.mcanomalyarchives.entity.PinkSheepEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UO-012 幸运粉羊 · 最高档灾厄的【扩展池】。
 *
 * 最高档（Lv3；触发条件：贴身 3 格内，或攻击/远程命中粉羊）共 6 种，
 * 由 {@link net.mcreator.mcanomalyarchives.events.PinkSheepCalamityHandler} 分发：
 * <pre>
 *   0 陨石雨        PinkSheepMeteor（延迟 → 可见下坠 → 12 格爆炸 + 保底 200）
 *   1 天基屠龙炮    PinkSheepCalamityHandler#orbitalArrowRain
 *   2 天罚铁砧      本类 anvilRain            —— 24 块铁砧，每格 20 点、上限 400
 *   3 天罚·地毯轰炸  本类 tntCarpet           —— 5×5 共 25 颗 TNT 滚动爆炸
 *   4 末影龙火球集火  本类 dragonFireballVolley —— 6 颗紫火球同时汇聚
 *   5 末地水晶殉爆   本类 endCrystalChain      —— 8 颗水晶错峰殉爆（每颗 6 级爆炸）
 * </pre>
 *
 * 设计要点（都按"最高档 = 无预警、当场要命"来做）：
 * <ul>
 * <li><b>伤害要能真的打满</b>：MC 的受击无敌帧是 20 tick，同一瞬间的多段伤害只结算一次。
 * 所以多段设计一律做成【错峰】：水晶每 5 tick 一颗（40 tick 内连炸 8 次）、
 * TNT 引信 8~58 tick 依次起爆、铁砧先后落地 —— 保证 2 秒内命中 3~4 次。</li>
 * <li><b>出招前先请走粉羊本体</b>：{@link #clearNearbySheep}。贴身触发时它不会自己转移
 * （只有被攻击才转移），这些范围打击会顺手把它打死（粉羊只有 20 血）。</li>
 * <li>地形破坏沿用 TNT 等级（与陨石一致），砸完留痕。</li>
 * </ul>
 *
 * 【工程层归属】anomaly 包（非 MCreator 生成区）。
 */
public final class PinkSheepCataclysms {

	/** 这一档一共有几种（含陨石雨与天基屠龙炮） */
	public static final int VARIANT_COUNT = 6;

	/** 错峰引爆队列（末地水晶用）：到点了再引爆 */
	private static final List<PendingBlast> PENDING = new CopyOnWriteArrayList<>();

	private record PendingBlast(ServerLevel level, UUID crystal, double x, double y, double z, long detonateTick) {
	}

	private PinkSheepCataclysms() {
	}

	// ===== 2. 天罚铁砧 =====

	/**
	 * 玩家头顶 14~22 格、7×7 范围里砸下 24 块铁砧。
	 * 每块按"每格坠落 20 点、上限 400"结算 —— 常规盔甲挡不住，且铁砧先后落地会连续命中。
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
		level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0F,
				0.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0F, 0.7F);
	}

	// ===== 3. 天罚·地毯轰炸 =====

	/**
	 * 以玩家为中心 5×5（间隔 2 格）共 25 颗 TNT，引信 8~58 tick 依次起爆（滚动轰炸）。
	 * 单颗 TNT 是 4 级爆炸，25 颗错峰落地 → 无敌帧挡不住全部，玩家脚下会被犁成一整片焦土。
	 */
	public static void tntCarpet(ServerPlayer player, ServerLevel level) {
		int index = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				double x = player.getX() + dx * 2.0;
				double z = player.getZ() + dz * 2.0;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
				PrimedTnt tnt = EntityType.TNT.create(level);
				if (tnt == null)
					continue;
				tnt.setPos(x, Math.max(y, player.getY()), z);
				tnt.setFuse(8 + index * 2); // 错峰起爆：同一瞬间炸只结算一次伤害
				tnt.setDeltaMovement(0.0, 0.1, 0.0);
				level.addFreshEntity(tnt);
				index++;
			}
		}
		level.playSound(null, player.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 3.0F, 0.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0F,
				0.5F);
	}

	// ===== 4. 末影龙火球集火 =====

	/**
	 * 6 颗末影龙火球从半径 12、高 8 格的环上同时射向玩家。
	 * 龙火球是 3 级爆炸（范围极大、紫色爆闪、附带龙息），到达时间差在 1 秒内 → 至少两段命中。
	 */
	public static void dragonFireballVolley(ServerPlayer player, ServerLevel level) {
		final int count = 6;
		final double ring = 12.0;
		Vec3 aim = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		double offset = player.getRandom().nextDouble() * Math.PI * 2;
		for (int i = 0; i < count; i++) {
			double angle = offset + Math.PI * 2 * i / count;
			Vec3 spawn = new Vec3(player.getX() + Math.cos(angle) * ring, player.getY() + 8.0,
					player.getZ() + Math.sin(angle) * ring);
			DragonFireball fireball = EntityType.DRAGON_FIREBALL.create(level);
			if (fireball == null)
				continue;
			fireball.setPos(spawn.x, spawn.y, spawn.z);
			fireball.setDeltaMovement(aim.subtract(spawn).normalize().scale(0.9));
			level.addFreshEntity(fireball);
		}
		level.sendParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 1, player.getZ(), 80, 3.0, 1.5,
				3.0, 0.1);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 3.0F, 0.7F);
	}

	// ===== 5. 末地水晶殉爆 =====

	/**
	 * 玩家周围半径 5 摆 8 颗末地水晶，每 5 tick 引爆一颗。
	 * 单颗是 6 级爆炸（除陨石外最狠的一发），8 颗错峰 → 40 tick 内持续命中，无敌帧完全挡不住。
	 * 引爆走原版路径 {@code EndCrystal#hurt}（内部自己 remove + 爆炸 + 演出）。
	 */
	public static void endCrystalChain(ServerPlayer player, ServerLevel level) {
		final int count = 8;
		final double ring = 5.0;
		long now = level.getGameTime();
		int index = 0;
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2 * i / count;
			double x = player.getX() + Math.cos(angle) * ring;
			double z = player.getZ() + Math.sin(angle) * ring;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			double yy = Math.max(y, player.getY());
			EndCrystal crystal = EntityType.END_CRYSTAL.create(level);
			if (crystal == null)
				continue;
			crystal.setPos(x, yy, z);
			level.addFreshEntity(crystal);
			PENDING.add(new PendingBlast(level, crystal.getUUID(), x, yy, z, now + 4L + index * 5L));
			index++;
		}
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 100, 5.0, 2.0, 5.0,
				0.15);
		level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2.0F, 0.6F);
	}

	/** 错峰引爆推进（由 PinkSheepCalamityHandler 的 ServerTickEvent 每 tick 调用） */
	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty())
			return;
		for (PendingBlast blast : PENDING) {
			if (server.getLevel(blast.level().dimension()) != blast.level()) {
				PENDING.remove(blast);
				continue;
			}
			if (blast.level().getGameTime() < blast.detonateTick())
				continue;
			PENDING.remove(blast);
			Entity entity = blast.level().getEntities().get(blast.crystal());
			if (entity instanceof EndCrystal crystal && !crystal.isRemoved()) {
				crystal.hurt(blast.level().damageSources().generic(), 1.0F); // 原版引爆路径
			} else {
				// 水晶被提前清掉了 → 原地补一发同级爆炸，别让"说好的灾厄"落空
				blast.level().explode(null, blast.x(), blast.y(), blast.z(), 6.0F, true,
						Level.ExplosionInteraction.TNT);
			}
		}
	}

	// ===== 工具 =====

	/**
	 * 把附近的粉羊先转移走。
	 *
	 * 为什么必须有：贴身 3 格触发的最高档灾厄里，粉羊**不会**自己转移（只有被攻击时才转移），
	 * 而这些范围打击会顺手把它打死（粉羊只有 20 血）——结果"遭遇还没开始，本体先没了"。
	 */
	public static void clearNearbySheep(ServerPlayer player, ServerLevel level) {
		for (PinkSheepEntity sheep : level.getEntitiesOfClass(PinkSheepEntity.class,
				player.getBoundingBox().inflate(16.0))) {
			if (sheep.isAlive())
				PinkSheepMechanics.teleportAway(sheep);
		}
	}

	/** 抽一种最高档灾厄的编号（0/1 留给陨石雨与天基屠龙炮） */
	public static int rollVariant(ServerPlayer player) {
		return player.getRandom().nextInt(VARIANT_COUNT);
	}

	/** 编号 2..5 在这里执行；0/1 由 PinkSheepCalamityHandler 自己处理 */
	public static boolean run(ServerPlayer player, ServerLevel level, int variant) {
		switch (variant) {
			case 2 -> anvilRain(player, level);
			case 3 -> tntCarpet(player, level);
			case 4 -> dragonFireballVolley(player, level);
			case 5 -> endCrystalChain(player, level);
			default -> {
				return false;
			}
		}
		return true;
	}
}
