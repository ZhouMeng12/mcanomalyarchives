# Strangerecord Mod 代码审查报告

**日期**: 2026-07-29 | **环境**: NeoForge 1.21.8 + GeckoLib 5.2.2 | **文件数**: ~60

---

## 发现总览

| 维度 | 高 | 中 | 低 | 总计 |
|------|----|-----|-----|------|
| Bug | 10 | 15 | 16 | 41 |
| 性能 | 2 | 6 | 11 | 19 |
| 代码质量 | 0 | 10 | 22 | 32 |
| 安全 | 1 | 5 | 9 | 15 |
| **总计** | **13** | **36** | **58** | **107** |

---

## 立即修复 (严重程度: 高)

### 1. [破坏性] `PURPLEHAND_SOUNDS` 未注册 (`StrangerecordMod.java`)
- `DeferredRegister<SoundEvent> PURPLEHAND_SOUNDS` 从未调用 `register(modEventBus)`，运行时引用 `PURPLEHAND_SHOOT` 会抛异常。

### 2. [破坏性] `GameRule` 事件总线错误 (`StrangerecordModGameRules.java`)
- `@EventBusSubscriber` 使用默认 Bus (GAME) 但 `FMLCommonSetupEvent` 是 MOD bus 事件，导致 `cornPoppySad` 永远不注册，所有引用处 NPE。

### 3. [破坏性] `PlayerSlowSwingMixin` 在专用服务器崩溃
- `@Mixin(Player.class)` 调用了客户端专用类 `PurpleDogPettingClientHandler`，专用服务器加载时 `NoClassDefFoundError`。

### 4. [设计缺陷] 表演模式退出后 AI 永久丢失 (`PurpleMonsterEntity`, `PurpleDogEntity`)
- `setPerformMode()` 进入时清除所有 goals，退出时仅 `setNoAi(false)` 未重新注册，实体变"脑残"。

### 5. [设计缺陷] 能量消耗仅客户端执行 (`SanityMonitorHudOverlay`)
- `consumeEnergy()` 在 `RenderGuiEvent.Post` 中修改客户端 ItemStack，服务端不同步。重登后能量重置。

### 6. [设计缺陷] `StrangeFishingRodLootHandler` 单人模式失效
- `@EventBusSubscriber(Dist.DEDICATED_SERVER)` 使单人游戏 (Integrated Server) 中处理器不注册，钓鱼战利品系统完全无效。

### 7. [渲染崩溃] `CatRendererMixin` NPE 风险
- `currentCat.getCustomName().getString()` 在猫无自定义名称时 NPE，导致渲染崩溃。

### 8. [渲染崩溃] `LevelRendererMixin` Camera 未恢复
- `setDetached(true)` 后若其间抛异常，Camera 永久 detached，所有实体可见性判断异常。

### 9. [渲染崩溃] `PurpleDogRenderer` / `QuRenderer` 空指针
- 内嵌 `AnimatedModel` 的 `this.entity` 可能为 null 时调用 `keyframeAnimation.apply()`，导致 NPE。

### 10. [渲染泄漏] `ItemInHandRendererMixin` 每帧创建 `PlayerRenderState` 对象
- 在高帧率下每帧 `new PlayerRenderState()` + `createRenderState()` 造成严重 GC 压力。

---

## 尽快修复 (严重程度: 中)

### 安全相关
- `RemovePurpleMonsterPacket` 无任何实体所有权校验，客户端可删除/杀死任意实体
- `PetPurpleDogPacket` / `PettingCompletePacket` 无实体归属校验
- `AdvanceToPhase2Packet` / `AdvanceToPhase4Packet` 无阶段状态校验

### 功能 Bug
- `AnbulaEntity` 晕厥第0帧 `faintTicks==0` 永远为 false（已在自增后检查）
- `OrangeSaplingBlock` 树苗拼写错误 `oreange_tree`
- `HudLogicTest` 测试与 `NordIndexResolver.NAMES` 不一致
- `PurplehandItem` 的 `WeakHashMap<ItemStack, ...>` 使用 `==` 比较，语义错误
- `StangeCloudEntity` 的 `isFirstSpawn` 未持久化
- `SittingEnityEntity.aiStep()` 缺少 `@Override`

### 网络问题
- `PurpleMonsterPhase1ClientHandler` 使用 `player.connection.send()` hack 而非 `PacketDistributor.sendToServer()`
- `PurpleDogPettingClientHandler` 客户端丢弃物品不同步服务端

---

## 性能热点

| 文件 | 问题 | 频率 |
|------|------|------|
| `AnbulaEntity.java` | 每 tick 创建新 `MobEffectInstance` (即使效果存在) | 每 tick |
| `PurpleMonsterTriggerHandler.java` | `stillTimers` 用非线程安全 `HashMap` | 每 tick |
| `SanityMonitorDetector.java` | 每帧反射调用 Curios API | 每帧 |
| `PlayerLookAtCornPoppyListener.java` | 每 tick 遍历所有维度所有实体 | 每 tick |
| `AddictionClientHandler.java` | 每 tick 扫描 9261 个方块 | 每 tick |
| `AnbulaBlockGoal / AnbulaDodgeGoal / PickupWeaponGoal` | 每 tick AABB 实体查询 | 每 tick |
| `ItemInHandRendererMixin.java` | 每帧创建 `PlayerRenderState` | 每帧 |
| `PurpleMonsterScreen.java` | 每 tick 通过 GLFW 创建 `DoubleBuffer` | 每 tick |

---

## 代码质量共性问题

1. **多个 Renderer 存储 entity 字段但未读取** — `SvanRenderer`, `PrisonerRenderer`, `SittingEnityRenderer`, `StangeCloudRenderer`（MCreator 生成反模式）
2. **`AnbulaMeleeGoal` 反射访问 `MeleeAttackGoal.ticksUntilNextAttack`** — 极脆弱，跨版本必定失效
3. **`AnbulaBlockGoal` / `AnbulaDodgeGoal` 直接 `setBaseValue` 覆写属性** — 破坏药水/装备加成
4. **`WatchCornPoppyGoal` / `AvoidPoppyGoal` 在 `canUse()` 中有副作用** — 违反 Goal 契约
5. **事件包名不一致** — 部分在 `event` 包，部分在 `events` 包
6. **`CornPoppyBlock` 使用字符串坐标 key** — 应用 `BlockPos` 作 key
7. **邪门魔法数字** — `69` (PlayerAnimationRendererMixin)、`0.15/0.3` (AnbulaBlockGoal)
8. **反射滥用** — 3处使用反射修改私有字段（`StrangeFishingRodItem`、`AnbulaMeleeGoal`、`SanityMonitorDetector`）
9. **客户端的 `ConcurrentHashMap`** — 多个客户端 handler 在单线程上下文用同步 Map
10. **异常静默吞没** — 5处空 `catch` 块，无日志

---

## MCreator 生成代码标注

以下目录/文件主要由 MCreator 生成，手动修改需使用 `user code block` 注释：

- `init/` 目录下所有文件（标注 "This file will be REGENERATED on each build"）
- `procedures/` 目录下所有文件
- `block/` 目录下的 `Orange*Block.java`, `Cloud*Block.java`, `SadPoppyBlock.java`
- `item/` 目录下的 `HearticonItem`, `IconItem`, `SadiconItem`, `OrangeItem`, `CloudWaterButtleItem`
- `client/renderer/` 目录下的标准实体渲染器（非 `AnbulaRenderer`, `PurpleMonsterRenderer`）
- `client/model/` 目录下所有模型类
