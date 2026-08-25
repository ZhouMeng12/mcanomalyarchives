# Anbula 冲锋技能设计

## 概述

为安布拉新增一个独立的冲锋技能：高速冲刺撞击路径上的敌人，最后跳劈收尾。通过 GeckoLib 动画系统展示冲刺和跳劈两段动画。

## 触发条件

- 安布拉处于狂暴模式，非晕厥
- 存在存活目标
- 冷却已结束（240 ticks = 12 秒）
- 目标距离 5-15 格

## 技能流程（三阶段）

| 阶段 | 时长 | 行为 | 动画 |
|------|------|------|------|
| LOCK_ON | 8 ticks (0.4s) | 面向目标，短暂锁定，不可移动 | 无独立动画（chargeController 激活时停止 mainController） |
| SPRINT | 最长 20 ticks (1.0s) | 高速直冲目标（速度 2.5x），每 tick 路径碰撞造成伤害；碰到目标或到达后进入下一阶段 | `animation.anbula.charge`（前倾冲刺） |
| LEAP | 10 ticks (0.5s) | 小跳 + 空中挥砍，落地造成范围伤害和击退 | `animation.anbula.leap`（跳劈） |

**冷却**：结束后 240 ticks (12 秒)。

**伤害**：
- 路径碰撞：6 点，每个敌人独立计算（同个敌人一帧只判定一次）
- 跳劈：10 点 + 0.5 格击退

## 文件变更

### 1. 新建：`AnbulaChargeGoal.java`

路径：`src/main/java/net/mcreator/strangerecord/entity/ai/AnbulaChargeGoal.java`

- 继承 `Goal`，设置 `Flag.MOVE | Flag.LOOK`
- 三阶段枚举（LOCK_ON, SPRINT, LEAP）驱动状态机
- 通过 `AnbulaEntity.chargeSprinting` / `chargeLeaping` 布尔字段控制动画切换
- 路径碰撞：每 tick 用 AABB 检测前方 2 格范围，对碰到的新实体造成伤害
- 冲刺方向：锁定目标位置，沿直线移动

### 2. 修改：`AnbulaEntity.java`

- 新增字段：
  ```java
  public boolean chargeSprinting = false;
  public boolean chargeLeaping = false;
  ```
- `registerControllers()` 新增动画控制器：
  ```java
  controllers.add(new AnimationController<>("chargeController", 0, this::chargePredicate));
  ```
  其中 `chargePredicate` 逻辑：
  - `chargeLeaping` → 播放 `animation.anbula.leap`
  - `chargeSprinting` → 播放 `animation.anbula.charge`
  - 否则 → STOP

- `registerGoals()` 调整：
  ```java
  this.goalSelector.addGoal(0, new FloatGoal(this));
  this.goalSelector.addGoal(1, new AnbulaChargeGoal(this));   // 新增：冲锋
  this.goalSelector.addGoal(2, new AnbulaDodgeGoal(this));     // 原 1 → 2
  this.goalSelector.addGoal(2, new AnbulaRangedAttackGoal(...)); // 原 1 → 2
  this.meleeGoal = new AnbulaMeleeGoal(this, 1.2, true) { ... };
  this.goalSelector.addGoal(3, this.meleeGoal);               // 原 2 → 3
  // 后续 goal 依次 +1
  ```

### 3. 修改：`anbula.animation.json`

新增两个动画定义：

**`animation.anbula.charge`**（0.7s，loop=false）：
- body：前倾 35°（X 轴旋转），压低身形
- head：微仰 5° 看前方
- right_arm：后摆 -60°
- left_arm：后摆 -40°
- right_leg / left_leg：大步交替，快速摆动

**`animation.anbula.leap`**（0.5s，loop=false）：
- body：从 -10° 过渡到 20°（后仰蓄力 → 前倾挥砍）
- right_arm：从 -60° 挥到 -120°（过顶下劈）
- right_item：跟随 right_arm
- left_arm：配合平衡摆动
- right_leg / left_leg：跳起 + 落地

## 假设与决策

- 冷却 12 秒，后续可根据反馈调整
- 路径伤害不重复判定同一实体（用 Set 去重）
- 冲刺方向在 SPRINT 阶段开始时锁定，中途不追踪目标移动
- 跳劈阶段附加 0.5 格垂直跳跃，落地无摔伤
- 冲锋期间不受近战绕圈 AI 干扰（Goal 优先级更高）

## 验证方式

1. 编译通过，进入游戏 spawn 安布拉
2. 触发狂暴（用铁傀儡等重大威胁），观察是否在合适距离发动冲锋
3. 检查动画是否正确播放（冲刺 → 跳劈）
4. 检查路径伤害和跳劈伤害数值
5. 检查 12 秒冷却是否生效
6. 冲锋结束后恢复正常 AI 行为
