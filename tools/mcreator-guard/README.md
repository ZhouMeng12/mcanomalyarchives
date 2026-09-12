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
| `mechanics-package` | 存在检查 | `anomaly/pinksheep/` 两个文件丢失（可用 `git checkout` 恢复） |
| `no-stale-prefix` | 反向检查 | 出现旧模组名 `strangerecord` 残留 → 注册名/网络通道不一致（会导致联机掉线） |

`contains` / `exists` / `noMatch` 三类**不会**被 `apply` 自动改（改动位置不确定），只在报告里给出 `fixHint`。

## 还原精度

- 整文件项：`Copy-Item` 字节级复制，编码与行尾（LF/CRLF）原样保持；
- 代码块项：按锚点定位后只替换该段，其余部分（包括 CRLF 文件的行尾风格）不动。

自测记录：模拟"实体类被还原成怪物模板 + 注册表被还原成 MONSTER/0.6×1.8"，
`check` 正确报 2 项漂移；`apply` 后注册表文件 SHA256 与漂移前**完全一致**（`1ADE44EE7AB3…`）。

## 版本控制

`.gitignore` 曾有第 30 行 `net/` 未锚定仓库根，git 会把它匹配到任意层级的 `net` 目录，
**导致 `src/main/java/net/` 整棵源码树（224 个文件）被静默忽略**，前 4 次提交里一行 Java 都没有。
现已改为 `/net/`（并把 `software/`、`run/`、`logs/`、`backup/` 一并锚定到仓库根）。

因此现在有双保险：守卫脚本负责"局部还原"，git 负责"整体回退"：

```powershell
git checkout -- src/main/java/net/mcreator/mcanomalyarchives/   # 生成区被大面积覆盖时
```
