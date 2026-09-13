# UO-008 百变命名牌 · 「替换」的技术实现

> 配套文档：`docs/design/uo-008-name-tag-plan.md`（玩法与系统方案）。
> 本文所有 API 结论均已对着 **NeoForge 21.1.190 + Minecraft 1.21.1 反编译源码**核过（`build/moddev/artifacts/neoforge-21.1.190-sources.jar`），核对方式见文末。

## 0. 总原则

三类目标的"内核"住在三个不同的地方，所以**做法不可能统一**：

| 目标 | 内核住在哪 | 外部能不能改写 | 我们的做法 |
|---|---|---|---|
| **物品** | **数据组件**（DataComponents） | ✅ 能，直接复制 | **组件转让**——1:1 还原，最干净 |
| **实体** | 类里硬编码的方法（`Chicken.aiStep` 的产蛋计时、`Cow.mobInteract` 的挤奶） | ❌ 不能 | **可观测行为覆盖层**——输出/交互/音效替换 |
| **方块** | 方块类的方法 + `FlowingFluid` 引擎 | ❌ 不能 | **自建 mimic 方块**——外观引用原版模型，行为自写 |

**先明确两条做不到、不要承诺的东西：**
1. **"把牛真的变成鸡"在技术上不存在。** 一个 `Cow` 实例不可能获得 `Chicken` 类的行为。正片里那种效果，在 MC 里只能覆盖"**玩家能观测到的部分**"。
2. **"让原石真的流动"做不到。** 流体是 `FlowingFluid` 引擎的活，外挂不到别的方块上。

---

## 1. 物品：组件转让（1:1 还原）

### 1.1 为什么这条路最干净

1.21 把物品行为全部搬进了数据组件。已核源码，**三处关键逻辑全部只读 `DataComponents.TOOL`**：

```java
// Item.getDestroySpeed —— 挖掘速度
Tool tool = stack.get(DataComponents.TOOL);
return tool != null ? tool.getMiningSpeed(state) : 1.0F;

// Item.isCorrectToolForDrops —— 能不能挖出掉落物（即"挖掘等级"）
Tool tool = stack.get(DataComponents.TOOL);
return tool != null && tool.isCorrectForDrops(state);

// Item.mineBlock —— 挖一格扣多少耐久
if (!level.isClientSide && player.getDestroySpeed(...) != 0.0F && tool.damagePerBlock() > 0)
    stack.hurtAndBreak(tool.damagePerBlock(), player, EquipmentSlot.MAINHAND);
```

而 `Tool` 是一个纯 record：

```java
public record Tool(List<Tool.Rule> rules, float defaultMiningSpeed, int damagePerBlock)
```

**所以"木铲获得下界合金镐的采集能力"就是一行**：

```java
target.set(DataComponents.TOOL, sourceStack.get(DataComponents.TOOL));
```

模型、贴图、名字、物品类型一个字都不用改 —— 这正好精确复刻正片的"外观不变、只换内核"。

### 1.2 耐久 = 名字字符数（正片"5 次后损毁"）

```java
target.set(DataComponents.MAX_DAMAGE, Math.clamp(name.length(), 1, 64));
target.set(DataComponents.DAMAGE, 0);      // 全新
// damagePerBlock 沿用源工具的（通常是 1）→ 挖满 name.length() 格后碎
```

"下界合金镐"5 个字 → 耐久上限 5 → 挖 5 格碎。参数可配。

### 1.3 组件白名单 / 黑名单

| 转让（白名单） | 效果 |
|---|---|
| `TOOL` | 挖掘速度、挖掘等级、每格掉耐久 |
| `ATTRIBUTE_MODIFIERS` | 攻击力、护甲值、击退抗性 |
| `ENCHANTABLE` | 可附魔等级 |
| `FOOD` | 食物属性（把木棍命名成"熟牛排"就能吃） |
| `EQUIPPABLE`（阶段 2） | 把木棍命名成"钻石胸甲"→ 真的能穿，且带护甲值 |

> 跨类（物品做目标、名字是实体/方块）不走组件转让 —— 走**质料守恒结算**（玩法方案 §4.1）：物品是质料预算最小的载体，跨类几乎必然"质量不足"→ 爆炸 + 极小残渣，正是正片木棍→钻石块。

### 1.6 但物品不止"组件转让"这一支（作者 2026-09-13 追加的要求）

作者要求：**被命名的东西必须与名称有同样的性质**——给石头命名"钻石"，这块石头就要能合成钻石装备、
而且不能再当方块放下去。这**做不到**用组件解决：Minecraft 的配方按 **Item** 匹配（组件救不了），
"能不能放置"取决于它是不是 `BlockItem`。所以物品路径必须拆成两支：

| 目标 | 做法 | 落点 |
|---|---|---|
| 工具 / 护甲（`DataComponents.TOOL` 或 `ArmorItem`） | **组件转让**：外观不变，能力全换，耐久 = 名字字数 | `ItemNaming.transfer` |
| 其它物品（材料、方块物品…） | **完全转换**：`ItemStack` 真的换成目标物品 | `ItemNaming.convert` |

换掉之后，"有名字所指事物的全部性质"自动成立——耐久、能否放置、能否合成、能否当燃料、能不能吃
一律随目标物品走，一条都不用枚举。代价随之从"耐久 = 名字字数"变成**质料守恒**：
`MaterialUnits.canHold` 为假就是爆炸 + 极小残渣。

对应的实体侧规则也理顺了：名字指向的事物**有行为**（工具）→ 行为移植（正片猪→钻石镐挖到死）；
**没有行为**（钻石、金锭、牛排）→ `EntityNaming.convertToMaterial` 直接析出那个东西，
数量按质料守恒折算（牛 110 ÷ 钻石 30 = 3 颗）。

| **绝不复制**（黑名单） | 原因 |
|---|---|
| `MAX_STACK_SIZE` | 会把 64 个木棍变成 64 个"钻石"——**复制物品** |
| `CONTAINER` / `BUNDLE_CONTENTS` | 潜影盒内容物被复制 |
| `BLOCK_ENTITY_DATA` / `BLOCK_STATE` | 方块物品带 NBT，放置后可能崩 |
| `CHARGED_PROJECTILES`、`RECIPES`（知识之书）、`WRITABLE_BOOK_CONTENT` | 直接给玩家白送内容 |

### 1.4 落盘与不可逆

自定义组件 `mcanomalyarchives:nametag_origin`（`{origin_name, target_id, remaining, irreversible}`）挂在物品上 —— 物品进箱子、掉地上、跨维度、进存档都跟着走，**天然持久化，不需要额外表**。

`AnvilUpdateEvent` 里若左槽物品已带该组件 → 直接不产出（对应正片"普通命名牌改回去失败"）。

### 1.5 做不到的

**模组自定义物品**如果是靠重写 `Item` 方法 / MCreator 过程实现的硬编码行为，复制组件无效（组件只管原版那套逻辑）。降级处理：识别不出来就**无效**（不消耗命名牌），不做"猜"。

---

## 2. 实体：可观测行为覆盖层

### 2.1 为什么不能换实体

- 换成一个真的 `Chicken` 实例 → 外观会变成鸡，违反"外观不变"。
- 让 `Cow` 实例跑 `Chicken` 的 AI 树 → `registerGoals()` 是 `protected`，产蛋计时器写在 `Chicken.aiStep()` 里，外部无法构造。

**所以：不换实体，只覆盖"玩家能观测到的部分"。** 三件事：

### 2.2 输出替换（我们自己的 tick 层）

挂 `EntityTickEvent.Post`（已核存在，服务端也触发），按身份驱动产出与计时：

| 身份 | 输出 |
|---|---|
| `chicken` | 播鸡的音效、按计时产出**"牛蛋"**（自定义物品，**能孵出正常的牛** —— 还原正片彩蛋）、不产奶 |
| `potato` | 立刻失去活性 → 掉落发芽马铃薯 → 原地长出嫩芽 |
| `diamond_pickaxe` | 用 `navigation.moveTo()` 走到最近的石头/矿石并挖掉，挖够次数后死亡（正片里那头猪就是这么干的） |
| `gold_block` 等材料 | 延迟转化 + **从周围环境掠夺对应元素**（抽走附近的金块/金装备） |

`navigation` 在服务端是为每个 mob 常驻 tick 的，所以 `moveTo` 直接可用。

### 2.3 交互拦截

`PlayerInteractEvent.EntityInteract`（已核：**可取消事件**）→ 拦掉原版挤奶（桶）、剪毛、上鞍、繁殖，换成身份对应的交互。对应正片"该牛**无法再挤出牛奶**"。

### 2.4 音效重映射（不只是取消，可以**替换**）

```java
// PlayLevelSoundEvent.AtEntity  —— 已核：有 getEntity() 和 setSound()
if (identityOf(event.getEntity()) == CHICKEN) event.setSound(SoundEvents.CHICKEN_AMBIENT);
```

牛叫变鸡叫，一行搞定，纯客户端效果。

### 2.5 为什么不接管移动（刻意的取舍）

`Mob.serverAiStep()` 里这几行是**连着跑**的（已核源码）：

```java
this.sensing.tick();
this.targetSelector.tick();  / this.goalSelector.tick();
this.navigation.tick();      // ← 导航
this.customServerAiStep();
this.moveControl.tick();     // ← 转向
this.lookControl.tick();
```

而 `Mob.isEffectiveAi()` = `!level.isClientSide && !isNoAi()` —— 一旦 `setNoAi(true)`，**整个 `serverAiStep()` 都不再被调用**，`navigation` / `moveControl` 一起停，我们就得自己写转向、寻路、跳跃。

**所以：保留原版游荡 AI。** 正片里那头牛本来也只是撞墙、悲鸣、下蛋，不换 AI 完全够看，成本却差一个数量级。这是明确写进方案的取舍，不是遗漏。

### 2.6 客户端怎么知道身份

实体身份存 NeoForge `AttachmentType`（可序列化、可 `copyOnDeath`）。**注意：NeoForge 的实体附件不会自动同步到客户端**（只有物品堆附件会 —— 已核 `AttachmentType` 源码）。而音效重映射、被命名目标的 HUD 提示都在客户端。

→ 用 `PlayerEvent.StartTracking`（已核存在）在玩家**开始追踪该实体时补发一个同步包**，复用我们已经有的网络通道（`McanomalyarchivesMod.addNetworkMessage` + `PacketDistributor.sendToPlayer`）。

### 2.7 一个拦不住的特例

鸡的产蛋写在 `Chicken.aiStep()` 里，外部拦不住。若一只**鸡**被命名成别的身份，它照样下蛋。
→ 用 `EntityJoinLevelEvent` 把命名鸡附近新生成的蛋物品**丢弃**掉。（小 hack，但干净且有界）

---

## 3. 方块：自建 mimic 方块

### 3.1 为什么必须自建

方块的"内核"是方块类的方法 + 流体引擎：
- `entityInside`（水/网的减速、仙人掌伤害、蛛网黏附）
- `onPlace` / `neighborChanged` / `tick`（TNT、沙子重力、流体扩散）
- `FlowingFluid`（流动、可被桶装、与水/岩浆的交互）

这些**不能外挂到原石上**。但反过来，一旦方块是**我们自己的类**，行为就随我们写。

### 3.2 外观：不需要 BER，改 JSON 就够

`named_block` 带一个字符串属性 `appearance` + BlockEntity，blockstate JSON 里**直接引用原版模型**（模型是命名空间资源，可以跨命名空间引用）：

```json
{
  "variants": {
    "appearance=stone":       { "model": "minecraft:block/stone" },
    "appearance=gold_block":  { "model": "minecraft:block/gold_block" },
    "appearance=diamond_block": { "model": "minecraft:block/diamond_block" }
  }
}
```

→ **不需要 BlockEntityRenderer、不需要任何渲染代码**，加外观就是加一行 JSON。这是整套方案里最省事的一环。
（约束：只支持**完整立方体模型**，所以水/岩浆这类流体外观不会真的动；用我们的 tick 做"慢速黏稠扩散"来近似。）

### 3.3 行为：手写行为表

写在**我们自己的方块类**里，按身份查表：

| 身份 | 行为 |
|---|---|
| 水 | `entityInside` 强黏附（无法自主挣脱）、`tick` 挥发计时（11 分钟后消散）、慢速扩散 |
| 岩浆 | `stepOn` 灼烧 + 发光 |
| 沙子/沙砾 | 重力（`FallingBlock`） |
| 仙人掌 | `entityInside` 接触伤害 |
| TNT | 被点燃后爆炸 |
| 金/钻石/铁块 | 被"材料掠夺"抽走（作为转化的原料来源） |

### 3.4 落盘：**改用 BlockEntity NBT，不用 SavedData**

BlockEntity 自带 NBT 序列化，省掉我在玩法方案 §6 里写的世界级 `SavedData`。
→ **这条要修订玩法方案**：`NamedBlockData`（SavedData）删除，改为 `NamedBlockEntity`。
（方案 §6 已同步修订。）

### 3.5 做不到的 / 降级档

方块的"内核"只能来自上面这张手写行为表（约 10~15 条），**做不到"任意方块名都行为保真"**。想要任意方块名 100% 行为保真，只有一条路：

| 档 | 做法 | 行为 | 外观 |
|---|---|---|---|
| **A（推荐）** | `named_block` mimic + 行为表 | 表内 10~15 条身份保真 | ✅ 保留原方块外观 |
| **B（省事）** | 直接 `level.setBlock(真方块)` | ✅ 任意方块 100% 真 | ❌ 外观变成那个方块 |

**建议 A/B 混用**：流体与特殊行为类身份走 A（还原正片"原石→水"的名场面）；普通方块类身份走 B（行为绝对保真，"完全转换物质成分"也正好对应正片木棍→钻石块那一节的定性）。

---

## 4. 对玩法方案的修订

1. **§6 数据持久化**：方块从 `SavedData`（`NamedBlockData`）改为 **`NamedBlockEntity` 的 NBT**。
2. **§6 数据持久化**：实体从 `persistentData` 改为 **`AttachmentType`**（可 `copyOnDeath`），并新增 **`StartTracking` 同步包**。
3. **§7 文件清单**：新增 `block/NamedBlock.java`（生成区薄壳）、`anomaly/nametag/BlockBehaviorTable.java`、`NamedBlockEntity`、`assets/.../blockstates/named_block.json`、`NamedEntitySyncPacket`。
4. **§12 风险表**：新增"实体 AI 不接管"这一条明确的取舍说明。

---

## 5. 本次核对清单（怎么验的）

| 结论 | 核对来源 |
|---|---|
| 物品挖掘行为全部组件驱动 | `Item.getDestroySpeed` / `isCorrectToolForDrops` / `mineBlock` 源码 |
| `Tool` 是纯 record，可直接复制 | `net/minecraft/world/item/component/Tool.java` |
| `AnvilUpdateEvent` 能改输出/消耗/花费 | `AnvilUpdateEvent` 源码（`setOutput`/`setMaterialCost`/`setCost`） |
| `EntityInteract` 可取消 | `PlayerInteractEvent` 源码 + javadoc |
| `PlayLevelSoundEvent.AtEntity` 能**替换**音效 | 该事件源码（`getEntity()` / `setSound()`） |
| `EntityTickEvent.Post` 服务端也触发 | `EntityTickEvent` 源码 |
| `Mob.serverAiStep` 中 navigation/moveControl 连跑；`isEffectiveAi` 受 `isNoAi` 影响 | `Mob.java` 源码 |
| 实体附件**不自动同步**客户端 | `AttachmentType` 源码 javadoc（仅物品堆附件会同步） |
| `PlayerEvent.StartTracking` 存在 | `PlayerEvent.java` |
| GLM 用 `neoforge:add_table` + `neoforge:loot_table_id` | `AddTableLootModifier` / `LootTableIdCondition` 源码 |

核对工具：`tools/video-research/probe_neoforge_src.py`（配 `PROBE_JAR` 指向 `build/moddev/artifacts/neoforge-21.1.190-sources.jar`，该 jar 含合并后的 Minecraft 源码）。
