# 可控实体状态切换 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现空手右键切换安布拉和死刑犯的 AI 状态（原地停留/四处闲逛/跟随玩家），带 ActionBar 提示和 NBT 持久化。

**Architecture:** 新建抽象基类 ControllableMonster 掌管状态机和交互逻辑，两个实体改为继承它。三个 AI Goal（StayGoal/FollowPlayerGoal/ControlledWanderGoal）通过 EntityDataAccessor 读取状态决定是否激活。

**Tech Stack:** Java 21, NeoForge 21.8.31, Minecraft 1.21.8, Mojang mappings

---

### Task 1: 创建 ControllableMonster 抽象基类

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/entity/ControllableMonster.java`

- [ ] **Step 1: 写入 ControllableMonster.java**

```java
package net.mcreator.strangerecord.entity;

import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InteractionResult;
import net.minecraft.world.entity.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class ControllableMonster extends Monster {

	public static final int STATE_STAY = 0;
	public static final int STATE_WANDER = 1;
	public static final int STATE_FOLLOW = 2;

	private static final EntityDataAccessor<Integer> MOB_STATE =
			SynchedEntityData.defineId(ControllableMonster.class, EntityDataSerializers.INT);

	@Nullable
	private UUID followTargetUUID;

	protected ControllableMonster(EntityType<? extends Monster> type, Level world) {
		super(type, world);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MOB_STATE, STATE_WANDER);
	}

	public int getMobState() {
		return this.entityData.get(MOB_STATE);
	}

	public void setMobState(int state) {
		this.entityData.set(MOB_STATE, state);
	}

	@Nullable
	public UUID getFollowTargetUUID() {
		return followTargetUUID;
	}

	public void setFollowTargetUUID(@Nullable UUID uuid) {
		this.followTargetUUID = uuid;
	}

	protected boolean canInteract() {
		return true;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.level().isClientSide() && hand == InteractionHand.MAIN_HAND) {
			if (!canInteract()) {
				return InteractionResult.PASS;
			}

			this.setFollowTargetUUID(player.getUUID());

			int current = getMobState();
			int next = (current + 1) % 3;
			setMobState(next);

			String msg = switch (next) {
				case STATE_STAY -> "§e◉ §f原地停留";
				case STATE_WANDER -> "§a↻ §f四处闲逛";
				case STATE_FOLLOW -> "§b→ §f跟随玩家";
				default -> "§f未知状态";
			};
			player.displayClientMessage(Component.literal(msg), true);
			this.level().playSound(null, this.blockPosition(), SoundEvents.VILLAGER_YES, this.getSoundSource(), 1.0f, 1.0f);

			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		compound.putInt("MobState", this.getMobState());
		if (followTargetUUID != null) {
			compound.putUUID("FollowTarget", followTargetUUID);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		this.setMobState(compound.getInt("MobState"));
		if (compound.hasUUID("FollowTarget")) {
			this.followTargetUUID = compound.getUUID("FollowTarget");
		} else {
			this.followTargetUUID = null;
		}
	}
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew compileJava --no-daemon
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/entity/ControllableMonster.java
git commit -m "feat: add ControllableMonster base class with state machine and interact logic"
```

---

### Task 2: 创建 StayGoal

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/entity/ai/StayGoal.java`

- [ ] **Step 1: 写入 StayGoal.java**

```java
package net.mcreator.strangerecord.entity.ai;

import net.mcreator.strangerecord.entity.ControllableMonster;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class StayGoal extends Goal {
	private final ControllableMonster mob;

	public StayGoal(ControllableMonster mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return mob.getMobState() == ControllableMonster.STATE_STAY;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		mob.getNavigation().stop();
		mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
	}
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew compileJava --no-daemon
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/entity/ai/StayGoal.java
git commit -m "feat: add StayGoal — entity stays in place when STAY state"
```

---

### Task 3: 创建 ControlledWanderGoal

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/entity/ai/ControlledWanderGoal.java`

- [ ] **Step 1: 写入 ControlledWanderGoal.java**

```java
package net.mcreator.strangerecord.entity.ai;

import net.mcreator.strangerecord.entity.ControllableMonster;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;

import java.util.EnumSet;

public class ControlledWanderGoal extends RandomStrollGoal {
	private final ControllableMonster mob;

	public ControlledWanderGoal(ControllableMonster mob, double speedModifier) {
		super(mob, speedModifier);
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (mob.getMobState() != ControllableMonster.STATE_WANDER) {
			return false;
		}
		return super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		if (mob.getMobState() != ControllableMonster.STATE_WANDER) {
			return false;
		}
		return super.canContinueToUse();
	}
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew compileJava --no-daemon
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/entity/ai/ControlledWanderGoal.java
git commit -m "feat: add ControlledWanderGoal — wander only when WANDER state"
```

---

### Task 4: 创建 FollowPlayerGoal

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/entity/ai/FollowPlayerGoal.java`

- [ ] **Step 1: 写入 FollowPlayerGoal.java**

```java
package net.mcreator.strangerecord.entity.ai;

import net.mcreator.strangerecord.entity.ControllableMonster;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

public class FollowPlayerGoal extends Goal {
	private final ControllableMonster mob;
	private Player targetPlayer;
	private static final double MIN_DISTANCE = 3.0;
	private static final double MAX_DISTANCE = 5.0;
	private static final double TELEPORT_DISTANCE = 32.0;

	public FollowPlayerGoal(ControllableMonster mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (mob.getMobState() != ControllableMonster.STATE_FOLLOW) {
			return false;
		}
		UUID targetUUID = mob.getFollowTargetUUID();
		if (targetUUID == null) {
			return false;
		}
		targetPlayer = mob.level().getPlayerByUUID(targetUUID);
		if (targetPlayer == null || !targetPlayer.isAlive() || targetPlayer.isSpectator()) {
			demoteToWander();
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (mob.getMobState() != ControllableMonster.STATE_FOLLOW) {
			return false;
		}
		if (targetPlayer == null || !targetPlayer.isAlive() || targetPlayer.isSpectator()) {
			demoteToWander();
			return false;
		}
		return true;
	}

	@Override
	public void tick() {
		if (targetPlayer == null) return;
		double dist = mob.distanceToSqr(targetPlayer);
		if (dist > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
			mob.getNavigation().moveTo(targetPlayer, 1.2);
		} else if (dist > MAX_DISTANCE * MAX_DISTANCE) {
			mob.getNavigation().moveTo(targetPlayer, 1.0);
		} else if (dist < MIN_DISTANCE * MIN_DISTANCE) {
			mob.getNavigation().stop();
		}
		mob.getLookControl().setLookAt(targetPlayer, 10.0f, (float) mob.getMaxHeadXRot());
	}

	private void demoteToWander() {
		mob.setMobState(ControllableMonster.STATE_WANDER);
		targetPlayer = null;
	}

	@Override
	public void stop() {
		targetPlayer = null;
		mob.getNavigation().stop();
	}
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew compileJava --no-daemon
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/entity/ai/FollowPlayerGoal.java
git commit -m "feat: add FollowPlayerGoal — follow the player who last right-clicked"
```

---

### Task 5: 修改 AnbulaEntity — 继承 ControllableMonster 并注册新 goal

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/AnbulaEntity.java`

- [ ] **Step 1: 修改继承和 registerGoals**

将 AnbulaEntity 的继承从 `Monster` 改为 `ControllableMonster`，并在 registerGoals 中插入新 goal。

修改行 43: `public class AnbulaEntity extends Monster {` → `public class AnbulaEntity extends ControllableMonster {`

修改 registerGoals 方法（行 63-79），将原有的 goal 优先级重新排列：

```java
@Override
protected void registerGoals() {
    this.goalSelector.addGoal(0, new FloatGoal(this));
    this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true) {
        @Override
        protected boolean canPerformAttack(LivingEntity entity) {
            return this.isTimeToAttack() && this.mob.distanceToSqr(entity) < (this.mob.getBbWidth() * this.mob.getBbWidth() + entity.getBbWidth()) && this.mob.getSensing().hasLineOfSight(entity);
        }
    });
    this.goalSelector.addGoal(2, new PickupWeaponGoal(this));
    this.goalSelector.addGoal(3, new WatchCornPoppyGoal(this));
    this.goalSelector.addGoal(4, new StayGoal(this));
    this.goalSelector.addGoal(5, new FollowPlayerGoal(this));
    this.goalSelector.addGoal(6, new ControlledWanderGoal(this, 1));
    this.goalSelector.addGoal(7, new MoveTowardsTargetGoal(this, 1.0, 10));
    this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

    this.targetSelector.addGoal(0, new BerserkTargetGoal(this));
}
```

移除原有的 import `RandomStrollGoal`，添加新的 import：

```java
import net.mcreator.strangerecord.entity.ai.StayGoal;
import net.mcreator.strangerecord.entity.ai.ControlledWanderGoal;
import net.mcreator.strangerecord.entity.ai.FollowPlayerGoal;
```

移除不再需要的 `import net.minecraft.world.entity.ai.goal.RandomStrollGoal;`

- [ ] **Step 2: 添加安布拉 canInteract 覆写**

在 `isBerserk()` 方法之后（行 55 之后）添加：

```java
@Override
protected boolean canInteract() {
    return !isBerserk;
}
```

- [ ] **Step 3: 编译验证**

```bash
.\gradlew compileJava --no-daemon
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/entity/AnbulaEntity.java
git commit -m "feat: AnbulaEntity extends ControllableMonster, register state goals"
```

---

### Task 6: 修改 PrisonerEntity — 继承 ControllableMonster 并注册新 goal

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/PrisonerEntity.java`

- [ ] **Step 1: 修改继承和 registerGoals**

将 PrisonerEntity 的继承从 `Monster` 改为 `ControllableMonster`：

修改行 27: `public class PrisonerEntity extends Monster {` → `public class PrisonerEntity extends ControllableMonster {`

修改 registerGoals 方法（行 34-41）：

```java
@Override
protected void registerGoals() {
    super.registerGoals();
    this.goalSelector.addGoal(0, new FloatGoal(this));
    this.goalSelector.addGoal(1, new StayGoal(this));
    this.goalSelector.addGoal(2, new FollowPlayerGoal(this));
    this.goalSelector.addGoal(3, new ControlledWanderGoal(this, 1));
    this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
}
```

移除原有的 import，添加新的 import：

```java
import net.mcreator.strangerecord.entity.ai.StayGoal;
import net.mcreator.strangerecord.entity.ai.ControlledWanderGoal;
import net.mcreator.strangerecord.entity.ai.FollowPlayerGoal;
```

移除不再需要的导入：
- `import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;`
- `import net.minecraft.world.entity.ai.goal.RandomStrollGoal;`

- [ ] **Step 2: 编译验证**

```bash
.\gradlew compileJava --no-daemon
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/entity/PrisonerEntity.java
git commit -m "feat: PrisonerEntity extends ControllableMonster, register state goals"
```

---

### Task 7: 最终编译验证

**Files:**
- 无新建/修改文件

- [ ] **Step 1: 完整编译**

```bash
.\gradlew build --no-daemon
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 验证输出**

确认:
- 无编译错误
- 无新增 warning（除已有的 deprecated API 提示）
