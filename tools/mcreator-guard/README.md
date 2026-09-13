# MCreator 生成区守卫（tools/mcreator-guard）

## 这个目录解决什么问题

粉羊（UO-012 幸运粉羊）的代码分两类：

| 类别 | 位置 | MCreator 重新生成代码时 |
|---|---|---|
| **生成区**（MCreator 根据 `elements/*.mod.json` 生成） | `entity/PinkSheepEntity.java`、`client/renderer/PinkSheepRenderer.java`、`init/McanomalyarchivesModEntities.java`、`neoforge/biome_modifier/pink_sheep_biome_modifier.json` | **会被还原成默认模板**：羊→怪物、羊模型→默认渲染、`CREATURE`→`MONSTER` |
| **自建区**（MCreator 不认识这些文件） | `anomaly/pinksheep/`（机制与栖息地规则）、`events/PinkSheep*.java`、`client/BlinkClientHandler.java`、`network/PlayerBlinkPacket.java` | 不受影响，永远不会被覆盖 |

生成区里那点代码躲不开，所以采用两层防护：

1. **把机制搬出生成区**（已完成）—— 实体类被削成"薄壳"，只留不能搬走的东西：
   继承 `Sheep`、强制粉色、两个防 NPE 的空覆写、`init` / `createAttributes` 两个约定钩子。
   观察者效应状态机、消散转移、选点规则全部在 `anomaly/pinksheep/`，即使薄壳被覆盖，
   损失也只是几十行可还原的样板。
2. **快照 + 一键还原**（本目录）—— 记录生成区里"我们对模板做过的改动"，漂移可检测、可还原。

## 文件

| 文件 | 作用 |
|---|---|
| `manifest.json` | 保护清单：每项要检查什么、怎么还原、为什么 |
| `canonical/` | **已知良好版本**快照（由 `-Action snapshot` 生成，不要手改） |
| `guard.ps1` | 检查 / 还原 / 快照 |
| `build.ps1` | 先守卫检查再构建，漂移时不出坏包 |

> `guard.ps1` / `build.ps1` 必须保存为 **UTF-8 带 BOM**：Windows PowerShell 5.1 会把无 BOM 的
> UTF-8 脚本按 GBK 解码，中文注释会直接撑爆语法解析。

## 用法

```powershell
# 1) 检查：是否被 MCreator 覆盖过（有漂移 → 退出码 1）
powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/guard.ps1 -Action check

# 2) 还原：把漂移项改回快照（只动清单里列出的文件和代码块）
powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/guard.ps1 -Action apply

# 3) 安全构建：检查通过才构建（-AutoFix 则先自动还原）
powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/build.ps1 -AutoFix

# 4) 改了机制/薄壳并确认编译通过后，把当前状态固化为新基线
powershell -ExecutionPolicy Bypass -File tools/mcreator-guard/guard.ps1 -Action snapshot
```

## 什么时候该跑

| 时机 | 命令 |
|---|---|
| 在 MCreator 里点了「重新生成代码」、或解锁并改动了 PinkSheep 元素之后 | `check` → 有漂移就 `apply` |
| 改完机制代码、`gradlew build` 通过之后 | `snapshot`（否则下次 `apply` 会用旧版本覆盖新代码） |
| 提交 git 之前 | `check` 应全绿；不绿说明仓库里的是坏状态 |
| 只是想确认清单内容 | `status` |

## 清单保护的 7 项

| id | 类型 | 覆盖后会怎样 |
|---|---|---|
| `pink-sheep-entity` | 整文件 | 实体不再是 `Sheep`，变成怪物模板：没有羊模型/粉色/剪毛 |
| `pink-sheep-renderer` | 整文件 | 渲染退回默认人形，粉羊不可见或不显示羊毛层 |
| `pink-sheep-registration` | 代码块 | 注册退回 `MobCategory.MONSTER` + `sized(0.6f, 1.8f)`（1.8 格高的怪物判定） |
| `pink-sheep-biome-modifier` | 整文件 | 自然刷怪权重文件被改写，与结构式生成器冲突 |
| `mod-init-hooks` | 内容检查 | 主类 `user code block mod init` 里的 5 行 `init()` 丢失 → 整个粉羊机制不生效 |
| `mechanics-package` | 存在检查 | `anomaly/pinksheep/` 文件丢失（可用 `git checkout` 恢复） |
| `no-stale-prefix` | 反向检查 | 出现旧模组名 `strangerecord` 残留 → 注册名/网络通道不一致（会导致联机掉线） |
| `pink-sheep-renderer-registration` | 内容检查 | 渲染器注册行丢失 → 粉羊退回默认渲染 |
| `pink-sheep-entity-hooks` | 内容检查 | 实体 init/属性钩子调用丢失 → 粉羊没血量、没生成规则 |
| `meteor-damage-type` | 整文件 | 陨石伤害类型丢了 → 保底 200 变成可被护甲减免 |
| `meteor-bypass-tags` | 存在检查 | 5 个 bypasses_* 标签丢了 → 保底 200 名存实亡 |
| `lang-zh-pink-sheep` | 代码块 | 粉羊词条被写成英文（workspace 里没中文） |
| `lang-zh-extra` / `lang-en-extra` | 插入式 | 自定义词条（端坐者/创造标签页/陨石死亡信息）被整文件重写冲掉 |
| `structure-nbt-namespace` | NBT 反向检查 | 结构模板里残留旧命名空间 → 结构生成时方块变空气、实体不生成 |
| `structure-jigsaw-refs` | 内容检查 | 拼图池指向的模板名被改坏 |
| `stange-cloud-registration` | 代码块 | 伪云碰撞箱被改回 0.6×1.8 → 模型 30 格宽被视锥剔除，看云边缘时整朵云消失 |
| `stange-cloud-element-hitbox` | 内容检查 | MCreator 元素里的碰撞箱字段（生成 `.sized()` 的源头）被改回 |
| `pink-sheep-advancements` | 存在检查 | 粉羊进度线的 3 个成就元素或生成的 advancement 资源丢失 |
| `mod-constructor-modcontainer` | 内容检查 | 构造器签名被 MCreator 改回单参 → 舒适度配置静默失效 |
| `comfort-config` | 内容检查 | 舒适度配置本体（震动强度 / 眨眼黑屏 / 陨石是否破坏地形） |

> 伪云为什么要 30×5 的碰撞箱：渲染器 `poseStack.scale(20f,20f,20f)` 把模型放大了 20 倍，
> 模型本体 `addBox(-12,-4,-6, 24,4,12)` + `PartPose.offset(0,24,0)` 换算成方块是
> **30 宽 × 5 高 × 15 深**（底面正好贴脚底）。而碰撞箱只有 0.6×1.8 ——
> **视锥剔除用的是碰撞箱**，所以看云边缘时那 0.6 格的小盒子出了屏幕，整朵云就被剔除掉了。
> 碰撞箱覆盖模型后就不会再被误剔除（实体是 `setPos` 直移 + 免疫 IN_WALL，放大碰撞箱不会卡地形）。
> 因为实体转向会交换长宽方向（30×15 ↔ 15×30），而 AABB 不能旋转，所以取 30×30 的正方形覆盖面。

`contains` / `exists` / `noMatch` 三类**不会**被 `apply` 自动改（改动位置不确定），只在报告里给出 `fixHint`。

规则类型一览（`manifest.json` 的 `kind`）：

| kind | 检查方式 | 能否自动还原 |
|---|---|---|
| `full` | 整文件标记检查（`expectAll`） | ✅ 字节级复制 canonical |
| `block` | 锚点区间内的标记检查 | ✅ 只替换该区间，其余不动 |
| `insertBefore` | 文件里是否含这些键 | ✅ 只把**缺失的键**插到锚点行（通常是结尾 `}`）之前，自动处理 JSON 逗号 |
| `contains` | 文件里是否含指定文本 | ❌（给 fixHint） |
| `exists` | 文件是否存在 | ❌（给 fixHint） |
| `noMatch` | 反向：不应出现的文本 | ❌（给 fixHint） |
| `nbtNoMatch` | 反向：结构 `.nbt` **解压后**不应出现的文本 | ❌（用 tools/structure-fix 修） |

`insertBefore` 是为 lang 这类"MCreator 整文件重写、我们的词条在末尾"的场景准备的：
逐行比对键名，只插缺的那几条，所以幂等、不会产生重复键（重复键会让 lang 解析炸掉），
也会自动给上一行补逗号、给最后一条去掉逗号。

## 还原精度

- 整文件项：`Copy-Item` 字节级复制，编码与行尾（LF/CRLF）原样保持；
- 代码块 / 插入项：按锚点定位后只改该段，其余部分（包括 CRLF 文件的行尾风格）不动。

自测记录（都是真实发生过的故障场景）：

1. 实体类被还原成怪物模板 + 注册表被还原成 `MONSTER`/`0.6×1.8` →
   `check` 报 2 项漂移；`apply` 后注册表 SHA256 与漂移前**完全一致**（`1ADE44EE7AB3…`）。
2. MCreator 重写整个 lang（删掉自定义词条 + 粉羊词条回英文）→
   `check` 报 3 项漂移；`apply` 后 JSON 依然合法、无重复键，词条全部回来。
3. **基线保护**：曾出现 `snapshot` 把 MCreator 刚改坏的注册表存成"好基线"，
   之后 `apply` 反而忠实还原成坏的。现在检查不通过的项**拒绝覆盖已有基线**。

> ⚠️ 如果你在 MCreator 里点了「重新生成代码」，先跑一次 `-Action apply` 再继续写代码，
> 否则你手上的工作副本是"已经被改坏"的状态。重建 MCreator 工作区时它还会重写
> `mcanomalyarchives.mcreator` 里的 language_map —— 那里没有中文词条，
> 所以每次重生成都需要守卫把 lang 补回来（或直接在 MCreator 界面里给元素填中文名）。

## 结构模板（.nbt）为什么需要单独的工具

模组从 `strangerecord` 改名成 `mcanomalyarchives` 时，`src/main/resources` 下的 JSON
（worldgen / template_pool / loot_table / advancement）用文本替换就能全部改完，
**但结构模板 `.nbt` 是 gzip 压缩的二进制，文本搜索扫不到里面**：
`adass.nbt` 的调色板里写着 `strangerecord:chair` / `strangerecord:computer`，
实体列表里写着 `strangerecord:anbula` / `david` / `potter` / `prisoner` / `svan` / `yifulin`；
`strtree.nbt` 里是 `strangerecord:strange_tree` + 两个方块。

后果：编译、构建全部正常，**只有进游戏才会发现**——结构生成出来方块变空气、实体不生成。

```powershell
python tools/structure-fix/fix_structure_nbt.py dump            # 列出每个结构里的方块/实体
python tools/structure-fix/fix_structure_nbt.py fix --apply     # 修复（写入前自动备份 + 自检）
python tools/structure-fix/fix_structure_nbt.py verify          # 校验引用的 ID 在模组里真实存在
```

工具只做**字节级补丁**：NBT 字符串是「2 字节长度前缀 + UTF-8 内容」，改名后长度会变，
所以同步改长度前缀，其余字节一个都不动。之所以不"解析成对象再序列化回去"，
是因为那样标量类型会被反推（TAG_Byte/TAG_Short/TAG_Float 可能被写成 TAG_Int/TAG_Double），
而 MC 读 NBT 是按类型查的（`CompoundTag.getByte` 类型不符就返回 0）—— 这种静默漂移
比原名更难查。脚本在写回前会做反向补丁自检：还原结果必须与原始字节逐字节相同。

守卫里的 `structure-nbt-namespace` 项会在每次 `check` 时解压这些 `.nbt` 反查旧命名空间，
所以这个问题不会再悄悄溜进构建产物。

## 舒适度配置（游戏体验）

`config/ComfortConfig.java`（非生成区）+ 在模组构造器里注册：

| 配置项 | 默认 | 作用 |
|---|---|---|
| `comfort.screenShakeIntensity` | 1.0 | 屏幕震动强度倍率（0 关闭 / 上限 2.0）。影响怪树地震、伪云掠过、粉羊陨石下落震颤与落地大震 |
| `comfort.blinkDarkness` | 1.0 | 粉羊凝视触发眨眼的黑屏浓度（0 = 不黑屏） |
| `comfort.meteorTerrainDamage` | true | 陨石是否破坏地形（关掉只保留伤害与演出，不炸建筑） |

改法（二选一）：
- 游戏内：模组列表 → MC诡异见闻录 → **Config**（NeoForge 自动界面）
- 文件：`config/mcanomalyarchives-client.toml`（客户端本地，不影响服务器）

> 注册配置必须在**模组构造期**，而 NeoForge 21.1 已移除 `ModLoadingContext`，
> 所以构造器必须注入 `ModContainer`：`McanomalyarchivesMod(IEventBus, ModContainer)`。
> 这是生成区里的签名，MCreator 重生成会改回单参版本（配置会静默失效），
> 因此清单里加了 `mod-constructor-modcontainer` 盯着它。

## 见闻录（图鉴）系统

`codex` 包（非生成区）+ 一个 MCreator 物品元素：

| 部分 | 位置 | 说明 |
|---|---|---|
| 档案数据 + 解锁状态 | `codex/AnomalyCodex.java` | 12 条 UO 档案；解锁状态存玩家 `persistentData` 的位掩码（不占数据组件、不用注册 AttachmentType） |
| 周期扫描 | `codex/CodexScanner.java` | 每 2 秒对在线玩家扫一次：附近有该实体 / 背包有该物品 → 解锁；死亡重生时搬运位掩码（`PlayerEvent.Clone`） |
| 右键打开 | `codex/CodexItemHandler.java` | 监听 `PlayerInteractEvent.RightClickItem`，不碰生成区代码 |
| 客户端界面 | `client/codex/CodexScreen.java` | 左列 12 条（未解明显示 ???），右侧详情，含进度 |
| 同步 | `network/CodexSyncPacket.java` | 位掩码 + 「打开界面」标志一起发 |
| 承载物品 | `elements/AnomalyCodex.mod.json` | MCreator 元素，**`locked_code: true`**（用户建议的防覆盖） |

档案内容取自 wiki 的 UO 系列（UO-001~011，字段按原文用「项目等级 / 保密协议等级」而不是「威胁等级」），
UO-012 幸运粉羊是本模组自己实现的、wiki 上暂无页面，等级由本模组自定。
未实装的 6 条（UO-002/006/007/008/009/010）以 `PLANNED` 标记：名称可见但要显示「尚未实装」。

wiki 原文抓取存放在 `docs/codex-research/`，**已加进 .gitignore**（wiki 内容为 CC BY-SA，
只作本地研究资料，不随仓库分发）。

## 自定义词条的根因修复：写进工作区 language_map

只往 `lang/*.json` 里插词条的做法**每次都会被 MCreator 冲掉**——它重新生成代码时会拿工作区
`mcanomalyarchives.mcreator` 里的 `language_map` 重写整个 lang 文件。守卫的 `insertBefore`
只能事后补，玩家那次运行照样会看到一串原始键名（"文字全变成注册名"就是这么来的）。

正解是把自定义词条**写进 `language_map`**（结构就是 `{"en_us": {key: value}, "zh_cn": {...}}`）：

```powershell
python tools/structure-fix/inject_language_map.py   # 从 canonical 读词条 → 写进工作区
```

之后 MCreator 会自己把这些词条生成出来，不必再依赖守卫事后补。
注入用的是**字符串感知的括号匹配 + 文本插入**，不会重排整个工作区文件（工作区里可能有
只有大小写不同的重复键，整体 JSON round-trip 会静默丢键）。

> ⚠️ 踩过的坑：`gen_codex_lang.py` 当初**整文件覆盖**了 `canonical/*.extra.txt`，
> 把之前 4 条自定义键（陨石死亡信息、矿车名、端坐者、创造标签页）从基线里挤掉了——
> 结果它们既不在工作区也不在基线，守卫只能报 MANUAL 却修不了。
> 以后生成 canonical 要**追加**，不要覆盖。

## 版本控制

`.gitignore` 曾有第 30 行 `net/` 未锚定仓库根，git 会把它匹配到任意层级的 `net` 目录，
**导致 `src/main/java/net/` 整棵源码树（224 个文件）被静默忽略**，前 4 次提交里一行 Java 都没有。
现已改为 `/net/`（并把 `software/`、`run/`、`logs/`、`backup/` 一并锚定到仓库根）。

因此现在有双保险：守卫脚本负责"局部还原"，git 负责"整体回退"：

```powershell
git checkout -- src/main/java/net/mcreator/mcanomalyarchives/   # 生成区被大面积覆盖时
```
