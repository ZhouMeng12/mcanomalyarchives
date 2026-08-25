# Anbula Combat Enhancement Design

## Scope

Strengthen Anbula to dominate Iron Golems and contend with the Warden through skill-based combat (dodging, blocking, movement) rather than raw stats. Fix broken targeting AI during berserk.

---

## 1. Base Stats & Equipment

### Attribute Changes

| Attribute | Old | New | Reason |
|-----------|-----|-----|--------|
| MAX_HEALTH | 30 (15 hearts) | 100 (50 hearts) | Survive Iron Golem hits long enough for skill to matter |
| ARMOR | 0 | 0 (armor from gear) | Diamond armor provides 20 armor points = 80% physical reduction |
| ATTACK_DAMAGE | 5 | 7 | Diamond sword base; combo + Strength II = ~13/hit at combo 3+ |
| KNOCKBACK_RESISTANCE | 0 | 1.0 | Complete knockback immunity |
| MOVEMENT_SPEED | 0.3 | 0.3 | Unchanged; Speed IV during berserk brings to ~0.51 |

### Default Equipment (onInitialSpawn, no enchantments, dropChance=0)

| Slot | Item | Notes |
|------|------|-------|
| Mainhand | Diamond Sword | Guarantees immediate berserk capability |
| Offhand | Shield | Core defense tool |
| Head | Diamond Helmet | +3 armor |
| Chest | Diamond Chestplate | +8 armor |
| Legs | Diamond Leggings | +6 armor |
| Feet | Diamond Boots | +3 armor |

### Berserk Effects

| Effect | Level | Notes |
|--------|-------|-------|
| SPEED | IV (amp=3) | Existing, keep |
| RESISTANCE | II (amp=1) | Existing, keep |
| STRENGTH | II (amp=1) | **New**: +6 damage, brings diamond sword to 13 per hit |

---

## 2. Threat Perception & Berserk Rework

### Problem
Old system: `hasWeapon() → berserk → targetSelector active`. Chicken-and-egg — no weapon means no berserk means no targeting means can't find weapons.

### New System
Anbula has default diamond gear on spawn, so `hasWeapon()` is always true. The trigger for berserk is now **threat presence**:

| Trigger | Behavior |
|---------|----------|
| Hurt by any entity | Instant berserk (overrides cooldown) |
| Major hostile within 12 blocks (Iron Golem, Warden, Wither, Elder Guardian, Ravager) | Enter berserk |
| Player detected | Do NOT enter berserk |
| Minor mobs (zombie, skeleton, etc.) | Do NOT enter berserk |

Cooldown: 60 seconds after exiting berserk. Overridden when hurt.

### Implementation
- New `evaluateThreats()` method in `AnbulaEntity.tick()`
- Runs every 20 ticks (1 second) when not berserk, not faint, and cooldown expired
- Scans for major hostile entities within 12 blocks using AABB

### Files changed
- `AnbulaEntity.java`: Add `evaluateThreats()`, modify tick loop

---

## 3. Targeting Fix During Berserk

### Problem
`NearestAttackableTargetGoal` uses `mustSee=true` — requires direct line of sight. In caves, buildings, or behind any block, Anbula "can't see" the target and stands idle.

### Fix

| Parameter | Old | New |
|-----------|-----|-----|
| mustSee | true | **false** |
| randomInterval | 10 (default) | **5** |

Anbula senses threats without needing direct line of sight, similar to the Warden's perception.

### Files changed
- `AnbulaEntity.java` line 369: Change `NearestAttackableTargetGoal` constructor parameters

---

## 4. Dodge System Rework

### Current Problems
- `DODGE_DURATION = 2` ticks — dodge starts and stops instantly, does nothing
- Uses `navigation.moveTo()` — pathfinding latency defeats dodge purpose
- Only detects projectiles and bow users — no melee dodge
- `setBaseValue()` on global attribute — breaks if multiple Anbula instances exist

### 4a. Melee Dodge (New)

Probabilistic preemptive dodge — impossible to react to `swing()` in the same tick because of entity tick ordering. Instead, simulate reading the opponent:

**Trigger**: Anbula within 2.5 blocks of target for ≥ 5 ticks
**Chance**: 35% per tick to trigger dodge
**Cooldown**: 15 ticks (0.75s) after dodge

**Execution**:
```
Dodge direction: perpendicular to target's facing, random left/right
Dodge method: setPos() teleport 2 blocks laterally
Collision check: if destination is solid → dodge backward (away from target) instead
Visual: brief translucency + particle effect during dodge
```

### 4b. Projectile/Sonic Boom Dodge (Enhanced)

**Trigger**: Projectile within 8 blocks AND approaching (dot product check + distance < 4)
**Cooldown**: 12 ticks after dodge

**Execution**:
```
Dodge direction: perpendicular to projectile velocity, random left/right
Dodge method: setDeltaMovement() for momentum-based side-step
Duration: 8 ticks (was 2)
Visual: brief translucency + particle effect
```

**Detection**:
```
// Check if projectile is approaching (not flying past)
Vec3 toAnbula = anbula.position().subtract(proj.position()).normalize();
Vec3 projDir = proj.getDeltaMovement().normalize();
double dot = projDir.dot(toAnbula);
if (dot > 0.3) return true; // flying toward us
```

### 4c. Dodge Cooldown Separation

Melee and ranged dodges have **separate** cooldown timers so Anbula can dodge a melee swing and immediately dodge a projectile (or vice versa).

### Files changed
- `AnbulaDodgeGoal.java`: Complete rewrite

---

## 5. Blocking & Movement

### AnbulaBlockGoal
- Shield raise delay: 1 tick → **0 ticks** (instant)
- Existing behavior preserved

### AnbulaMeleeGoal
- After each melee hit: retreat 1.5 blocks, then re-approach (hit-and-run)
- Circle radius: 2.5 → 1.5 (tighter strafe)
- Circle angle step: 45° → 60° per 20 ticks (more aggressive circling)

### Files changed
- `AnbulaBlockGoal.java`: Reduce shield delay
- `AnbulaMeleeGoal.java`: Add retreat, adjust circle params

---

## 6. Weapon Rendering Fix

### Problem
Custom `AnbulaRenderState` breaks item-in-hand rendering in GeckoLib 5.x.

### Fix
Override `extractRenderState()` in `AnbulaRenderer` to call `super.extractRenderState()` and ensure item data flows to render state. Fallback: revert to standard `GeoEntityRenderer<AnbulaEntity>` if custom render state is unnecessary.

### Files changed
- `AnbulaRenderer.java`: Add extractRenderState override

---

## 7. Verification

### Build
- `./gradlew build` — zero compilation errors

### Gameplay Tests
| Test | Expected |
|------|----------|
| Spawn Anbula, spawn Iron Golem nearby | Anbula detects Golem, enters berserk, engages |
| Anbula vs Iron Golem | Anbula wins convincingly, dodging punches and circling |
| Anbula vs 3 Iron Golems | Anbula survives through dodging + blocking, wins slowly |
| Player approaches berserk Anbula | Anbula does NOT target peaceful player |
| Player attacks Anbula | Anbula targets and fights back |
| Anbula vs Warden | Anbula dodges sonic booms, survives extended fight |
| Anbula vs Skeleton (bow) | Anbula dodges arrows via side-step |
| Anbula in cave with zombie behind wall | Anbula senses and targets zombie (mustSee=false) |
| Anbula weapon rendering | Diamond sword visible in Anbula's hand |
| Anbula below 20% HP | Enters faint state properly, stops fighting |

---

## Files Summary

| File | Change Type | Description |
|------|-------------|-------------|
| `AnbulaEntity.java` | Major | Stats, equipment, threat perception, targeting, berserk effects |
| `AnbulaDodgeGoal.java` | Rewrite | Melee + ranged dodge with teleport, collision, particles |
| `AnbulaMeleeGoal.java` | Modify | Hit-and-run, tighter circle |
| `AnbulaBlockGoal.java` | Modify | Instant shield raise |
| `AnbulaRenderer.java` | Fix | Weapon rendering via extractRenderState |
