# 紫怪自然触发系统 — 设计文档

> 日期: 2026-07-28 | 状态: 已确认

## 1. 概述

当前紫怪只能通过 `/purplegui 1` 命令触发。本设计添加基于游戏状态的自然触发机制：低理智或低血量 + 静止一段时间 → 触发紫怪 Phase 1 UI。

## 2. 触发条件

| 参数 | 值 | 说明 |
|------|-----|------|
| 理智阈值 | ≤ 15 | 与血量条件为 OR 关系 |
| 血量阈值 | ≤ 30% | 与理智条件为 OR 关系 |
| 静止时长 | 60 秒 (1200 tick) | 玩家无移动/无受伤/无交互 |
| 冷却时间 | 20 分钟 (24000 tick) | 触发后进入冷却，期间不再触发 |

完整触发条件（所有必须同时满足）：
1. 玩家为生存模式
2. 不在冷却期内
3. 不在已有的紫怪 UI 事件中
4. （理智 ≤ 15 **或** 血量 ≤ 30%）
5. 连续静止 ≥ 60 秒

## 3. 实现方案

### 3.1 新增文件

**`event/PurpleMonsterTriggerHandler.java`**

服务端 `ServerTickEvent.Post` 事件处理器。

核心逻辑：
```
每 tick 遍历所有在线玩家:
  ├── 生存模式？冷却期？已有事件？ → 否则跳过
  ├── (理智 ≤ 15 或 血量 ≤ 30%)？
  │   ├── 是 + 静止？ → stillTimer++
  │   │   ├── stillTimer ≥ 1200？
  │   │   │   └── 触发 → 生成紫怪 + 发送OpenPurpleGuiPacket + 记录冷却时间戳
  │   │   └── 继续
  │   └── 否 → stillTimer = 0
  └── 条件不满足 → stillTimer = 0
```

静止检测：
- `player.xxa == 0 && player.zza == 0`（无移动输入）
- 未被伤害（记录受伤时间戳，受伤后重置计时）
- 不在骑乘、不在 GUI 中

### 3.2 冷却存储

使用 NeoForge Attachment（与 `PLAYER_SANITY` 类似）：
- Key: `PURPLE_MONSTER_TRIGGER_COOLDOWN`（类型: `Long`，存储 gameTime 时间戳）
- 注册于 `StrangerecordModAttachments.java`

### 3.3 修改文件

| 文件 | 修改内容 |
|------|---------|
| `init/StrangerecordModAttachments.java` | 注册 `PURPLE_MONSTER_TRIGGER_COOLDOWN` attachment |
| `event/PurpleMonsterTriggerHandler.java` | 新增文件，核心触发逻辑 |

无需新增网络包，复用现有 `OpenPurpleGuiPacket` 和命令中已有的紫怪生成逻辑。

## 4. 触发行为

触发时执行（已在 `PurpleGuiCommand.java` 中实现，直接复用）：

1. 在玩家面前 2 格生成 `PurpleMonsterEntity`（TEXTURE_VARIANT=0，purpleboy 纹理）
2. 设置紫怪为 PERFORM_MODE，朝向玩家
3. 发送 `OpenPurpleGuiPacket(entityId, 1, -1)` 给玩家
4. 记录冷却时间戳

## 5. 常量汇总

```java
static final int SANITY_THRESHOLD = 15;
static final float HEALTH_RATIO_THRESHOLD = 0.3f;
static final int STILL_TIME_REQUIRED = 1200;    // 60 秒
static final int COOLDOWN_TICKS = 24000;         // 20 分钟
```

## 6. 验证方法

1. `/purplegui 1` 命令依然正常工作（不受冷却影响）
2. 创造模式/旁观模式玩家不触发
3. 触发后 20 分钟内同一玩家不重复触发
4. 行走/跳跃/受伤会重置静止计时
5. 仅理智 ≤ 15（血量正常）时静止 60 秒 → 应触发
6. 仅血量 ≤ 30%（理智正常）时静止 60 秒 → 应触发
7. 条件不满足时计时器归零
