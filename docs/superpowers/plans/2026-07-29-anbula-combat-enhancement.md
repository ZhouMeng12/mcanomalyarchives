# Anbula Combat Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strengthen Anbula into a skilled fighter that defeats Iron Golems through dodging/blocking/movement, not raw stats. Fix targeting AI during berserk.

**Architecture:** Incremental enhancement to AnbulaEntity and its AI goals. Threat perception drives berserk; probabilistic dodge replaces nav-based dodge; teleport-based melee evasion; tighter circle strafe + hit-and-run.

**Tech Stack:** Minecraft NeoForge 1.21.8, GeckoLib 5.x, Java 21

---

### Task 1: Base Stats, Default Equipment & Berserk Effects

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/AnbulaEntity.java`

- [ ] **Step 1: Update createAttributes() for HP and knockback resistance**

Replace the `createAttributes()` method at line 605-613:

```java
public static AttributeSupplier.Builder createAttributes() {
    AttributeSupplier.Builder builder = Mob.createMobAttributes();
    builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3);
    builder = builder.add(Attributes.MAX_HEALTH, 100);
    builder = builder.add(Attributes.ARMOR, 0);
    builder = builder.add(Attributes.ATTACK_DAMAGE, 7);
    builder = builder.add(Attributes.FOLLOW_RANGE, 32);
    builder = builder.add(Attributes.STEP_HEIGHT, 0.6);
    builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    return builder;
}
```

Key changes: MAX_HEALTH 30→100, ATTACK_DAMAGE 5→7, new KNOCKBACK_RESISTANCE 1.0.

- [ ] **Step 2: Add default equipment on spawn**

Add this method to `AnbulaEntity` (after the constructor at line 282):

```java
@Override
public void finalizeSpawn(
        net.minecraft.world.level.ServerLevelAccessor level,
        net.minecraft.world.DifficultyInstance difficulty,
        net.minecraft.world.entity.MobSpawnType spawnType,
        @org.jetbrains.annotations.Nullable net.minecraft.world.entity.SpawnGroupData spawnGroupData) {
    super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    // 只在自然生成时装备（非命令/summon等）
    if (spawnType == net.minecraft.world.entity.MobSpawnType.NATURAL
            || spawnType == net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION
            || spawnType == net.minecraft.world.entity.MobSpawnType.STRUCTURE
            || spawnType == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG) {
        equipDefaultGear();
    }
}

private void equipDefaultGear() {
    this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
    this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.SHIELD));
    this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
    this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
    this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS));
    this.setItemSlot(EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
    // 不掉落装备，防止玩家刷装
    this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    this.setDropChance(EquipmentSlot.HEAD, 0.0F);
    this.setDropChance(EquipmentSlot.CHEST, 0.0F);
    this.setDropChance(EquipmentSlot.LEGS, 0.0F);
    this.setDropChance(EquipmentSlot.FEET, 0.0F);
}
```

Also add the sword to the weapon inventory so PickupWeaponGoal knows about it:

At the end of `equipDefaultGear()`, add:

```java
    // 将初始武器加入武器背包
    ItemStack sword = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
    if (weaponInventory.isEmpty()) {
        weaponInventory.add(sword.copy());
        activeWeaponSlot = 0;
    }
```

- [ ] **Step 3: Add STRENGTH effect to berserk**

In `applyBerserkEffects()` (line 507), add Strength II after Resistance:

```java
private void applyBerserkEffects() {
    if (!this.hasEffect(MobEffects.SPEED)) {
        this.addEffect(new MobEffectInstance(MobEffects.SPEED, -1, 3, false, true));
    }
    if (!this.hasEffect(MobEffects.RESISTANCE)) {
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 1, false, true));
    }
    if (!this.hasEffect(MobEffects.STRENGTH)) {
        this.addEffect(new MobEffectInstance(MobEffects.STRENGTH, -1, 1, false, true));
    }
}
```

Also update `removeBerserkEffects()` to clear Strength:

```java
private void removeBerserkEffects() {
    this.removeEffect(MobEffects.SPEED);
    this.removeEffect(MobEffects.RESISTANCE);
    this.removeEffect(MobEffects.STRENGTH);
}
```

---

### Task 2: Threat Perception System

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/AnbulaEntity.java`

- [ ] **Step 1: Add threatTicker field and imports**

Add the field after `private boolean soundPlayed = false;` at line 76:

```java
private int threatTicker = 0;
```

Add required imports at the top (if not already present):

```java
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Ravager;
```

- [ ] **Step 2: Add evaluateThreats() method**

Add to `AnbulaEntity` (before `enterFaint()` at line 446):

```java
private void evaluateThreats() {
    if (isBerserk() || isFaint() || berserkCooldown > 0) return;

    threatTicker++;
    if (threatTicker < 20) return; // 每秒检测一次
    threatTicker = 0;

    AABB scanBox = this.getBoundingBox().inflate(12.0);
    List<LivingEntity> threats = this.level().getEntitiesOfClass(LivingEntity.class, scanBox,
            e -> e != this && e.isAlive() && isMajorThreat(e));

    if (!threats.isEmpty()) {
        enterBerserk();
        // 设置最近的威胁为目标
        LivingEntity closest = threats.stream()
                .min((a, b) -> Double.compare(this.distanceToSqr(a), this.distanceToSqr(b)))
                .orElse(null);
        if (closest != null) {
            this.setTarget(closest);
        }
    }
}

private static boolean isMajorThreat(LivingEntity entity) {
    return entity instanceof IronGolem
            || entity instanceof Warden
            || entity instanceof WitherBoss
            || entity instanceof ElderGuardian
            || entity instanceof Ravager;
}
```

- [ ] **Step 3: Modify tick() to call evaluateThreats and fix berserk entry**

Replace the berserk block in `tick()` (lines 433-443):

Old:
```java
if (berserkCooldown > 0) {
    berserkCooldown--;
    return;
}

if (isBerserk) {
    berserkTicks++;
    applyBerserkEffects();
} else if (hasWeapon()) {
    enterBerserk();
}
```

New:
```java
if (berserkCooldown > 0) {
    berserkCooldown--;
}

if (isBerserk) {
    berserkTicks++;
    applyBerserkEffects();
} else {
    evaluateThreats();
}
```

Note: `return` is removed from cooldown block so threat evaluation can still run.

---

### Task 3: Targeting Fix

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/AnbulaEntity.java`

- [ ] **Step 1: Change NearestAttackableTargetGoal parameters**

Replace line 369:

Old:
```java
this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true) {
```

New:
```java
this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 5, false, false, null) {
```

Parameters: `randomInterval=5` (scan every 5 ticks), `mustSee=false` (no line-of-sight required), `mustReach=false`, `targetingConditions=null` (use default).

---

### Task 4: Dodge Goal Rewrite

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/ai/AnbulaDodgeGoal.java`

- [ ] **Step 1: Rewrite AnbulaDodgeGoal with melee + ranged dodge**

Replace the entire file content:

```java
package net.mcreator.strangerecord.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import net.mcreator.strangerecord.entity.AnbulaEntity;

import java.util.EnumSet;
import java.util.List;

/**
 * 安布拉闪避：近战概率预判侧闪 + 投射物/音波预判侧闪。
 * 两种闪避有独立冷却，互不干扰。
 */
public class AnbulaDodgeGoal extends Goal {
    private final AnbulaEntity anbula;

    // 投射物闪避
    private static final double PROJ_DETECT_RANGE = 8.0;
    private static final double PROJ_DODGE_THRESHOLD = 4.0;
    private static final int PROJ_DODGE_COOLDOWN = 12;
    private static final int PROJ_DODGE_DURATION = 8;
    private int projCooldown = 0;

    // 近战闪避
    private static final double MELEE_DETECT_RANGE = 2.5;
    private static final int MELEE_DODGE_COOLDOWN = 15;
    private static final double MELEE_DODGE_CHANCE = 0.35;
    private int meleeCooldown = 0;
    private int meleeCloseTicks = 0;

    // 当前闪避状态
    private int dodgeTicks = 0;
    private boolean isDodging = false;

    public AnbulaDodgeGoal(AnbulaEntity anbula) {
        this.anbula = anbula;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!anbula.isBerserk() || anbula.isFaint()) return false;
        LivingEntity target = anbula.getTarget();
        if (target == null || !target.isAlive()) return false;

        // 先冷却递减
        updateCooldowns();

        // 投射物闪避检测
        if (projCooldown <= 0 && detectIncomingProjectile()) {
            return true;
        }

        // 近战闪避检测
        if (meleeCooldown <= 0 && detectMeleeThreat(target)) {
            return true;
        }

        return false;
    }

    private void updateCooldowns() {
        if (projCooldown > 0) projCooldown--;
        if (meleeCooldown > 0) meleeCooldown--;
    }

    // ===== 投射物检测 =====

    private boolean detectIncomingProjectile() {
        AABB box = anbula.getBoundingBox().inflate(PROJ_DETECT_RANGE);
        List<Projectile> projectiles = anbula.level().getEntitiesOfClass(Projectile.class, box,
                p -> p.getOwner() != anbula && p.isAlive());

        for (Projectile proj : projectiles) {
            double dist = anbula.distanceToSqr(proj);
            if (dist > PROJ_DODGE_THRESHOLD * PROJ_DODGE_THRESHOLD) continue;

            // 检查投射物是否在飞向自己（不是飞过）
            Vec3 projDir = proj.getDeltaMovement().normalize();
            Vec3 toAnbula = anbula.position().subtract(proj.position()).normalize();
            double dot = projDir.dot(toAnbula);
            if (dot <= 0.3) continue; // 不是朝自己飞来

            projCooldown = PROJ_DODGE_COOLDOWN;
            return true;
        }
        return false;
    }

    // ===== 近战检测 =====

    private boolean detectMeleeThreat(LivingEntity target) {
        double dist = anbula.distanceToSqr(target);
        if (dist > MELEE_DETECT_RANGE * MELEE_DETECT_RANGE) {
            meleeCloseTicks = 0;
            return false;
        }

        meleeCloseTicks++;
        if (meleeCloseTicks < 5) return false; // 需贴身0.25秒

        // 每tick 35%概率触发
        if (anbula.getRandom().nextDouble() < MELEE_DODGE_CHANCE) {
            meleeCloseTicks = 0;
            meleeCooldown = MELEE_DODGE_COOLDOWN;
            return true;
        }
        return false;
    }

    // ===== 闪避执行 =====

    @Override
    public boolean canContinueToUse() {
        return isDodging && dodgeTicks < PROJ_DODGE_DURATION;
    }

    @Override
    public void start() {
        isDodging = true;
        dodgeTicks = 0;
        executeDodge();
    }

    private void executeDodge() {
        LivingEntity target = anbula.getTarget();
        if (target == null) return;

        Vec3 dodgeDir;

        if (meleeCooldown > 0) {
            // 近战闪避：垂直于目标朝向
            float targetYaw = target.getYRot();
            double rad = Math.toRadians(targetYaw);
            Vec3 targetFacing = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            dodgeDir = new Vec3(-targetFacing.z, 0, targetFacing.x);
        } else {
            // 投射物闪避：随机侧向
            dodgeDir = anbula.getRandom().nextBoolean()
                    ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        }

        // 随机方向
        if (anbula.getRandom().nextBoolean()) {
            dodgeDir = dodgeDir.scale(-1);
        }

        Vec3 dodgePos = anbula.position().add(dodgeDir.scale(2.0));

        // 碰撞检查：如果目标位置是固体，改为向后闪（远离目标）
        BlockPos checkPos = BlockPos.containing(dodgePos.x, anbula.getY(), dodgePos.z);
        if (anbula.level().getBlockState(checkPos).isSolid()) {
            Vec3 awayFromTarget = anbula.position().subtract(target.position()).normalize();
            dodgePos = anbula.position().add(awayFromTarget.scale(2.0));
        }

        anbula.setPos(dodgePos.x, anbula.getY(), dodgePos.z);

        // 粒子效果（服务端生成粒子）
        if (anbula.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    anbula.getX(), anbula.getY() + 0.5, anbula.getZ(),
                    8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @Override
    public void tick() {
        dodgeTicks++;
        if (dodgeTicks >= PROJ_DODGE_DURATION) {
            isDodging = false;
        }
    }

    @Override
    public void stop() {
        isDodging = false;
        dodgeTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
```

---

### Task 5: Melee Goal Enhancement

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/ai/AnbulaMeleeGoal.java`

- [ ] **Step 1: Add hit-and-run retreat after attack**

After the attack block in `tick()` (after line 61 `this.mob.doHurtTarget(target);`), add retreat logic. Replace the entire `tick()` method:

```java
@Override
public void tick() {
    LivingEntity target = this.mob.getTarget();
    if (target != null) {
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // 攻击后退
        if (retreatTicks > 0) {
            retreatTicks--;
            Vec3 away = this.mob.position().subtract(target.position()).normalize();
            double retreatSpeed = 0.15;
            this.mob.setPos(
                this.mob.getX() + away.x * retreatSpeed,
                this.mob.getY(),
                this.mob.getZ() + away.z * retreatSpeed
            );
            return; // 后退期间不攻击也不绕圈，专注位移
        }

        // 自定义攻击冷却
        if (tickUntilAttack > 0) {
            tickUntilAttack--;
        }

        // 执行攻击
        double dist = this.mob.distanceToSqr(target);
        double attackRange = this.mob.getBbWidth() * 2.0 * this.mob.getBbWidth() * 2.0 + target.getBbWidth();
        if (tickUntilAttack <= 0
                && dist < attackRange
                && this.mob.getSensing().hasLineOfSight(target)) {
            tickUntilAttack = getBaseInterval();
            this.mob.swing(InteractionHand.MAIN_HAND);
            if (this.mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                this.mob.doHurtTarget(serverLevel, target);
            }
            // 攻击后后退
            retreatTicks = 8;
        }
    }

    // combo 超时归零
    if (target != null) {
        comboTimer++;
        if (comboTimer > COMBO_TIMEOUT) {
            comboCount = 0;
        }

        // 绕圈走位（更紧、更激进）
        double dist = this.mob.distanceToSqr(target);
        if (dist <= CIRCLE_RADIUS * CIRCLE_RADIUS && retreatTicks <= 0) {
            circleTimer++;
            if (circleTimer >= CIRCLE_STEP_INTERVAL) {
                circleTimer = 0;
                circleAngle = (circleAngle + 60) % 360;
                double rad = Math.toRadians(circleAngle);
                double targetX = target.getX() + Math.cos(rad) * CIRCLE_RADIUS;
                double targetZ = target.getZ() + Math.sin(rad) * CIRCLE_RADIUS;
                this.mob.getNavigation().moveTo(targetX, target.getY(), targetZ, this.mob.getSpeed());
            }
        }
    }
}
```

- [ ] **Step 2: Add retreatTicks field and change constants**

Add the field after `private int tickUntilAttack = 0;` at line 23:

```java
private int retreatTicks = 0;
```

Change the constant at line 15:

Old:
```java
private static final double CIRCLE_RADIUS = 2.5;
```

New:
```java
private static final double CIRCLE_RADIUS = 1.5;
```

---

### Task 6: Block Goal Instant Shield

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/ai/AnbulaBlockGoal.java`

- [ ] **Step 1: Change shield raise to be instant (no delay)**

In the `tick()` method, the shield raise is already gated by `isBlocking` flag. The shield uses `anbula.startUsingItem(InteractionHand.OFF_HAND)` which is instant — no delay. However, the current code in `tick()` only raises shield on the tick AFTER `canUse()` returns true. The real "delay" is that `tick()` might not be called immediately if the goal wasn't selected.

The current implementation already has no meaningful delay — `startUsingItem()` is called in the first `tick()` after `canUse()` returns true. This is effectively instant.

No code change needed for AnbulaBlockGoal — it's already instant.

---

### Task 7: Weapon Rendering Fix

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/client/renderer/AnbulaRenderer.java`

- [ ] **Step 1: Add extractRenderState override**

Add after `createRenderState()` at line 69:

```java
@Override
public void extractRenderState(AnbulaEntity entity, AnbulaRenderState state, float partialTick) {
    super.extractRenderState(entity, state, partialTick);
}
```

If `extractRenderState` doesn't exist in the parent class (API variant), use the fallback approach: simplify the renderer to not use a custom render state. Replace the entire renderer with:

```java
package net.mcreator.strangerecord.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

import net.mcreator.strangerecord.entity.AnbulaEntity;

public class AnbulaRenderer extends GeoEntityRenderer<AnbulaEntity> {

    private static final class AnbulaGeoModel extends GeoModel<AnbulaEntity> {
        @Override
        public ResourceLocation getModelResource(AnbulaEntity entity) {
            return ResourceLocation.parse("strangerecord:anbula");
        }

        @Override
        public ResourceLocation getTextureResource(AnbulaEntity entity) {
            return ResourceLocation.parse("strangerecord:textures/entities/111.png");
        }

        @Override
        public ResourceLocation getAnimationResource(AnbulaEntity entity) {
            return ResourceLocation.parse("strangerecord:anbula");
        }
    }

    public AnbulaRenderer(EntityRendererProvider.Context context) {
        super(context, new AnbulaGeoModel());
        this.shadowRadius = 0.5f;
    }
}
```

This removes the custom `AnbulaRenderState`, reverting to the standard `GeoEntityRenderer<AnbulaEntity>` which handles item rendering natively.

---

### Task 8: Build Verification

- [ ] **Step 1: Clean build**

```powershell
cd d:\Desktop\MCreaterWorkspace
.\gradlew clean compileJava
```

Expected: BUILD SUCCESSFUL, 0 errors.

- [ ] **Step 2: Run client for gameplay test**

```powershell
.\gradlew runClient
```

Manual verification checklist:
- Spawn Anbula + Iron Golem → Anbula detects and engages
- Anbula dodges Iron Golem punches (side-teleport + particle)
- Anbula blocks with shield when in melee range
- Anbula weapon renders in hand (diamond sword visible)
- Anbula circles tightly and retreats after hitting
