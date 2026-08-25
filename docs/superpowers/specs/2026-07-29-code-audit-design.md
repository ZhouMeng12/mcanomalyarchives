---
design_type: feature
created_at: 2026-07-29
---

# Strangerecord Mod 全面代码审查

## Intent Contract

```
intent: 对 Strangerecord 模组全部 Java 源码做系统性审查，发现 Bug、性能问题、代码质量问题和安全风险。
constraints:
  - 只报告，不自动修改代码
  - 不破坏现有功能
  - 审查基于 NeoForge 1.21.8 + GeckoLib 5.2.2 环境
success_criteria:
  - 每个模块输出结构化审查报告
  - 汇总报告包含所有发现 + 严重程度分级
  - 用户确认报告内容完整
risk_level: low
```

## Verification Contract

```
verify_steps:
  - run tests: 无需运行测试（只读审查）
  - check: 每个模块报告包含至少 Bug/性能/质量/安全 四个维度的扫描结果
  - confirm: 用户确认所有发现
```

## Governance Contract

```
approval_gates:
  - 设计文档批准后进入审查执行
  - 汇总报告提交用户审核
rollback: 无需回滚（只读操作）
ownership: 开发者
```

## Scope

| 范围内 | 范围外 |
|--------|--------|
| 所有 `src/main/java/` 下的 Java 源文件 | 资源文件 (JSON、PNG、模型) |
| 运行时 Bug 风险 | 功能需求变更 |
| 性能优化机会 | 第三方库 (GeckoLib) 内部代码 |
| 代码质量和可维护性 | Gradle 构建配置 |
| 客户端/服务端安全边界 | MCreator 自动生成代码的设计理念 |

## Decisions

| # | 决定 | 选择 | 拒绝的替代方案 |
|---|------|------|----------------|
| 1 | 审查方式 | 6 个子代理并行扫描 | 逐一审查（太慢）、单代理全扫（太浅） |
| 2 | 发现处理 | 只报告，不修改 | 直接修复（高风险）、自动修复低危项（范围不明） |
| 3 | 模块划分 | 按功能分 6 组 | 按目录结构分（耦合高）、按文件大小分（无逻辑关联） |
| 4 | 审查深度 | 全量全面扫描 | 仅高风险优先（可能遗漏）、仅 Bug（不够全面） |

## Surface

### 审查模块分组

**模块 1 — 实体层** (10 文件): AnbulaEntity, PurpleMonsterEntity, SvanEntity, PrisonerEntity, PurpleDogEntity, QuEntity, StangeCloudEntity, SittingEnityEntity, ControllableMonster, PurpleArrowEntity

**模块 2 — AI 系统** (11 文件): AnbulaBlockGoal, AnbulaDodgeGoal, AnbulaMeleeGoal, AnbulaRangedAttackGoal, AvoidPoppyGoal, ControlledWanderGoal, FollowPlayerGoal, PickupWeaponGoal, StayGoal, SwitchWeaponGoal, WatchCornPoppyGoal, NpcMessages

**模块 3 — 渲染层** (12 文件): 所有 Renderer, Model, PurpleMonsterScreen, 所有 Mixin, 动画模型, PurplehandItemRenderer

**模块 4 — 事件 & 网络** (16 文件): 所有 Event handler, 所有 Network packet, Phase 过渡处理器, Sanity events, CornPoppy events

**模块 5 — 物品 & 方块** (10 文件): PurplehandItem, StrangeFishingRodItem, ChairBlock, CornPoppyBlock, BlockEntities, Procedures, DetecterItem, 其他物品

**模块 6 — Sanity & 初始化** (8 文件): PlayerSanity, SanityClientHandler, Sanity 相关, Init registries, Commands, Effects, Potions, Dimension

### 审查维度

每个文件按四个维度扫描：
- **Bug** — 空指针、逻辑错误、边界条件、状态机不一致、客户端/服务端混用
- **性能** — 每 tick 重计算、不必要的对象分配、渲染路径优化
- **代码质量** — 重复代码、过长方法、异常处理缺失、魔法数字
- **安全** — 网络包校验缺失、权限检查、数据注入风险

### 输出格式

每个模块输出结构化报告，问题按严重程度 (高/中/低) 分级，最终汇总为一个完整文档。

## Risks & Open Questions

| 风险 | 影响 | 缓解 |
|------|------|------|
| 静态审查可能遗漏运行时 Bug | 中 | 标记不确定项为"需运行时验证" |
| MCreator 生成代码限制修改范围 | 低 | 标注哪些是 MCreator 生成代码 |
| 审查报告信息量大 | 低 | 按严重程度排序，关键问题优先展示 |
