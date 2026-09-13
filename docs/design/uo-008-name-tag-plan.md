# UO-008 百变命名牌 · 实现方案（v1）

> 依据：正片《这张"命名牌", 能篡改一切... |【百变命名牌】[MC诡异见闻录]》BV1YiGt6vEbK，11:06。
> 证据链已存档：旁白转写 `docs/video-research/raw/uo008_transcript.txt`（250 段带时间戳）、弹幕 `uo008_danmaku.tsv`（1800 条带秒）、设定卡 `docs/video-research/uo-008-name-tag.md`。
> 本方案只描述设计，不含实现。

---

## 0. 一句话设计

**玩家自己在铁砧上输入的名字，就是这条异常的"代码"。**名字决定属性，代价由名字支付，且**不可撤销**。

正片原文（【旁白 2:00-2:06】）："该命名牌并不会完全转变物体的形态，但却能**篡改物体的内核属性**。"
正片原文（【旁白 3:07-3:11】）："该命名牌可以彻底改写物体的物理属性，且**所有属性转化均存在明显的代价与损耗**。"
观众把正片的逻辑总结得最准（【弹幕 614s】）："**百变命名牌倒置了命名的因果：正常是根据事物的特性赋予名字，它是根据名字赋予特性。**"

---

## 1. 关键：正片的三条使用路径（已由旁白确认，直接决定实现方式）

| 目标 | 正片流程 | 对应实现 |
|---|---|---|
| **实体** | 拿一张命名牌，直接对生物使用（【旁白 1:26-1:29】牛→鸡） | `PlayerInteractEvent.EntityInteract` 右键生效 |
| **物品 / 方块** | **直接命名全部失败**，直到"通过**铁砧**结合命名牌"才成功（【旁白 2:11-2:24】） | `AnvilUpdateEvent`：左槽目标 + 右槽命名牌 + 输入框名字 → 输出被命名的物品 |
| **名字的来源** | 玩家在铁砧输入框里打出目标名字 | 直接读 `AnvilUpdateEvent#getName()`，**不需要做本地化解析** |

> 这条对应关系很重要：**"铁砧改名"不是我们挑的偷懒方案，而是正片行为本身。**并且它顺带解决了"服务端不知道中文名"的技术难题——名字是玩家输入的字符串，不依赖 `Language`。

---

## 2. 玩家体验（三条路径）

1. **给宠物改名**：把百变命名牌放到铁砧输入框改名成"鸡" → 右键自己的牛 → 牛不能挤奶了、发出鸡叫、开始下深褐色的蛋，外貌仍是牛。【旁白 1:26-2:00】
2. **升级装备**：铁砧左槽放木铲、右槽放（改名为"下界合金镐"的）命名牌 → 输出的木铲获得下界合金采集能力，**用 5 次就碎**。【旁白 2:19-2:37】
3. **改写世界**：铁砧把命名牌命名为"水"再与"原石"结合 → 放置后原石变成**极黏的液体向四周蔓延**、被粘住**无法自主挣脱**、**11 分钟后彻底挥发消散**。【旁白 2:44-3:03】

---

## 3. 名字解析

### 3.1 主解析层：内置名字索引（生成脚本产出）

- 脚本 `tools/nametag/gen_name_index.py` 从 Minecraft 客户端 jar 的 `assets/minecraft/lang/zh_cn.json` / `en_us.json` 反查注册表，生成：
  - `data/mcanomalyarchives/name_index/zh_cn.json`
  - `data/mcanomalyarchives/name_index/en_us.json`
- 结构：`{"鸡": "entity:minecraft:chicken", "下界合金镐": "item:minecraft:netherite_pickaxe", "原石": "block:minecraft:stone", ...}`
- 脚本同时把**本模组实体/物品**（幸运粉羊、伪云、见闻录、百变命名牌自己…）补进去，因为生成时 jar 还没构建。
- 服务端在 `AddReloadListenerEvent`（或首次使用时懒加载）读入索引；**纯服务端、零网络代码**，单人/多人一致。
- 同名冲突规则：目标同类型的优先，其次 `item` > `block` > `entity`。
- 未命中 → **无效**：不消耗命名牌，只提示一句"只认主流叫法"。（对应【弹幕 240.6s】观众实测"地蛋"这种别称不生效）

### 3.2 覆盖表（手写，优先级最高）

代码常量 `NameRules`，处理高概念词与正片特例，不参与注册表查找：

| 名字 | 效果 | 出处 |
|---|---|---|
| `无` / 空 / 已删除 | **认知性抹除**：目标消失、无掉落、无音效；且不写任何记录 | 【旁白 6:10-6:31】D187 |
| `地球` | 极限命名：时空扭曲 + 刺眼黄光，强制回溯 | 【旁白 6:31-7:47】 |
| 乱码 / 不可解析字符串 | 变成与 **UO-002** 同源的乱码物品 | 【旁白 6:00-6:10】 |
| `土豆` | 立刻丧失活性 → 块茎化、长出嫩芽 | 【旁白 0:19-0:36】 |
| 已死亡者的名字 | 精神崩溃死亡（同名重叠转化） | 【旁白 4:58-5:17】D363 |
| 56 字母级别的超长真名 | 完美同步复制对方动作、眼角渗泪（**意识仍在**） | 【旁白 5:24-6:00】D473 |

---

## 4. 命名矩阵：3×3 九种组合

**先立两条正交的规则**，别混：
- **同类 → 改内核**：外观不变，能力/行为变。
- **跨类 → 改物质**：真的换成分，量按守恒结算（见 4.1）。
- 转完之后**能活多久**由代价系统（§5）单独管。

打 ✓ 的是**正片验证过**的；其余是按同一条规则外推的。

| 目标 ＼ 名字 | 实体名 | 物品名 | 方块名 |
|---|---|---|---|
| **实体** | ✓ 牛→鸡<br>行为覆盖 + 寿命=字数 | ✓ 猪→钻石镐<br>行为移植 + 挖到死 | ✓ 羊→金块<br>延迟转化 + 掠夺周围金 + 内部中空 |
| **物品** | 生成该生物（多半先炸） | ✓ 木铲→下界合金镐<br>组件转让 + 耐久=字数 | ✓ 木棍→钻石块<br>爆炸 + 极小残渣 |
| **方块** | 生成该生物 + 方块崩解 | 方块消失、掉出该物品 | ✓ 原石→水<br>行为表改写 + 11 分钟挥发 |

### 4.1 统一规则：质料守恒结算（**不要给跨类写特判**）

正片自己给了统一的物理解释，不需要我们编：

- 【旁白 3:28-3:40】木棍→钻石块：爆炸后"在爆炸中心区域找到了一例**极其微小的钻石块颗粒**"，推论木棍"在突然出现的极端环境中进行了**原子重组**，**等量的转换**成了这一小粒钻石块"。
- 【旁白 3:43-3:45】"莫非该形式的转换遵循一定的**物质守恒**吗？"
- 【旁白 4:24-4:34】羊→金块：转化**不是瞬间完成**，而是"通过未知途径**不停吸收周围的金元素**"，最终"内部结构竟呈现**完全中空**"——**它自己不够，就去抢环境里的**。

所以给目标一个抽象「**质料**」值、给名字一个「**需求**」值，做一次减法：

```
差额 = 名字需求 − 目标自带质料

差额 ≤ 0   → 成功（多余质料以崩解 / 极小爆炸释放）
差额 > 0   → 先尝试从周围掠夺（半径内同族材料，或实体自身血肉）
             掠夺得到 → 延迟转化（羊→金块：历时数天，中途保留意识、流泪）
             掠夺不到 → 爆炸（威力 ∝ 差额），只留下等量转换的极小残渣
```

**这一条规则自动解释了正片里全部三种跨类结果**，也自动解释了为什么"物品做目标最容易炸"：物品的质料预算最小（一根木棍 ≈ 1，一个钻石块 ≈ 900）。

### 4.2 抽象质料表（主观量级，够用就好）

| 目标 | 质料 | 目标 | 质料 |
|---|---|---|---|
| 木棍 / 木铲 | 1 | 鸡 | 5 |
| 原石方块 | 100 | 羊 | 50 |
| 铁块 | 500 | 猪 / 牛 | 120 / 300 |
| 钻石块 | 900 | 铁傀儡 | 800 |
| 金块 | 1200 | 末影龙 | 9000 |

**不做真实 kg——相对量级对就够了**，玩家能清晰感知到"木棍变钻石块必炸""石头变钻石反而有余"。整张表是一个常量文件，随时能调。

### 4.3 为什么实体能承受跨类，物品/方块不行（正片的隐藏不对称）

正片里**所有成功的跨类转化都发生在实体身上**（猪→物品、羊→方块），而唯一的爆炸发生在**物品→方块**。这不是巧合，4.1 正好解释它：

**实体"活着"**——可以拖时间、可以从环境掠夺、可以掏空自己（羊内部中空）；物品和方块没有这个过程，只能在放置那一瞬间结算一次 → 差额不够就是爆炸。

> 一句话记忆：**实体能拖，方块够用，物品装不下就炸。**

### 4.4 同类·反转

复制源的"内核"，保留目标的"外壳"：模型/贴图/尺寸/血量上限全部不变，替换掉——
- 实体：行为、音效、掉落表、繁殖方式、交互（挤奶/剪毛/上鞍）
- 物品：挖掘等级、攻击力、工具 tag、可附魔性（走组件转让，见机制文档 §1）
- 方块：物理属性（黏附/重力/伤害/亮度/挥发，走行为表，见机制文档 §3）

### 4.5 实体→物品名 · 行为移植（正片：猪→钻石镐）

实体获得该物品的**功能行为**：猪意识怪异、不顾一切寻找附近的石头和矿石挖掘、采集能力等同钻石镐，**挖掘不久后立刻死亡**（寿命耗尽）。行为移植与质料结算无关，走实体的输出层（机制文档 §2.2）。

---

## 5. 代价系统（正片的张力所在）

### 5.1 统一抽象

`NameTagCost` 四件套，全部由"名字"自身决定：

| 代价 | 规则 | 出处 |
|---|---|---|
| **寿命 = 名字长度** | 物品：耐久上限 = 名字字符数（"下界合金镐"5 字 → 5 次）；实体：按新身份行动的次数 = 名字字符数 | 【旁白 2:34-2:37】木铲 5 次后损毁 |
| **产蛋/产出计数** | 具有产出型身份的生物，每次产出扣 1，归零后在痛苦中死亡 | 【旁白 1:38-2:00】牛 |
| **挥发/消散** | 流体类改写有存活时间（水：11 分钟） | 【旁白 2:58-3:03】 |
| **爆炸** | 跨类转化即时支付 | 【旁白 3:22-3:26】 |

### 5.2 不可逆（**必须做**）

**【旁白 1:51-1:56】"尝试让第196使用普通命名牌替换掉该名字，结果失败。"**

→ 实现：被命名过的目标打上 `NamedByBaibian` 标记；**普通命名牌无法覆盖**（拦截 `NameTagItem` 的改名），百变命名牌也**无法二次命名**（二次使用只提示"这个名字已经写死了"）。这是整个机制的核心压力来源：**玩家每次动手前都得想清楚**。

### 5.3 不做：反噬（用户决定）

正片结尾的暗线（用品最终变成新命名牌、牌子本身是人变的、残影台词）本方案**不实现**。仅在文档与图鉴文本中保留设定，方便以后加。

---

## 6. 数据持久化

> ⚙️ **三类目标的"替换"具体怎么做（组件转让 / 行为覆盖层 / mimic 方块），见 `docs/design/uo-008-naming-mechanics.md`。** 那一篇已逐条对着 NeoForge 21.1.190 源码核实，并据此修订了下表。

| 数据 | 载体 | 说明 |
|---|---|---|
| 实体的原身份/新身份/剩余寿命 | NeoForge `AttachmentType`（可 `copyOnDeath`） | 实体附件**不会自动同步客户端**，需配 `PlayerEvent.StartTracking` 补发同步包（音效重映射要客户端知道身份） |
| 被命名方块 | **`NamedBlockEntity` 的 NBT** | 原决定用世界级 `SavedData`；改用 BlockEntity 后自带序列化，不需要额外表 |
| 被命名物品 | `DataComponents` 自定义组件 `mcanomalyarchives:nametag_origin` | 存原名 + 目标 id + 剩余寿命，随物品一起走（进箱子/掉地上/跨维度都不丢） |
| 玩家侧 | 已有 `persistentData` | 成就/图鉴 |

---

## 7. 文件清单

### 新增（手工区，MCreator 不会碰）

```
anomaly/nametag/
  NameTagHandler.java        # 事件入口：EntityInteract / AnvilUpdateEvent
  NameResolver.java          # 名字 → (kind, id)，索引加载与缓存
  NameRules.java             # 覆盖表（无/地球/乱码/土豆/死者名/超长名）
  MaterialUnits.java         # 抽象质料表（木棍 1 … 金块 1200）+ 守恒结算
  NameTagCosts.java          # 代价常量 + config 读取
  NamedEntityAttachments.java # AttachmentType 定义与读写
  NamedBlockEntity.java      # 被命名方块的身份/寿命（自带 NBT）
  BlockBehaviorTable.java    # 方块"内核"行为表（水/岩浆/沙/仙人掌/TNT/金属…）
  NamedEntitySyncPacket.java # StartTracking 时把实体身份同步给客户端
  effects/EntityNaming.java  # 输出/交互/音效覆盖 + 寿命
  effects/ItemNaming.java    # 组件转让（TOOL/ATTRIBUTE_MODIFIERS/…）+ 耐久=字数
  effects/BlockNaming.java   # 替换为 named_block / 真方块 + 挥发计时
  effects/MaterialDrain.java # 环境掠夺（金/钻/铁…）
  effects/VoidErase.java     # "无"的认知性抹除
  NameTagNotifier.java       # 提示、失败反馈、缺失音效
```

### 新增（资源）

```
data/mcanomalyarchives/name_index/{zh_cn,en_us}.json      # 生成脚本产出
data/mcanomalyarchives/loot_modifiers/mineshaft_name_tag.json
data/mcanomalyarchives/loot_table/nametag/mineshaft_inject.json
data/neoforge/loot_modifiers/global_loot_modifiers.json   # 注册 GLM
assets/mcanomalyarchives/textures/item/baibian_name_tag.png   # 深褐色贴图
assets/mcanomalyarchives/models/item/baibian_name_tag.json
assets/mcanomalyarchives/blockstates/named_block.json     # appearance 属性 → 直接引用原版模型
assets/mcanomalyarchives/models/block/named_block.json
elements/BaibianNameTag.mod.json                          # MCreator 元素（locked_code: true）
elements/NamedBlock.mod.json                              # mimic 方块元素（locked_code: true）
tools/nametag/gen_name_index.py
```

**掉落接入已核对过 API**（NeoForge 21.1.190 源码）：用全局战利品修饰器 `neoforge:add_table`，条件用 `neoforge:loot_table_id` 锁定 `minecraft:chests/abandoned_mineshaft` + `minecraft:random_chance` 控制稀有度。
对应【旁白 0:56-1:04】"这些异常命名牌均来自一处矿井内的**矿车宝藏**"。

### 改动（生成区，走守卫）

| 文件 | 改动 |
|---|---|
| `init/McanomalyarchivesModItems.java` | 注册 `baibian_name_tag`（加守卫规则 + canonical） |
| `item/BaibianNameTagItem.java` | MCreator 生成的薄壳，最小实现 |
| `codex/AnomalyCodex.java` | UO-008 从 `PLANNED` 改为按物品解锁 |
| `lang/zh_cn.json` / `en_us.json` / workspace `language_map` | 物品名、提示语、图鉴正文注入 |
| `config/ComfortConfig.java` | 新增开关与数值 |
| `tools/mcreator-guard/manifest.json` | 32 → 新增 1~2 项 |

---

## 8. 参数表（默认值，全部可配）

| 参数 | 默认 | 说明 |
|---|---|---|
| `nameTagEnabled` | true | 总开关 |
| `durabilityFromNameLength` | true | 物品耐久上限 = 名字字符数 |
| `durabilityMin` / `durabilityMax` | 1 / 64 | 上限夹取 |
| `entityLifespanPerChar` | 1 次行动 | 实体寿命 |
| `crossTypeExplosionPower` | 3.0 | 跨类爆炸 |
| `crossTypeBreaksTerrain` | 跟随 `meteorTerrainDamage` | 复用已有开关 |
| `materialDrainRadius` | 24 格 | 环境掠夺范围 |
| `eraseEnabled` | true | "无"的抹除 |
| `eraseAffectsPlayers` | **false** | 不对玩家生效（防恶意） |
| `namingCooldownTicks` | 20 | 防连点 |
| `lootChanceMineshaft` | 0.08 | 矿井宝箱开出概率 |

**安全边界**：不作用于玩家、不作用于 Boss（凋灵/末影龙/监守者）、不作用于已命名目标、不作用于本模组的异常实体（避免把粉羊命名成"鸡"这种破坏体验的事）。

---

## 9. 分阶段实施

### 阶段 1 · 能玩（预计：物品 + 掉落 + 索引 + 两类效果）
物品与贴图、GLM 掉落、名字索引生成与加载、覆盖表、**同名反转**（实体 + 物品两条路径）、代价（耐久=字数 / 实体寿命 / 不可逆）、图鉴 UO-008 改写、语言注入、守卫与构建。

### 阶段 2 · 世界改写
方块路径（`named_block` + `NamedBlockEntity` + 外观 JSON + 行为表）、流体属性改写与挥发、**质料守恒结算（含爆炸与残渣）**、环境掠夺、实体→物品名的行为移植、剩余 4 种跨类组合。

### 阶段 3 · 氛围与收尾（可选）
"无"的认知性抹除、乱码名字→UO-002 同源物、极限命名（地球）+ 需负值唱片兜底、成就线、残影台词剧情（复用 `story/` 脚本系统）。

---

## 10. 验证清单

- `guard.ps1 check` 全绿；`build.ps1 -AutoFix` 通过；`BUILD SUCCESSFUL`
- 离线单测 `tools/nametag/test_name_resolver.py`：索引生成正确、同名冲突优先级、未命中回退、覆盖表优先
- 游戏内手测：
  1. 铁砧把牌改名"鸡" → 右键牛 → 不能挤奶、鸡叫、产深褐色蛋、产够次数后死亡
  2. 铁砧左槽木铲 + 右槽牌（名"下界合金镐"）→ 能挖黑曜石、5 次后碎
  3. 普通命名牌无法覆盖已命名目标
  4. 未命中名字（"地蛋"）→ 不消耗、给提示
  5. 矿井宝箱能开出（`/setblock` + 开箱验证）
  6. 单人 + 局域网双人各验一遍（沿用之前的频道修复）

---

## 11. 明确不做

- ❌ 反噬机制（用户决定，仅保留设定文本）
- ❌ 新增任何指令（沿用"指令现在没啥必要"）
- ❌ 作用于玩家 / Boss / 本模组异常实体
- ❌ 无上限许愿（"神明"这类无对应注册表的名字一律无效或归入覆盖表的有限处理）

---

## 12. 风险与备选

| 风险 | 备选 |
|---|---|
| **实体的 AI 不接管**（`setNoAi` 会连 navigation/moveControl 一起停）：被命名的牛仍按牛的 AI 游荡 | 刻意取舍。正片里那头牛也只是撞墙、悲鸣、下蛋，覆盖"输出/交互/音效"已足够；日后需要再自己写转向 |
| **方块的"内核"只能来自手写行为表**（约 10~15 条），无法任意方块名保真 | 普通方块类身份降级为"直接替换成真方块"（行为 100% 真、外观变），见机制文档 §3.5 的 A/B 两档 |
| 名字索引依赖客户端 jar 的语言文件，生成脚本在新版本可能需要微调 | 索引是静态产物，一次生成长期可用；崩了也能手写表 |
| 材料掠夺可能拆掉玩家自己的基地 | 半径可配 + 默认 24 格 + 只抽"未被玩家认领"的方块（可选：跳过带容器/已被放置记录的方块） |
| "不可逆"可能让玩家误操作后想删档 | 提供 config 关闭 `irreversibleNaming`（默认开） |

---

## 13. 实现状态（落地记录）

### 已实装

| 项 | 落点 |
|---|---|
| 物品（MCreator 元素） | `elements/BaibianNameTag.mod.json` + `item/BaibianNameTagItem.java` + 贴图/模型 + workspace 元素（`locked_code`） |
| 名字索引（1888 中文名 / 1628 英文名） | `data/mcanomalyarchives/name_index/{zh_cn,en_us}.json`，脚本 `tools/nametag/gen_name_index.py` |
| 口语别名表 | `data/mcanomalyarchives/name_index/alias_{zh_cn,en_us}.json` |
| 解析 / 覆盖表 / 质料守恒 / 代价 | `anomaly/nametag/{NameResolver,NameRules,MaterialUnits,NameTagCosts,NamedState}.java` |
| 右键实体 + 铁砧命名 + 不可逆 + tooltip | `anomaly/nametag/NameTagHandler.java` |
| 实体行为覆盖层（产蛋 / 块茎化 / 挖掘 / 掠夺转化 / 寿命） | `anomaly/nametag/NameTagTicker.java` + `effects/{EntityNaming,ItemNaming}.java` |
| 组件转让（TOOL / ATTRIBUTE_MODIFIERS / FOOD）+ 耐久=字数 | `effects/ItemNaming.java` |
| 矿井矿车宝藏掉落（8%） | `data/mcanomalyarchives/loot_modifiers/` + `data/neoforge/loot_modifiers/` |
| 图鉴 UO-008 从 PLANNED 改为按物品解锁 + 正文改写 | `codex/AnomalyCodex.java` + `lang/*.json` + workspace `language_map` |
| 守卫 39 项全绿 | `tools/mcreator-guard/manifest.json`（新增 7 条） |
| 自检脚本 | `tools/nametag/{test_name_resolver,verify_jar}.py` |

### 实现中发现并修掉的偏差（计划里没料到的）

1. **1.21.1 没有 `ENCHANTABLE` 组件**（那是 1.21.2+ 才拆出来的），所以"可附魔性"转让不了，白名单只剩 `TOOL` / `ATTRIBUTE_MODIFIERS` / `FOOD`。
2. **铁砧输入框默认显示的是左槽物品自己的名字**。不加判定的话"放着不动"也会被当成认真命名并吃掉一张牌 → 加了"输入名与左槽物品名相同则视为没动手"的判据。
3. **原版 Java 版 zh_cn 里没有"原石 / 羊 / 土豆 / 木铲"**：官方是"石头 / 绵羊 / 马铃薯 / 木锹"。"原石""土豆"是基岩版或口语叫法 → 必须有别名表，否则玩家按习惯输入会被判"查无此名"。
   同时**故意不收录"地蛋"**——正片弹幕实测这个别称无效，保留"只认主流叫法"的设定（观众试的正是这个词）。
4. **客户端 jar 里只有 en_us，不带 zh_cn**（其余语言在 assets 对象里），生成脚本因此要支持从目录读；本机可用的最新 zh_cn 是 1.21.9 时代的文件，靠"键集 100% 覆盖 1.21.1 + 注册表校验"兜住（索引里不存在的 id 在加载时会被丢弃）。
5. **同类型但源物品没有可转让内核时不能白送**（否则"木棍 → 下界合金锭"就是复制器）→ 这类同样走质料守恒结算。
6. **名字就是它自己原本的类型时**（牛→"牛"）不该按"改写过的存在"收寿命，否则给牛起名"牛"十秒后就暴毙。
7. **掠夺来源放宽到矿石形态**：原本要求附近正好有一块金块，而正片里羊抽的是"含金元素的设备" → 放宽成"金块 / 金矿石 / 深层金矿石 / 粗金块 / 下界金矿石"任一种。

### 尚未实装（按原计划留到后续阶段）

- **方块的 mimic 外观**：现在走"档 B"——直接替换成真方块，行为 100% 保真但外观会变。`named_block` + BlockEntity + blockstate 引用原版模型留到阶段 2。
- **物品侧的「无 / 地球 / 乱码」**：给"该档案尚未被协会解明"的明确提示，且不消耗命名牌。
- **音效重映射**（`PlayLevelSoundEvent.AtEntity` 把牛叫换成鸡叫）+ **客户端身份同步**（`PlayerEvent.StartTracking` 补发同步包）：需要网络包，留到阶段 2。
- **正片暗线**：反噬（用过的人最终变成命名牌）、残影台词剧情。
- **配置项**：`NameTagCosts` 现在是常量，还没接 `ComfortConfig`。
