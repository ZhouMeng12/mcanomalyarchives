# StrangeFishingRod 全物品钓鱼 + 生物钓取 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 StrangeFishingRod 根据浮钩距离产出不同诡异度的物品/生物，越近咬钩越快，越远咬钩越慢。

**Architecture:** 两个文件改动 — StrangeFishingRodItem 重写 use() 接管咬钩时间；新增 StrangeFishingRodLootHandler 订阅 ItemFishedEvent，取消原版战利品并替换为自定义物品池/生物池。咬钩时间通过反射修改 FishingHook 内部计时字段。

**Tech Stack:** NeoForge 21.8.31, Minecraft 1.21.8, Java 21, MCreator 项目结构

---

## 前置说明：API 发现

ItemFishedEvent（已验证）:
- `getDrops()` → `NonNullList<ItemStack>` — 战利品列表
- `getHookEntity()` → `FishingHook` — 获取浮钩实体
- `damageRodBy(int)` — 设置鱼竿耐久消耗
- 事件在 `NeoForge.EVENT_BUS` 上发布，可取消（取消后不生成战利品）
- **注意：`getDrops()` 返回的是原版 loot table 生成的 list 的副本，修改它不会影响实际掉落。需要通过取消事件 + 手动生成物品/召唤生物来实现自定义掉落。**

FishingHook 关键方法：
- `getPlayerOwner()` → `Player`
- `position()` → `Vec3` — 浮钩位置
- `distanceTo(Entity)` → `double` — 距离

FishingHook 内部计时字段（需反射发现）：
- 原版钓鱼等待时间为 100-600 ticks（5-30秒）
- Lure 附魔每级减少 100 ticks
- 计时字段在一系列 `int` 型私有字段中，实现时通过打印所有 int 字段的值来发现

---

### Task 1: 发现 FishingHook 计时字段名

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/events/StrangeFishingRodLootHandler.java` (临时调试代码)

- [ ] **Step 1: 创建临时调试处理器，打印 FishingHook 所有 int 字段**

```java
package net.mcreator.strangerecord.events;

import net.minecraft.world.entity.projectile.FishingHook;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

import java.lang.reflect.Field;

@EventBusSubscriber(Dist.DEDICATED_SERVER)
public class StrangeFishingRodLootHandler {

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        FishingHook hook = event.getHookEntity();
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

    public static void register() {
        NeoForge.EVENT_BUS.register(StrangeFishingRodLootHandler.class);
    }
}
```

- [ ] **Step 2: 在 StrangerecordMod.java 构造函数中注册事件总线**

打开 `src/main/java/net/mcreator/strangerecord/StrangerecordMod.java`，在构造函数末尾添加：

```java
StrangeFishingRodLootHandler.register();
```

- [ ] **Step 3: 编译运行，投钩钓鱼，查看控制台输出**

```bash
.\gradlew.bat runClient
```

在游戏中用 StrangeFishingRod 钓鱼并收杆，查看控制台中打印的 int 字段名和值。记录在咬钩瞬间（收杆时）数值较大的那个 int 字段名（通常是几百的数值，代表剩余的等待 ticks）。

- [ ] **Step 4: 记录发现的字段名**

找到控制咬钩时间的字段名后（假设为 `biteWaitTime` 或类似名称），记录备用。**继续执行 Task 2**。

---

### Task 2: 实现 StrangeFishingRodItem.use() — 距离→咬钩时间

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/item/StrangeFishingRodItem.java`

- [ ] **Step 1: 重写 use() 方法**

将 Task 1 中发现的字段名填入 `BITE_TIME_FIELD` 常量。如果未发现，使用反射遍历所有 int 字段，找到值在 0-1200 范围内的那个（即咬钩计时字段）。

```java
package net.mcreator.strangerecord.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import java.lang.reflect.Field;
import java.util.List;

public class StrangeFishingRodItem extends FishingRodItem {

    // 用 Task 1 发现的字段名替换此值
    private static final String BITE_TIME_FIELD = "TASK1_DISCOVERED_FIELD_NAME";
    private static Field biteTimeField = null;

    static {
        try {
            biteTimeField = FishingHook.class.getDeclaredField(BITE_TIME_FIELD);
            biteTimeField.setAccessible(true);
        } catch (Exception e) {
            // 降级：遍历查找 int 字段
        }
    }

    public StrangeFishingRodItem(Item.Properties properties) {
        super(properties.durability(51)
            .repairable(TagKey.create(Registries.ITEM,
                ResourceLocation.parse("strangerecord:strange_fishing_rod_repair_items"))));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 检查是否已有浮钩
        FishingHook existingHook = player.fishing;

        if (existingHook == null) {
            // 投钩：正常执行，但延迟一 tick 后再设置咬钩时间
            InteractionResultHolder<ItemStack> result = super.use(level, player, hand);

            // 在新 tick 中查找刚创建的浮钩并设置咬钩时间
            // 浮钩在 super.use() 中同步创建，但距离在飞行后确定
            // 使用 level.scheduleTick() 或直接在下一帧处理
            if (result.getResult().consumesAction() && !level.isClientSide) {
                scheduleBiteTimeAdjustment(level, player);
            }
            return result;
        } else {
            // 收杆：正常执行，战利品由事件处理器接管
            return super.use(level, player, hand);
        }
    }

    private static void scheduleBiteTimeAdjustment(Level level, Player player) {
        // 使用 ServerLevel 的计划任务在浮钩落地后设置咬钩时间
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // 延迟 5 ticks 等待浮钩飞行到目标位置
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(5, () -> {
                FishingHook hook = player.fishing;
                if (hook != null && hook.isAlive()) {
                    double distance = hook.distanceTo(player);
                    int biteTime = calculateBiteTime(distance);
                    setHookBiteTime(hook, biteTime);
                }
            }));
        }
    }

    private static int calculateBiteTime(double distance) {
        // biteTime(ticks) = 40 + (distance / 50) * (200 + random * 400)
        // 近距(8格): 40~100 ticks = 2~5秒
        // 远距(50格): 240~700 ticks = 12~35秒
        double factor = Math.min(distance / 50.0, 1.0);
        int randomExtra = 200 + (int)(Math.random() * 400); // 200~600
        return 40 + (int)(factor * randomExtra);
    }

    private static void setHookBiteTime(FishingHook hook, int biteTime) {
        try {
            if (biteTimeField != null) {
                biteTimeField.setInt(hook, biteTime);
            } else {
                // 反射降级：遍历所有 int 字段，找到值 > 0 且在合理范围内的
                for (Field field : FishingHook.class.getDeclaredFields()) {
                    if (field.getType() == int.class) {
                        field.setAccessible(true);
                        int current = field.getInt(hook);
                        // 咬钩计时字段通常在 50-1200 范围
                        if (current > 50 && current < 1200) {
                            field.setInt(hook, biteTime);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 反射失败则使用默认咬钩时间
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew.bat build
```

预期：无编译错误。如果 `FishingHook` 找不到 `int` 字段的 getter，所有错误被静默处理（降级到默认行为）。

---

### Task 3: 实现 StrangeFishingRodLootHandler — 战利品替换

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/events/StrangeFishingRodLootHandler.java` (替换调试代码)
- Read: `src/main/java/net/mcreator/strangerecord/init/StrangerecordModItems.java` (确认物品注册名)

- [ ] **Step 1: 替换调试代码为完整战利品处理器**

```java
package net.mcreator.strangerecord.events;

import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.strangerecord.init.StrangerecordModItems;

import java.util.*;

@EventBusSubscriber(Dist.DEDICATED_SERVER)
public class StrangeFishingRodLootHandler {

    private static final Random RANDOM = new Random();

    // ========== 诡异度计算 ==========

    private static double calculateWeirdness(double distance) {
        double base = Math.clamp((distance - 8.0) / 42.0, 0.0, 1.0);
        double noise = RANDOM.nextGaussian() * 0.1;
        return Math.clamp(base + noise, 0.0, 1.0);
    }

    // ========== 物品池 ==========

    private static final List<LootEntry> ITEM_POOL = new ArrayList<>();
    static {
        // Tier 0: 诡异度 0.0~0.2
        ITEM_POOL.add(new LootEntry(Items.DIRT, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.STONE, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.COBBLESTONE, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.GRAVEL, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.SAND, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.STICK, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.STRING, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.BONE, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.ROTTEN_FLESH, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.WHEAT_SEEDS, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.OAK_SAPLING, 0.0, 0.2));
        ITEM_POOL.add(new LootEntry(Items.LEATHER, 0.0, 0.2));

        // Tier 1: 诡异度 0.2~0.4
        ITEM_POOL.add(new LootEntry(Items.IRON_INGOT, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.GOLD_INGOT, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.DIAMOND, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.EMERALD, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.COAL, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.REDSTONE, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.LAPIS_LAZULI, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.COPPER_INGOT, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.IRON_SWORD, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.BOW, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.FISHING_ROD, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.SADDLE, 0.2, 0.4));
        ITEM_POOL.add(new LootEntry(Items.NAME_TAG, 0.2, 0.4));

        // Tier 2: 诡异度 0.4~0.6
        ITEM_POOL.add(new LootEntry(Items.ENDER_PEARL, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.BLAZE_ROD, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.GHAST_TEAR, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.NETHER_WART, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.ENCHANTED_BOOK, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.DIAMOND_SWORD, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.TOTEM_OF_UNDYING, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.ELYTRA, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.NETHERITE_SCRAP, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.SPONGE, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.SLIME_BALL, 0.4, 0.6));
        ITEM_POOL.add(new LootEntry(Items.PHANTOM_MEMBRANE, 0.4, 0.6));

        // Tier 3: 诡异度 0.6~0.8
        ITEM_POOL.add(new LootEntry(Items.NETHER_STAR, 0.6, 0.8));
        ITEM_POOL.add(new LootEntry(Items.DRAGON_EGG, 0.6, 0.8));
        ITEM_POOL.add(new LootEntry(Items.DRAGON_HEAD, 0.6, 0.8));
        ITEM_POOL.add(new LootEntry(Items.BEACON, 0.6, 0.8));
        ITEM_POOL.add(new LootEntry(Items.SHULKER_SHELL, 0.6, 0.8));
        ITEM_POOL.add(new LootEntry(Items.HEART_OF_THE_SEA, 0.6, 0.8));
        ITEM_POOL.add(new LootEntry(Items.TRIDENT, 0.6, 0.8));
        ITEM_POOL.add(new LootEntry(Items.NETHERITE_INGOT, 0.6, 0.8));
        ITEM_POOL.add(new LootEntry(Items.ENCHANTED_GOLDEN_APPLE, 0.6, 0.8));

        // Tier 4: 诡异度 0.8~1.0
        ITEM_POOL.add(new LootEntry(Items.BEDROCK, 0.8, 1.0));
        ITEM_POOL.add(new LootEntry(Items.STRUCTURE_BLOCK, 0.8, 1.0));
        ITEM_POOL.add(new LootEntry(Items.COMMAND_BLOCK, 0.8, 1.0));
        ITEM_POOL.add(new LootEntry(Items.BARRIER, 0.8, 1.0));
        ITEM_POOL.add(new LootEntry(Items.LIGHT, 0.8, 1.0));
        ITEM_POOL.add(new LootEntry(Items.JIGSAW, 0.8, 1.0));
        ITEM_POOL.add(new LootEntry(Items.SPAWNER, 0.8, 1.0));

        // 本模组物品 Tier 3: 0.55~0.65
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.CORN_POPPY.get(), 0.55, 0.65));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.SAD_POPPY.get(), 0.55, 0.65));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.DEAD_POPPY.get(), 0.55, 0.65));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.ORANGE.get(), 0.55, 0.65));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.ORANGE_SAPLING.get(), 0.55, 0.65));
        // 本模组物品 Tier 3-4: 0.60~0.70
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.CHAIR.get(), 0.60, 0.70));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.COMPUTER.get(), 0.60, 0.70));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.ORANGE_LOG.get(), 0.60, 0.70));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.ORANGE_LEAVES.get(), 0.60, 0.70));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.ORANGE_PLANK.get(), 0.60, 0.70));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.STRIPPED_ORANGE_LOG.get(), 0.60, 0.70));
        // 本模组物品 Tier 3: 0.65~0.80
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.CLOUD_STONE.get(), 0.65, 0.80));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.CLOUD_WATER_BUCKET.get(), 0.65, 0.80));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.CLOUD_WATER_BOTTLE.get(), 0.65, 0.80));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.CLOUD_DEM.get(), 0.65, 0.80));
        // 本模组物品 Tier 4: 0.75~0.90
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.ICON.get(), 0.75, 0.90));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.SADICON.get(), 0.75, 0.90));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.HEARTICON.get(), 0.75, 0.90));
        // 本模组物品 Tier 4: 0.80~1.00
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.STANGE_CLOUD_SPAWN_EGG.get(), 0.80, 1.00));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.ANBULA_SPAWN_EGG.get(), 0.80, 1.00));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.PRISONER_SPAWN_EGG.get(), 0.80, 1.00));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.SVAN_SPAWN_EGG.get(), 0.80, 1.00));
        ITEM_POOL.add(new LootEntry(StrangerecordModItems.QU_SPAWN_EGG.get(), 0.80, 1.00));
    }

    // ========== 生物池 ==========

    private static final List<MobEntry> MOB_POOL = new ArrayList<>();
    static {
        // Tier 0: 诡异度 0.0~0.2
        MOB_POOL.add(new MobEntry("minecraft:pig", 0.0, 0.2));
        MOB_POOL.add(new MobEntry("minecraft:cow", 0.0, 0.2));
        MOB_POOL.add(new MobEntry("minecraft:sheep", 0.0, 0.2));
        MOB_POOL.add(new MobEntry("minecraft:chicken", 0.0, 0.2));
        MOB_POOL.add(new MobEntry("minecraft:rabbit", 0.0, 0.2));
        MOB_POOL.add(new MobEntry("minecraft:cod", 0.0, 0.2));
        MOB_POOL.add(new MobEntry("minecraft:salmon", 0.0, 0.2));

        // Tier 1: 诡异度 0.2~0.4
        MOB_POOL.add(new MobEntry("minecraft:zombie", 0.2, 0.4));
        MOB_POOL.add(new MobEntry("minecraft:skeleton", 0.2, 0.4));
        MOB_POOL.add(new MobEntry("minecraft:spider", 0.2, 0.4));
        MOB_POOL.add(new MobEntry("minecraft:creeper", 0.2, 0.4));
        MOB_POOL.add(new MobEntry("minecraft:drowned", 0.2, 0.4));
        MOB_POOL.add(new MobEntry("minecraft:witch", 0.2, 0.4));
        MOB_POOL.add(new MobEntry("minecraft:slime", 0.2, 0.4));

        // Tier 2: 诡异度 0.4~0.6
        MOB_POOL.add(new MobEntry("minecraft:enderman", 0.4, 0.6));
        MOB_POOL.add(new MobEntry("minecraft:blaze", 0.4, 0.6));
        MOB_POOL.add(new MobEntry("minecraft:ghast", 0.4, 0.6));
        MOB_POOL.add(new MobEntry("minecraft:magma_cube", 0.4, 0.6));
        MOB_POOL.add(new MobEntry("minecraft:wither_skeleton", 0.4, 0.6));
        MOB_POOL.add(new MobEntry("minecraft:piglin_brute", 0.4, 0.6));

        // Tier 3: 诡异度 0.6~0.8
        MOB_POOL.add(new MobEntry("minecraft:elder_guardian", 0.6, 0.8));
        MOB_POOL.add(new MobEntry("minecraft:ravager", 0.6, 0.8));
        MOB_POOL.add(new MobEntry("minecraft:evoker", 0.6, 0.8));
        MOB_POOL.add(new MobEntry("strangerecord:anbula", 0.6, 0.8));
        MOB_POOL.add(new MobEntry("strangerecord:prisoner", 0.6, 0.8));
        MOB_POOL.add(new MobEntry("strangerecord:svan", 0.6, 0.8));
        MOB_POOL.add(new MobEntry("strangerecord:qu", 0.6, 0.8));

        // Tier 4: 诡异度 0.8~1.0
        MOB_POOL.add(new MobEntry("minecraft:wither", 0.8, 1.0));
        MOB_POOL.add(new MobEntry("minecraft:warden", 0.8, 1.0));
    }

    // ========== 事件处理 ==========

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        FishingHook hook = event.getHookEntity();
        Player player = hook.getPlayerOwner();
        if (player == null) return;

        // 检查是否使用 StrangeFishingRod
        boolean hasStrangeRod = player.getMainHandItem().getItem() instanceof net.mcreator.strangerecord.item.StrangeFishingRodItem
                || player.getOffhandItem().getItem() instanceof net.mcreator.strangerecord.item.StrangeFishingRodItem;
        if (!hasStrangeRod) return;

        // 取消原版战利品
        event.setCanceled(true);

        double distance = hook.distanceTo(player);
        double weirdness = calculateWeirdness(distance);
        Level level = player.level();

        if (!(level instanceof ServerLevel serverLevel)) return;

        // 50% 物品 / 50% 生物
        if (RANDOM.nextBoolean()) {
            // 物品路径
            ItemStack loot = selectItem(weirdness);
            if (loot != null && !loot.isEmpty()) {
                // 直接给玩家，如果背包满则掉落在地
                if (!player.getInventory().add(loot)) {
                    player.drop(loot, false);
                }
            }
        } else {
            // 生物路径
            EntityType<?> mobType = selectMob(weirdness);
            if (mobType != null) {
                Entity entity = mobType.create(serverLevel);
                if (entity != null) {
                    entity.moveTo(hook.getX(), hook.getY() + 0.5, hook.getZ(),
                            RANDOM.nextFloat() * 360.0F, 0.0F);
                    serverLevel.addFreshEntity(entity);
                }
            }
        }

        // 正常消耗耐久
        event.damageRodBy(1);
    }

    private static ItemStack selectItem(double weirdness) {
        // 筛选诡异度范围内的候选
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
            // 降级：选最近的层
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

        // 加权随机
        double roll = RANDOM.nextDouble() * totalWeight;
        double cumulative = 0;
        for (LootEntry candidate : candidates) {
            double weight = 1.0 - Math.abs(weirdness - (candidate.minWeirdness + candidate.maxWeirdness) / 2.0) * 5;
            weight = Math.max(weight, 0.1);
            cumulative += weight;
            if (roll <= cumulative) {
                return new ItemStack(candidate.item);
            }
        }

        return new ItemStack(candidates.get(candidates.size() - 1).item);
    }

    private static EntityType<?> selectMob(double weirdness) {
        // 筛选诡异度范围内的候选
        List<MobEntry> candidates = new ArrayList<>();
        for (MobEntry entry : MOB_POOL) {
            if (weirdness >= entry.minWeirdness && weirdness <= entry.maxWeirdness) {
                candidates.add(entry);
            }
        }

        if (candidates.isEmpty()) {
            // 降级：选最近的层
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
        final net.minecraft.world.item.Item item;
        final double minWeirdness;
        final double maxWeirdness;

        LootEntry(net.minecraft.world.item.Item item, double minWeirdness, double maxWeirdness) {
            this.item = item;
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

    public static void register() {
        NeoForge.EVENT_BUS.register(StrangeFishingRodLootHandler.class);
    }
}
```

- [ ] **Step 2: 确认 StrangerecordMod.java 中已注册事件总线**

打开 `src/main/java/net/mcreator/strangerecord/StrangerecordMod.java`，确认构造函数末尾有：

```java
StrangeFishingRodLootHandler.register();
```

如果 Task 1 中已添加则无需重复。

- [ ] **Step 3: 编译验证**

```bash
.\gradlew.bat build
```

预期：无编译错误。

---

### Task 4: 修复 BITETIMEFIELD 常量名并最终验证

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/item/StrangeFishingRodItem.java`

- [ ] **Step 1: 用 Task 1 发现的字段名替换占位符**

将 `BITE_TIME_FIELD` 的值从 `"TASK1_DISCOVERED_FIELD_NAME"` 改为实际发现的字段名。

- [ ] **Step 2: 最终编译**

```bash
.\gradlew.bat build
```

预期：BUILD SUCCESSFUL。

- [ ] **Step 3: 游戏内测试**

```bash
.\gradlew.bat runClient
```

测试场景：
1. 近处投钩（≤8格）→ 咬钩快（2-5秒）→ 收杆获得基础物品或友善生物
2. 中距离投钩（20格）→ 咬钩中等 → 收杆获得矿物/工具或普通怪物
3. 远处投钩（40格）→ 咬钩慢 → 收杆获得稀有物品或 BOSS 生物
4. 极远处投钩（50+格）→ 咬钩最慢 → 收杆获得基岩/结构方块等或凋零/监守者
5. 验证本模组物品（如 cloud_stone, icon 等）在 35+ 格后可钓到
6. 验证耐久正常消耗

- [ ] **Step 4: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/item/StrangeFishingRodItem.java
git add src/main/java/net/mcreator/strangerecord/events/StrangeFishingRodLootHandler.java
git add src/main/java/net/mcreator/strangerecord/StrangerecordMod.java
git commit -m "feat: StrangeFishingRod 全物品钓鱼 + 生物钓取系统"
```

---

### Task 5（可选）: 添加音效和粒子反馈

如果咬钩时间修改生效但希望增强体验：

- [ ] **Step 1: 在收杆时播放罕见物品的特殊音效**

在 `onItemFished` 中，当 `weirdness > 0.7` 时播放特殊音效（如 `minecraft:entity.player.levelup`）。

---

## 自审清单

1. **Spec 覆盖**: Task 1-3 覆盖咬钩时间修改和战利品替换；Task 4 覆盖最终验证
2. **占位符检查**: Task 1 的字段名是运行时发现的值，已标注为 `TASK1_DISCOVERED_FIELD_NAME` — 这是计划内的运行时发现，不是漏填
3. **类型一致性**: `LootEntry` 和 `MobEntry` 在两个方法中一致使用

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-07-09-strange-fishing-rod-loot.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
