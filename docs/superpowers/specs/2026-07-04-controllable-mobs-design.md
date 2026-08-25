# 可控实体状态切换 — 设计文档

**日期**: 2026-07-04  
**项目**: MC诡异见闻录 (strangerecord)  
**MC 版本**: 1.21.8 / NeoForge 21.8.31

---

## 1. 概述

允许玩家空手右键点击非狂暴状态的安布拉(Anbula)和死刑犯(Prisoner)，在三种 AI 状态之间循环切换：
- **原地停留 (STAY)** — 实体不移动
- **四处闲逛 (WANDER)** — 默认状态，随机走动
- **跟随玩家 (FOLLOW)** — 跟随触发右键的玩家

切换时在 ActionBar 显示带颜色的提示信息，状态通过 NBT 持久化。

---

## 2. 类层次

```
Monster
  └── ControllableMonster (abstract, 新增)
        ├── AnbulaEntity  (修改)
        └── PrisonerEntity (修改)
```

---

## 3. 文件清单

### 新增文件

| 文件 | 路径 |
|------|------|
| ControllableMonster.java | `src/main/java/net/mcreator/strangerecord/entity/ControllableMonster.java` |
| StayGoal.java | `src/main/java/net/mcreator/strangerecord/entity/ai/StayGoal.java` |
| ControlledWanderGoal.java | `src/main/java/net/mcreator/strangerecord/entity/ai/ControlledWanderGoal.java` |
| FollowPlayerGoal.java | `src/main/java/net/mcreator/strangerecord/entity/ai/FollowPlayerGoal.java` |

### 修改文件

| 文件 | 改动 |
|------|------|
| AnbulaEntity.java | `extends Monster` → `extends ControllableMonster`；注册新 goal；覆写 `canInteract()` |
| PrisonerEntity.java | `extends Monster` → `extends ControllableMonster`；注册新 goal |

---

## 4. ControllableMonster 设计

### 4.1 状态定义

```java
public static final int STATE_STAY   = 0;
public static final int STATE_WANDER = 1;
public static final int STATE_FOLLOW = 2;

private static final EntityDataAccessor<Integer> MOB_STATE =
    SynchedEntityData.defineId(ControllableMonster.class, EntityDataSerializers.INT);
```

- 默认状态：`STATE_WANDER` (1)
- 状态循环：`STAY → WANDER → FOLLOW → STAY`

### 4.2 核心方法

| 方法 | 说明 |
|------|------|
| `canInteract()` | 子类覆写，默认返回 `true`；安布拉返回 `!isBerserk()` |
| `getMobState()` / `setMobState(int)` | 状态 getter/setter |
| `getFollowTargetUUID()` / `setFollowTargetUUID(UUID)` | 跟随目标 UUID |
| `mobInteract(Player, InteractionHand)` | 右键交互入口 |
| `addAdditionalSaveData(CompoundTag)` | NBT 保存 |
| `readAdditionalSaveData(CompoundTag)` | NBT 加载 |

### 4.3 交互流程

```
玩家空手右键实体
  │
  ├─ 实体.canInteract() == false?
  │     └─ 返回 PASS，不响应
  │
  └─ canInteract() == true (仅服务端)
        │
        ├─ 记录玩家 UUID → followTargetUUID
        ├─ 状态循环
        ├─ 发送 ActionBar 提示
        │     STAY:   "§e◉ §f原地停留"
        │     WANDER: "§a↻ §f四处闲逛"
        │     FOLLOW: "§b→ §f跟随玩家"
        ├─ 播放交互音效
        └─ 返回 SUCCESS
```

### 4.4 NBT 持久化

```java
// 保存
compound.putInt("MobState", this.getMobState());
if (followTargetUUID != null) {
    compound.putUUID("FollowTarget", followTargetUUID);
}

// 加载
this.setMobState(compound.getInt("MobState"));
if (compound.hasUUID("FollowTarget")) {
    this.followTargetUUID = compound.getUUID("FollowTarget");
}
```

---

## 5. AI Goal 设计

### 5.1 StayGoal

| 属性 | 值 |
|------|---|
| 优先级 | Goal 1 (最高) |
| 激活条件 | `MOB_STATE == STATE_STAY` |
| 行为 | 停止导航，清零移动速度，仅允许转头 |

### 5.2 FollowPlayerGoal

| 属性 | 值 |
|------|---|
| 优先级 | Goal 2 |
| 激活条件 | `MOB_STATE == STATE_FOLLOW` 且目标玩家存在 |
| 跟随距离 | 3-5 格 |
| 移动速度 | 1.0 |
| 失联处理 | 玩家死亡/换维度 → 自动降为 WANDER |

### 5.3 ControlledWanderGoal

| 属性 | 值 |
|------|---|
| 优先级 | Goal 3 |
| 激活条件 | `MOB_STATE == STATE_WANDER` |
| 行为 | 等同于 RandomStrollGoal |

### 5.4 AnbulaEntity Goal 优先级

看花(WatchCornPoppyGoal)是正常状态下最高优先级。狂暴时 BerserkTargetGoal 设置攻击目标 → 看花自动跳过。

```
Goal 0: FloatGoal                    (水中浮起)
Goal 1: MeleeAttackGoal              (狂暴时攻击)
Goal 2: PickupWeaponGoal             (捡武器 → 触发狂暴)
Goal 3: WatchCornPoppyGoal           (看花 — 正常状态最高优先级)
Goal 4: StayGoal                     (新增 — 原地停留)
Goal 5: FollowPlayerGoal             (新增 — 跟随玩家)
Goal 6: ControlledWanderGoal         (新增 — 闲逛，替代原 RandomStrollGoal)
Goal 7: MoveTowardsTargetGoal        (向目标移动)
Goal 8: RandomLookAroundGoal         (四处张望)

targetSelector:
Goal 0: BerserkTargetGoal            (狂暴时搜索目标)
```

### 5.5 PrisonerEntity Goal 优先级

```
Goal 0: FloatGoal
Goal 1: StayGoal                     (新增 — 原地停留)
Goal 2: FollowPlayerGoal             (新增 — 跟随玩家)
Goal 3: ControlledWanderGoal         (新增 — 闲逛，替代原 RandomStrollGoal)
Goal 4: RandomLookAroundGoal
```

---

## 6. AnbulaEntity 特殊处理

- 覆写 `canInteract()`：狂暴状态 `isBerserk() == true` 时返回 `false`
- `WatchCornPoppyGoal` 优先级 (Goal 3) 高于状态控制 goal (Goal 4-6)，正常状态时看花优先
- 狂暴时 `BerserkTargetGoal` 设置攻击目标 → 压制看花 → `MeleeAttackGoal` 执行攻击
- 退出狂暴后恢复之前的状态

---

## 7. PrisonerEntity 特殊处理

- 移除 `HurtByTargetGoal`（STAY 状态下被攻击也不反击，保持状态纯净）
