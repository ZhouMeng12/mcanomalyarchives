# Anbula 冲锋技能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为安布拉新增独立冲锋技能：三阶段（锁定→冲刺→跳劈），GeckoLib 动画驱动，路径伤害+收尾跳劈。

**Architecture:** 新建自包含的 `AnbulaChargeGoal`（无状态泄漏到实体类），实体类仅添加动画控制器和两个 boolean 标记；动画 JSON 新增 `charge` 和 `leap` 两个动画定义。

**Tech Stack:** Minecraft NeoForge 1.21.8, GeckoLib, Java 21

---

### Task 1: 新增冲锋动画 JSON

**Files:**
- Modify: `src/main/resources/assets/strangerecord/geckolib/animations/anbula.animation.json`

- [ ] **Step 1: 在 animations 对象末尾新增 `animation.anbula.charge` 和 `animation.anbula.leap`**

读取 `anbula.animation.json`，在 `"animation.anbula.facedown"` 定义块的右花括号 `}` 后、外层 `}` 之前（即 `"facedown"` 结束后），添加两个新动画：

**`animation.anbula.charge`**（前倾冲刺）：

```json
"animation.anbula.charge": {
    "loop": false,
    "animation_length": 0.7,
    "bones": {
        "body": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.15": [35, 0, 0],
                "0.85": [35, 0, 0],
                "0.7": [0, 0, 0]
            }
        },
        "head": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.15": [5, 0, 0],
                "0.85": [5, 0, 0],
                "0.7": [0, 0, 0]
            }
        },
        "right_arm": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.1": [-60, 0, 0],
                "0.85": [-60, 0, 0],
                "0.7": [0, 0, 0]
            }
        },
        "left_arm": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.1": [-40, 0, 0],
                "0.85": [-40, 0, 0],
                "0.7": [0, 0, 0]
            }
        },
        "right_leg": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.0": [-45, 0, 0],
                "0.2": [45, 0, 0],
                "0.4": [-45, 0, 0],
                "0.6": [45, 0, 0],
                "0.7": [0, 0, 0]
            }
        },
        "left_leg": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.0": [45, 0, 0],
                "0.2": [-45, 0, 0],
                "0.4": [45, 0, 0],
                "0.6": [-45, 0, 0],
                "0.7": [0, 0, 0]
            }
        }
    }
}
```

**`animation.anbula.leap`**（跳劈收尾）：

```json
"animation.anbula.leap": {
    "loop": false,
    "animation_length": 0.5,
    "bones": {
        "body": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.15": [-10, 0, 0],
                "0.35": [20, 0, 0],
                "0.5": [0, 0, 0]
            }
        },
        "head": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.35": [10, 0, 0],
                "0.5": [0, 0, 0]
            }
        },
        "right_arm": {
            "rotation": {
                "0.0": [-60, 0, 0],
                "0.2": [-120, 0, -10],
                "0.4": [-120, 0, -10],
                "0.5": [0, 0, 0]
            }
        },
        "right_item": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.2": [-90, 0, 0],
                "0.4": [-90, 0, 0],
                "0.5": [0, 0, 0]
            }
        },
        "left_arm": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.15": [-30, 0, 10],
                "0.35": [15, 0, 0],
                "0.5": [0, 0, 0]
            }
        },
        "right_leg": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.1": [-20, 0, 0],
                "0.3": [20, 0, 0],
                "0.5": [0, 0, 0]
            }
        },
        "left_leg": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.1": [20, 0, 0],
                "0.3": [-20, 0, 0],
                "0.5": [0, 0, 0]
            }
        }
    }
}
```

- [ ] **Step 2: 确保 JSON 语法正确**

确认前一个动画 `animation.anbula.facedown` 的 `}` 后添加逗号，新动画插入正确位置。完整结构应为：

```
"animations": {
    "animation.anbula.idle": { ... },
    "animation.anbula.walk": { ... },
    "animation.anbula.attack": { ... },
    "animation.anbula.bow": { ... },
    "animation.anbula.facedown": { ... },
    "animation.anbula.charge": { ... },   // 新增
    "animation.anbula.leap": { ... }       // 新增（注意前面加逗号，最后一个不加逗号）
}
```

- [ ] **Step 3: 编译验证**

Run: `gradle compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

---

### Task 2: 修改 AnbulaEntity — 动画控制器与 Goal 注册

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/AnbulaEntity.java`

- [ ] **Step 1: 添加冲锋动画标记字段**

在 `// ===== GeckoLib 动画控制器 =====` 注释下方、`private boolean swinging = false;` 之后添加：

```java
// ===== 冲锋动画标记 =====
public boolean chargeSprinting = false;
public boolean chargeLeaping = false;
```

- [ ] **Step 2: 修改 `registerControllers()` 注册 chargeController**

在 `controllers.add(new AnimationController<>("faintController", 0, this::faintPredicate));` 之后添加：

```java
controllers.add(new AnimationController<>("chargeController", 0, this::chargePredicate));
```

- [ ] **Step 3: 添加 `chargePredicate` 方法**

在 `faintPredicate` 方法之后（`}` 后）添加：

```java
private PlayState chargePredicate(AnimationTest<AnbulaEntity> test) {
    if (isFaint()) return PlayState.STOP;
    if (this.chargeLeaping) {
        test.setAnimation(RawAnimation.begin().thenPlay("animation.anbula.leap"));
        return PlayState.CONTINUE;
    }
    if (this.chargeSprinting) {
        test.setAnimation(RawAnimation.begin().thenPlay("animation.anbula.charge"));
        return PlayState.CONTINUE;
    }
    return PlayState.STOP;
}
```

- [ ] **Step 4: 修改 `mainPredicate` 添加冲锋互斥**

在 `mainPredicate` 的方法体开头添加冲锋互斥检查，确保冲锋期间 idel/walk 动画停止：

```java
private PlayState mainPredicate(AnimationTest<AnbulaEntity> test) {
    if (isFaint()) return PlayState.STOP;
    if (chargeSprinting || chargeLeaping) return PlayState.STOP;
    if (test.isMoving()) {
        test.setAnimation(RawAnimation.begin().thenLoop("animation.anbula.walk"));
    } else {
        test.setAnimation(RawAnimation.begin().thenLoop("animation.anbula.idle"));
    }
    return PlayState.CONTINUE;
}
```

- [ ] **Step 5: 修改 `registerGoals()` 添加 AnbulaChargeGoal**

在 `registerGoals()` 中，在 `this.goalSelector.addGoal(0, new FloatGoal(this));` 之后、`this.goalSelector.addGoal(1, new AnbulaDodgeGoal(this));` 之前插入：

```java
this.goalSelector.addGoal(1, new AnbulaChargeGoal(this));
```

然后调整后续 Goal 优先级 +1：

```java
this.goalSelector.addGoal(0, new FloatGoal(this));
this.goalSelector.addGoal(1, new AnbulaChargeGoal(this));
this.goalSelector.addGoal(2, new AnbulaDodgeGoal(this));
this.goalSelector.addGoal(2, new AnbulaRangedAttackGoal(this, 1.0, 20, 15.0F));
this.meleeGoal = new AnbulaMeleeGoal(this, 1.2, true) { ... };
this.goalSelector.addGoal(3, this.meleeGoal);
this.goalSelector.addGoal(4, new SwitchWeaponGoal(this));
this.goalSelector.addGoal(5, new AnbulaBlockGoal(this));
this.goalSelector.addGoal(6, new PickupWeaponGoal(this));
this.goalSelector.addGoal(7, new WatchCornPoppyGoal(this));
this.goalSelector.addGoal(8, new StayGoal(this));
this.goalSelector.addGoal(9, new FollowPlayerGoal(this));
this.goalSelector.addGoal(10, new ControlledWanderGoal(this, 1));
this.goalSelector.addGoal(11, new MoveTowardsTargetGoal(this, 1.0, 10));
this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));
```

- [ ] **Step 6: 编译验证（此时因 AnbulaChargeGoal 不存在会失败，预期行为）**

Run: `gradle compileJava --no-daemon`
Expected: FAIL — `cannot find symbol: class AnbulaChargeGoal`

---

### Task 3: 创建 AnbulaChargeGoal.java

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/entity/ai/AnbulaChargeGoal.java`

- [ ] **Step 1: 创建文件，写入完整实现**

```java
package net.mcreator.strangerecord.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import net.mcreator.strangerecord.entity.AnbulaEntity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 安布拉冲锋技能：锁定 → 冲刺（路径伤害）→ 跳劈（范围伤害+击退）。
 * 冷却 240 ticks (12 秒)，仅在狂暴模式下对 5-15 格范围内的目标使用。
 */
public class AnbulaChargeGoal extends Goal {
    private final AnbulaEntity anbula;

    private static final int COOLDOWN_TICKS = 240;
    private static final double MIN_RANGE = 5.0;
    private static final double MAX_RANGE = 15.0;
    private static final double SPRINT_SPEED = 2.5;
    private static final float PATH_DAMAGE = 6.0F;
    private static final float LEAP_DAMAGE = 10.0F;
    private static final float KNOCKBACK = 0.5F;

    private static final int LOCK_ON_TICKS = 8;
    private static final int MAX_SPRINT_TICKS = 20;
    private static final int LEAP_TICKS = 10;

    private enum Phase { LOCK_ON, SPRINT, LEAP }
    private Phase phase;
    private int phaseTick;
    private int lastChargeTick = Integer.MIN_VALUE / 2;

    private Vec3 sprintDirection;
    private final Set<Integer> hitEntities = new HashSet<>();

    public AnbulaChargeGoal(AnbulaEntity anbula) {
        this.anbula = anbula;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!anbula.isBerserk() || anbula.isFaint()) return false;
        if (anbula.tickCount - lastChargeTick < COOLDOWN_TICKS) return false;

        LivingEntity target = anbula.getTarget();
        if (target == null || !target.isAlive()) return false;

        double dist = anbula.distanceTo(target);
        return dist >= MIN_RANGE && dist <= MAX_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        if (!anbula.isBerserk() || anbula.isFaint()) return false;
        if (anbula.getTarget() == null || !anbula.getTarget().isAlive()) return false;
        return phaseTick < getPhaseDuration();
    }

    @Override
    public void start() {
        phase = Phase.LOCK_ON;
        phaseTick = 0;
        anbula.chargeSprinting = false;
        anbula.chargeLeaping = false;
        hitEntities.clear();

        LivingEntity target = anbula.getTarget();
        if (target != null) {
            sprintDirection = target.position().subtract(anbula.position()).normalize();
        }
    }

    @Override
    public void stop() {
        anbula.chargeSprinting = false;
        anbula.chargeLeaping = false;
        lastChargeTick = anbula.tickCount;
        anbula.setDeltaMovement(0, anbula.getDeltaMovement().y, 0);
    }

    @Override
    public void tick() {
        phaseTick++;

        switch (phase) {
            case LOCK_ON:
                tickLockOn();
                break;
            case SPRINT:
                tickSprint();
                break;
            case LEAP:
                tickLeap();
                break;
        }
    }

    private void tickLockOn() {
        LivingEntity target = anbula.getTarget();
        if (target != null) {
            anbula.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        anbula.setDeltaMovement(0, anbula.getDeltaMovement().y, 0);

        if (phaseTick >= LOCK_ON_TICKS) {
            transitionTo(Phase.SPRINT);
        }
    }

    private void tickSprint() {
        anbula.chargeSprinting = true;
        anbula.chargeLeaping = false;

        // 高速移动
        Vec3 move = sprintDirection.scale(SPRINT_SPEED * 0.05);
        anbula.setDeltaMovement(move.x, anbula.getDeltaMovement().y, move.z);

        // 路径碰撞检测
        AABB sweepBox = anbula.getBoundingBox().inflate(1.5, 0.5, 1.5);
        for (Entity entity : anbula.level().getEntities(anbula, sweepBox)) {
            if (entity instanceof LivingEntity living && entity != anbula && living.isAlive()) {
                if (hitEntities.add(entity.getId())) {
                    living.hurt(anbula.damageSources().mobAttack(anbula), PATH_DAMAGE);
                    // 轻微击退
                    Vec3 knock = living.position().subtract(anbula.position()).normalize().scale(KNOCKBACK);
                    living.push(knock.x, 0.2, knock.z);
                }
            }
        }

        // 碰到目标或超时则进入跳劈
        LivingEntity target = anbula.getTarget();
        if (target != null && anbula.getBoundingBox().inflate(1.0).intersects(target.getBoundingBox())) {
            transitionTo(Phase.LEAP);
        } else if (phaseTick >= MAX_SPRINT_TICKS) {
            transitionTo(Phase.LEAP);
        }
    }

    private void tickLeap() {
        anbula.chargeSprinting = false;
        anbula.chargeLeaping = true;

        if (phaseTick == 1) {
            // 起跳
            anbula.setDeltaMovement(anbula.getDeltaMovement().x, 3.0, anbula.getDeltaMovement().z);
        }

        if (phaseTick >= LEAP_TICKS) {
            // 落地范围伤害
            if (anbula.level() instanceof ServerLevel serverLevel) {
                AABB aoeBox = anbula.getBoundingBox().inflate(2.0, 2.0, 2.0);
                for (Entity entity : anbula.level().getEntities(anbula, aoeBox)) {
                    if (entity instanceof LivingEntity living && entity != anbula && living.isAlive()) {
                        living.hurt(anbula.damageSources().mobAttack(anbula), LEAP_DAMAGE);
                        Vec3 knock = living.position().subtract(anbula.position()).normalize().scale(KNOCKBACK * 1.5);
                        living.push(knock.x, 0.3, knock.z);
                    }
                }
            }
            stop();
        }
    }

    private void transitionTo(Phase newPhase) {
        phase = newPhase;
        phaseTick = 0;
    }

    private int getPhaseDuration() {
        return switch (phase) {
            case LOCK_ON -> LOCK_ON_TICKS;
            case SPRINT -> MAX_SPRINT_TICKS;
            case LEAP -> LEAP_TICKS;
        };
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `gradle compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

---

### Task 4: 最终编译与验证

- [ ] **Step 1: 完整编译**

Run: `gradle compileJava --no-daemon`
Expected: BUILD SUCCESSFUL（仅 deprecation/unchecked 警告）

- [ ] **Step 2: 检查编译输出中无 AnbulaChargeGoal 相关错误**

确认没有语法错误或 import 缺失。确认所有文件引用一致。

---

### 手动游戏内验证步骤

1. 进入游戏 spawn 安布拉
2. 触发狂暴（用铁傀儡等重大威胁）
3. 观察 5-15 格距离时是否发动冲锋（锁定 → 冲刺动画 → 跳劈动画）
4. 确认路径碰撞造成伤害、跳劈造成范围伤害
5. 确认 12 秒冷却生效
6. 冲锋结束后恢复正常 AI
