# StrangeFishingRod 全物品钓鱼 + 生物钓取 设计文档

**日期:** 2026-07-09
**模组:** 诡异见闻录 (strangerecord)
**Minecraft:** 1.21.8 / NeoForge 21.8.31

---

## 1. 概述

StrangeFishingRod 不再使用原版钓鱼战利品表，改为自定义机制：
- 投钩后根据浮钩距离决定咬钩等待时间（越近越快，越远越慢）
- 收杆时根据距离-诡异度曲线随机产出物品或生物
- 物品池精选有代表性的原版物品 + 全部本模组物品
- 生物直接生成在浮钩位置

---

## 2. 当前状态分析

- **StrangeFishingRodItem.java** (13行): 仅设置耐久度 51 和修复标签，无任何重写方法
- **FishingRodItem.use()**: 原版逻辑 — 无浮钩时投钩，有浮钩时收杆。收杆触发 `ItemFishedEvent`
- **模组物品**: 24 个物品, 14 个方块, 6 个自定义实体
- **无现有钓鱼逻辑**, 无事件监听, 无自定义 FishingHook

---

## 3. 架构设计

```
玩家右键 StrangeFishingRod
  │
  ├─ 无浮钩 → 投钩
  │    └─ StrangeFishingRodItem.use()
  │         计算浮钩与玩家距离 → 调整咬钩时间
  │         近(≤8格): 2~5秒   远(≥50格): 15~30秒
  │
  └─ 有浮钩 → 收杆
       └─ FishingHook.retrieve() → ItemFishedEvent
            └─ StrangeFishingRodLootHandler.onItemFished()
                 ├─ 计算诡异度 = f(距离)
                 ├─ 50%物品 / 50%生物 随机
                 ├─ 物品路径: 按诡异度加权选择 → 替换战利品
                 └─ 生物路径: 按诡异度选生物 → 在浮钩位置召唤
```

### 3.1 诡异度计算

连续概率公式（加随机抖动）：

```
baseWeirdness = clamp((distance - 8) / 42, 0.0, 1.0)
weirdness = baseWeirdness + randomGaussian(-0.1, 0.1)  // 加噪声后 clamp 到 [0,1]
```

| 距离范围 | 诡异度 | 物品类别 | 生物类别 |
|---------|--------|---------|---------|
| ≤8格    | 0.00~0.20 | 基础建材、杂物 | 友善生物 |
| 8-20格  | 0.15~0.40 | 矿物、工具 | 敌对普通怪物 |
| 20-35格 | 0.35~0.60 | 末地/下界物品、附魔书 | 下界/末地生物 |
| 35-50格 | 0.55~0.80 | 稀有物品、本模组物品 | Boss级生物、模组生物 |
| 50+格   | 0.75~1.00 | 极限物品、全部本模组物品 | 终极Boss、守护者 |

### 3.2 咬钩时间

```
biteTime(ticks) = 40~100 + (distance / 50) * (200~600)
                 = 近距: 40~100 ticks (2~5秒)
                   远距: 240~700 ticks (12~35秒)
```

使用 `FishingHook` 的 `biteWaitTime` 字段调整（通过反射或 accessor）。

---

## 4. 物品池设计

### 4.1 原版物品精选（约 50 个）

按诡异度分层，每层选有"独特性"的代表：

| 层 | 诡异度 | 物品 |
|----|--------|------|
| 0 | 0.0~0.2 | dirt, stone, cobblestone, gravel, sand, stick, string, bone, rotten_flesh, wheat_seeds, oak_sapling, leather |
| 1 | 0.2~0.4 | iron_ingot, gold_ingot, diamond, emerald, coal, redstone, lapis_lazuli, copper_ingot, iron_sword, bow, fishing_rod, saddle, name_tag |
| 2 | 0.4~0.6 | ender_pearl, blaze_rod, ghast_tear, nether_wart, enchanted_book, diamond_sword, totem_of_undying, elytra, netherite_scrap, sponge, slime_ball, phantom_membrane |
| 3 | 0.6~0.8 | nether_star, dragon_egg, dragon_head, beacon, shulker_shell, heart_of_the_sea, trident, netherite_ingot, enchanted_golden_apple, music_disc_* (随机), **本模组物品开始出现** |
| 4 | 0.8~1.0 | bedrock, structure_block, command_block, barrier, light, jigsaw, spawner, **本模组全部物品可掉落** |

### 4.2 本模组物品分配

全部 24 个物品按原有定位分配诡异度：

| 物品 | 诡异度 |
|------|--------|
| corn_poppy, sad_poppy, dead_poppy, orange, orange_sapling | 0.55~0.65 |
| chair, computer, orange_log, orange_leaves, orange_plank, stripped_orange_log | 0.60~0.70 |
| cloud_stone, cloud_water_bucket, cloud_water_bottle, cloud_dem | 0.65~0.80 |
| icon, sadicon, hearticon | 0.75~0.90 |
| stange_cloud_spawn_egg, anbula_spawn_egg, prisoner_spawn_egg, svan_spawn_egg, qu_spawn_egg | 0.80~1.00 |

## 5. 生物池设计

| 层 | 诡异度 | 生物 |
|----|--------|------|
| 0 | 0.0~0.2 | pig, cow, sheep, chicken, rabbit, cod, salmon |
| 1 | 0.2~0.4 | zombie, skeleton, spider, creeper, drowned, witch, slime |
| 2 | 0.4~0.6 | enderman, blaze, ghast, magma_cube, wither_skeleton, piglin_brute |
| 3 | 0.6~0.8 | elder_guardian, ravager, evoker, **anbula, prisoner, svan, qu** (模组生物) |
| 4 | 0.8~1.0 | wither, warden, ender_dragon(罕见) |

---

## 6. 文件改动

### 6.1 修改: `StrangeFishingRodItem.java`
**路径:** `src/main/java/net/mcreator/strangerecord/item/StrangeFishingRodItem.java`

重写 `use()` 方法：
- 投钩时计算浮钩落地位置与玩家的距离
- 通过反射或 accessor 设置 `FishingHook.biteWaitTime` 为距离映射值
- 其余委托给 `super.use()`

预计改动: +40 行

### 6.2 新增: `StrangeFishingRodLootHandler.java`
**路径:** `src/main/java/net/mcreator/strangerecord/events/StrangeFishingRodLootHandler.java`

- 订阅 `ItemFishedEvent`
- 判断是否为 StrangeFishingRod
- 计算诡异度
- 50%/50% 随机物品或生物
- 物品: 从对应池子加权随机，替换 event 的 loot
- 生物: 在浮钩位置 summon 对应实体，清空战利品

预计改动: ~200 行

### 6.3 无需改动的文件
- 注册类、资源文件、模型文件均不变

---

## 7. 假设与决策

1. **FishingHook 咬钩时间修改**: 假设可通过反射访问 `FishingHook.biteWaitTime`/`nibbleWaitTime` 字段。如反射失败，备选方案为自定义 FishingHook 子类。
2. **ItemFishedEvent 可取消原版战利品**: 确认事件有 `getDrops()` 方法可清空并替换。
3. **生物生成**: 使用 `level.addFreshEntity()` 在服务端生成，敌对生物默认不 despawn。
4. **距离计算**: 浮钩收杆时 `FishingHook.distanceTo(player)` 计算，此时浮钩实体仍存在。
5. **物品池不会包含空气/技术性物品**: 通过手动精选列表排除 `minecraft:air`, `minecraft:barrier` 等 GUI 物品（barrier 作为"诡异"物品保留在第4层）。

---

## 8. 验证步骤

1. 编译通过: `gradlew build`
2. 游戏中装备 StrangeFishingRod
3. 分别投钩到不同距离水面，验证：
   - 近处咬钩快 (2-5秒)，远处咬钩慢 (12-35秒)
   - 收杆后根据距离获得对应层级物品
   - 约一半次数获得生物，生物出现在浮钩位置
   - 本模组物品在中远距离出现
   - 极远处可钓到基岩、结构方块等
4. 耐久度正常消耗
