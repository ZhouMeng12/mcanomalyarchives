package net.mcreator.mcanomalyarchives.events;

import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.advancements.AdvancementHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.events.SanityEventHandler;

import java.util.*;
import java.util.function.Supplier;
import java.lang.reflect.Field;

@EventBusSubscriber
public class StrangeFishingRodLootHandler {

	private static final Random RANDOM = new Random();
	private static boolean fieldsPrinted = false;

	// ========== 诡异度计算 ==========

	private static double calculateWeirdness(double distance) {
		// 第五档上限 15 格，2格内为安全区
		double base = Math.clamp((distance - 2.0) / 13.0, 0.0, 1.0);
		double noise = RANDOM.nextGaussian() * 0.1;
		return Math.clamp(base + noise, 0.0, 1.0);
	}

	// ========== 物品池 ==========

	private static final List<LootEntry> ITEM_POOL = new ArrayList<>();
	static {
		// Tier 0: 诡异度 0.00~0.30 — 基础建材、杂物、矿物、工具 (~2-6格)
		ITEM_POOL.add(new LootEntry(() -> Items.DIRT, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.STONE, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.COBBLESTONE, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.GRAVEL, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.SAND, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.STICK, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.STRING, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.BONE, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.ROTTEN_FLESH, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.WHEAT_SEEDS, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.OAK_SAPLING, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.LEATHER, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.IRON_INGOT, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.GOLD_INGOT, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.DIAMOND, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.EMERALD, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.COAL, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.REDSTONE, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.LAPIS_LAZULI, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.COPPER_INGOT, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.IRON_SWORD, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.BOW, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.FISHING_ROD, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.SADDLE, 0.00, 0.30));
		ITEM_POOL.add(new LootEntry(() -> Items.NAME_TAG, 0.00, 0.30));

		// Tier 1: 诡异度 0.25~0.50 — 末地/下界物品 (~5-9格)
		ITEM_POOL.add(new LootEntry(() -> Items.ENDER_PEARL, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.BLAZE_ROD, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.GHAST_TEAR, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.NETHER_WART, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.ENCHANTED_BOOK, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.DIAMOND_SWORD, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.TOTEM_OF_UNDYING, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.ELYTRA, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.NETHERITE_SCRAP, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.SPONGE, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.SLIME_BALL, 0.25, 0.50));
		ITEM_POOL.add(new LootEntry(() -> Items.PHANTOM_MEMBRANE, 0.25, 0.50));

		// Tier 2: 诡异度 0.45~0.75 — 稀有物品 (~8-12格)
		ITEM_POOL.add(new LootEntry(() -> Items.NETHER_STAR, 0.45, 0.75));
		ITEM_POOL.add(new LootEntry(() -> Items.DRAGON_EGG, 0.45, 0.75));
		ITEM_POOL.add(new LootEntry(() -> Items.DRAGON_HEAD, 0.45, 0.75));
		ITEM_POOL.add(new LootEntry(() -> Items.BEACON, 0.45, 0.75));
		ITEM_POOL.add(new LootEntry(() -> Items.SHULKER_SHELL, 0.45, 0.75));
		ITEM_POOL.add(new LootEntry(() -> Items.HEART_OF_THE_SEA, 0.45, 0.75));
		ITEM_POOL.add(new LootEntry(() -> Items.TRIDENT, 0.45, 0.75));
		ITEM_POOL.add(new LootEntry(() -> Items.NETHERITE_INGOT, 0.45, 0.75));
		ITEM_POOL.add(new LootEntry(() -> Items.ENCHANTED_GOLDEN_APPLE, 0.45, 0.75));

		// Tier 3: 诡异度 0.70~1.00 — 极限物品 (~11-15格)
		ITEM_POOL.add(new LootEntry(() -> Items.BEDROCK, 0.70, 1.00));
		ITEM_POOL.add(new LootEntry(() -> Items.STRUCTURE_BLOCK, 0.70, 1.00));
		ITEM_POOL.add(new LootEntry(() -> Items.COMMAND_BLOCK, 0.70, 1.00));
		ITEM_POOL.add(new LootEntry(() -> Items.BARRIER, 0.70, 1.00));
		ITEM_POOL.add(new LootEntry(() -> Items.LIGHT, 0.70, 1.00));
		ITEM_POOL.add(new LootEntry(() -> Items.JIGSAW, 0.70, 1.00));
		ITEM_POOL.add(new LootEntry(() -> Items.SPAWNER, 0.70, 1.00));

		// 本模组物品 Tier 2: 0.40~0.55
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.CORN_POPPY, 0.40, 0.55));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.SAD_POPPY, 0.40, 0.55));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.DEAD_POPPY, 0.40, 0.55));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.ORANGE, 0.40, 0.55));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.ORANGE_SAPLING, 0.40, 0.55));
		// 本模组物品 Tier 2-3: 0.45~0.60
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.CHAIR, 0.45, 0.60));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.COMPUTER, 0.45, 0.60));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.ORANGE_LOG, 0.45, 0.60));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.ORANGE_LEAVES, 0.45, 0.60));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.ORANGE_PLANK, 0.45, 0.60));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.STRIPPED_ORANGE_LOG, 0.45, 0.60));
		// 本模组物品 Tier 2: 0.50~0.70
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.CLOUD_STONE, 0.50, 0.70));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.CLOUD_WATER_BUCKET, 0.50, 0.70));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.CLOUD_WATER_BOTTLE, 0.50, 0.70));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.CLOUD_DEM, 0.50, 0.70));
		// 本模组物品 Tier 3: 0.65~0.85
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.ICON, 0.65, 0.85));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.SADICON, 0.65, 0.85));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.HEARTICON, 0.65, 0.85));
		// 本模组物品 Tier 3: 0.75~1.00
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.STANGE_CLOUD_SPAWN_EGG, 0.75, 1.00));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.ANBULA_SPAWN_EGG, 0.75, 1.00));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.PRISONER_SPAWN_EGG, 0.75, 1.00));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.SVAN_SPAWN_EGG, 0.75, 1.00));
		ITEM_POOL.add(new LootEntry(McanomalyarchivesModItems.QU_SPAWN_EGG, 0.75, 1.00));
	}

	// ========== 生物池 ==========

	private static final List<MobEntry> MOB_POOL = new ArrayList<>();
	static {
		// Tier 0: 诡异度 0.00~0.30 — 友善 + 敌对普通生物 (~2-6格)
		MOB_POOL.add(new MobEntry("minecraft:pig", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:cow", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:sheep", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:chicken", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:rabbit", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:cod", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:salmon", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:zombie", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:skeleton", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:spider", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:creeper", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:drowned", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:witch", 0.00, 0.30));
		MOB_POOL.add(new MobEntry("minecraft:slime", 0.00, 0.30));

		// Tier 1: 诡异度 0.25~0.50 — 下界/末地生物 (~5-9格)
		MOB_POOL.add(new MobEntry("minecraft:enderman", 0.25, 0.50));
		MOB_POOL.add(new MobEntry("minecraft:blaze", 0.25, 0.50));
		MOB_POOL.add(new MobEntry("minecraft:ghast", 0.25, 0.50));
		MOB_POOL.add(new MobEntry("minecraft:magma_cube", 0.25, 0.50));
		MOB_POOL.add(new MobEntry("minecraft:wither_skeleton", 0.25, 0.50));
		MOB_POOL.add(new MobEntry("minecraft:piglin_brute", 0.25, 0.50));

		// Tier 2: 诡异度 0.45~0.75 — Boss/模组生物 (~8-12格)
		MOB_POOL.add(new MobEntry("minecraft:elder_guardian", 0.45, 0.75));
		MOB_POOL.add(new MobEntry("minecraft:ravager", 0.45, 0.75));
		MOB_POOL.add(new MobEntry("minecraft:evoker", 0.45, 0.75));
		MOB_POOL.add(new MobEntry("mcanomalyarchives:anbula", 0.45, 0.75));
		MOB_POOL.add(new MobEntry("mcanomalyarchives:prisoner", 0.45, 0.75));
		MOB_POOL.add(new MobEntry("mcanomalyarchives:svan", 0.45, 0.75));
		MOB_POOL.add(new MobEntry("mcanomalyarchives:qu", 0.45, 0.75));

		// Tier 3: 诡异度 0.70~1.00 — 终极Boss (~11-15格)
		MOB_POOL.add(new MobEntry("minecraft:wither", 0.70, 1.00));
		MOB_POOL.add(new MobEntry("minecraft:warden", 0.70, 1.00));
	}

	// ========== 事件处理 ==========

	@SubscribeEvent
	public static void onItemFished(ItemFishedEvent event) {
		FishingHook hook = event.getHookEntity();
		Player player = hook.getPlayerOwner();
		if (player == null) return;

		// 检查是否使用 StrangeFishingRod
		boolean hasStrangeRod = player.getMainHandItem().getItem() instanceof net.mcreator.mcanomalyarchives.item.StrangeFishingRodItem
				|| player.getOffhandItem().getItem() instanceof net.mcreator.mcanomalyarchives.item.StrangeFishingRodItem;
		if (!hasStrangeRod) return;

		// 首次钓鱼时打印 FishingHook int 字段（用于发现计时字段名）
		if (!fieldsPrinted) {
			fieldsPrinted = true;
			System.out.println("=== FishingHook int fields ===");
			for (Field field : FishingHook.class.getDeclaredFields()) {
				if (field.getType() == int.class) {
					field.setAccessible(true);
					try {
						int value = field.getInt(hook);
						System.out.println("  " + field.getName() + " = " + value);
					} catch (Exception e) {
						System.out.println("  " + field.getName() + " = ERROR: " + e.getMessage());
					}
				}
			}
			System.out.println("=============================");
		}

		// 取消原版战利品
		event.setCanceled(true);

		double distance = hook.distanceTo(player);
		double weirdness = calculateWeirdness(distance);
		Level level = player.level();

		if (!(level instanceof ServerLevel serverLevel)) return;

		// 触发成就：钓鱼？
		if (player instanceof ServerPlayer serverPlayer) {
			triggerAdvancement(serverLevel, serverPlayer, "mcanomalyarchives:shangyu", "shangyu_0");
		}

		// Tier 3 最高档成就
		if (weirdness >= 0.70 && player instanceof ServerPlayer serverPlayer) {
			triggerAdvancement(serverLevel, serverPlayer, "mcanomalyarchives:whatthe", "whatthe_0");
		}

		// Tier 3 事件骰子（诡异度 >= 0.70）
		if (weirdness >= 0.70) {
			int roll = RANDOM.nextInt(100);
			switch (roll) {
				case 0: case 1: case 2: case 3: case 4:
					triggerExplosion(serverLevel, hook);
					break;
				case 5: case 6: case 7:
					triggerBlindDeath(player);
					break;
				case 8:
					if (player instanceof ServerPlayer sp) triggerCrash(sp);
					break;
				case 9: case 10: case 11:
					triggerGravity(player);
					break;
				case 12: case 13: case 14:
					triggerMelt(serverLevel, player);
					break;
				case 15: case 16: case 17:
					triggerVoid(serverLevel, player);
					break;
				default:
					dispatchLoot(serverLevel, player, hook, weirdness);
					break;
			}
		} else {
			dispatchLoot(serverLevel, player, hook, weirdness);
		}

		// 正常消耗耐久
		event.damageRodBy(1);
	}

	private static void dispatchLoot(ServerLevel serverLevel, Player player, FishingHook hook, double weirdness) {
		// 50% 物品 / 50% 生物
		if (RANDOM.nextBoolean()) {
			ItemStack loot = selectItem(weirdness);
			if (loot != null && !loot.isEmpty()) {
				if (!player.getInventory().add(loot)) {
					player.drop(loot, false);
				}
			}
		} else {
			EntityType<?> mobType = selectMob(weirdness);
			if (mobType != null) {
				BlockPos spawnPos = BlockPos.containing(hook.getX(), hook.getY() + 0.5, hook.getZ());
				Entity entity = mobType.spawn(serverLevel, spawnPos, MobSpawnType.MOB_SUMMONED);
				if (entity != null) {
					// Sanity loss for Tier 3 mobs (weirdness >= 0.70)
					if (weirdness >= 0.70 && player instanceof ServerPlayer serverPlayer) {
						SanityEventHandler.onFishingTier3(serverPlayer);
					}
					Vec3 pull = player.position().subtract(entity.position());
					double dist = pull.length();
					if (dist > 0.1) {
						pull = pull.normalize().scale(Math.min(dist * 0.3, 2.5));
						entity.setDeltaMovement(pull.x, pull.y + 0.5, pull.z);
						entity.hurtMarked = true;
					}
				}
			}
		}
	}

	// ========== 诡异事件 ==========

	private static void triggerExplosion(ServerLevel level, FishingHook hook) {
		// 先手动清除半径50球体内所有方块（突破水的保护）
		double x = hook.getX(), y = hook.getY(), z = hook.getZ();
		int radius = 50;
		int r2 = radius * radius;
		BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dy * dy + dz * dz <= r2) {
						mPos.set(x + dx, y + dy, z + dz);
						level.setBlock(mPos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		// 第一波引爆：50强度做伤害+粒子（NONE 不额外破坏方块）
		level.explode(null, x, y, z, 50.0F, false, Level.ExplosionInteraction.NONE);
		// 第二波引爆：75强度 BLOCK 模式二次破坏
		level.explode(null, x, y, z, 75.0F, false, Level.ExplosionInteraction.BLOCK);
	}

	private static void triggerBlindDeath(Player player) {
		player.addEffect(new MobEffectInstance(
				MobEffects.BLINDNESS, 5 * 20, 0));
		player.addEffect(new MobEffectInstance(
				MobEffects.MOVEMENT_SLOWDOWN, 5 * 20, 4));

		net.mcreator.mcanomalyarchives.McanomalyarchivesMod.queueServerWork(5 * 20, () -> {
			DamageSource brainDamage = getBrainDamageSource(player.level());
			forceKillPlayer(player, brainDamage);
		});
	}

	private static void triggerCrash(ServerPlayer player) {
		player.connection.disconnect(
				Component.literal("§c游戏客户端已崩溃\n\n" +
						"§7java.lang.OutOfMemoryError: §fFishingRods\n" +
						"§7    at §fmcanomalyarchives.fishing.events..."));
	}

	private static void triggerGravity(Player player) {
		double x = player.getX();
		double z = player.getZ();
		double y = player.getY() + 100;
		player.teleportTo(x, y, z);
	}

	private static void triggerMelt(ServerLevel level, Player player) {
		BlockPos center = player.blockPosition();
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				if (dx * dx + dz * dz <= 25) {
					BlockPos pos = center.offset(dx, -1, dz);
					if (level.getBlockState(pos).isSolid()) {
						level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	private static void triggerVoid(ServerLevel level, Player player) {
		BlockPos playerPos = player.blockPosition();
		// 从脚下到 -64 格（世界最低）
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = playerPos.getY() - 1; y >= -64; y--) {
					level.setBlock(new BlockPos(playerPos.getX() + dx, y, playerPos.getZ() + dz),
							Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
	}

	// ========== Braindeath 辅助方法 ==========

	private static DamageSource getBrainDamageSource(Level level) {
		ResourceKey<DamageType> brainDamageKey = ResourceKey.create(
				Registries.DAMAGE_TYPE,
				ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "braindamage")
		);
		return level.damageSources().source(brainDamageKey);
	}

	private static void forceKillPlayer(Player player, DamageSource damageSource) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.getAbilities().invulnerable = false;
			serverPlayer.getAbilities().flying = false;
			serverPlayer.onUpdateAbilities();
			serverPlayer.hurt(damageSource, Float.MAX_VALUE);
			if (serverPlayer.getHealth() > 0) {
				serverPlayer.die(damageSource);
			}
		} else {
			player.hurt(damageSource, Float.MAX_VALUE);
			if (player.getHealth() > 0) {
				player.die(damageSource);
			}
		}
	}

	// ========== 数据类 ==========

	private static ItemStack selectItem(double weirdness) {
		List<LootEntry> candidates = new ArrayList<>();
		double totalWeight = 0;
		for (LootEntry entry : ITEM_POOL) {
			if (weirdness >= entry.minWeirdness && weirdness <= entry.maxWeirdness) {
				double weight = 1.0 - Math.abs(weirdness - (entry.minWeirdness + entry.maxWeirdness) / 2.0) * 5;
				weight = Math.max(weight, 0.1);
				candidates.add(entry);
				totalWeight += weight;
			}
		}

		if (candidates.isEmpty()) {
			LootEntry closest = null;
			double closestDiff = Double.MAX_VALUE;
			for (LootEntry entry : ITEM_POOL) {
				double mid = (entry.minWeirdness + entry.maxWeirdness) / 2.0;
				double diff = Math.abs(weirdness - mid);
				if (diff < closestDiff) {
					closestDiff = diff;
					closest = entry;
				}
			}
			if (closest != null) candidates.add(closest);
		}

		if (candidates.isEmpty()) return ItemStack.EMPTY;

		double roll = RANDOM.nextDouble() * totalWeight;
		double cumulative = 0;
		for (LootEntry candidate : candidates) {
			double weight = 1.0 - Math.abs(weirdness - (candidate.minWeirdness + candidate.maxWeirdness) / 2.0) * 5;
			weight = Math.max(weight, 0.1);
			cumulative += weight;
			if (roll <= cumulative) {
				return new ItemStack(candidate.itemSupplier.get());
			}
		}

		return new ItemStack(candidates.get(candidates.size() - 1).itemSupplier.get());
	}

	private static EntityType<?> selectMob(double weirdness) {
		List<MobEntry> candidates = new ArrayList<>();
		for (MobEntry entry : MOB_POOL) {
			if (weirdness >= entry.minWeirdness && weirdness <= entry.maxWeirdness) {
				candidates.add(entry);
			}
		}

		if (candidates.isEmpty()) {
			MobEntry closest = null;
			double closestDiff = Double.MAX_VALUE;
			for (MobEntry entry : MOB_POOL) {
				double mid = (entry.minWeirdness + entry.maxWeirdness) / 2.0;
				double diff = Math.abs(weirdness - mid);
				if (diff < closestDiff) {
					closestDiff = diff;
					closest = entry;
				}
			}
			if (closest != null) candidates.add(closest);
		}

		if (candidates.isEmpty()) return null;

		MobEntry selected = candidates.get(RANDOM.nextInt(candidates.size()));
		return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(selected.entityId));
	}

	// ========== 数据类 ==========

	private static class LootEntry {
		final Supplier<Item> itemSupplier;
		final double minWeirdness;
		final double maxWeirdness;

		LootEntry(Supplier<Item> itemSupplier, double minWeirdness, double maxWeirdness) {
			this.itemSupplier = itemSupplier;
			this.minWeirdness = minWeirdness;
			this.maxWeirdness = maxWeirdness;
		}
	}

	private static class MobEntry {
		final String entityId;
		final double minWeirdness;
		final double maxWeirdness;

		MobEntry(String entityId, double minWeirdness, double maxWeirdness) {
			this.entityId = entityId;
			this.minWeirdness = minWeirdness;
			this.maxWeirdness = maxWeirdness;
		}
	}

	private static void triggerAdvancement(ServerLevel serverLevel, ServerPlayer player, String advancementId, String criteria) {
		AdvancementHolder advancement = serverLevel.getServer().getAdvancements()
				.get(ResourceLocation.parse(advancementId));
		if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
			player.getAdvancements().award(advancement, criteria);
		}
	}

	// @EventBusSubscriber 自动注册，无需手动 register()
}
