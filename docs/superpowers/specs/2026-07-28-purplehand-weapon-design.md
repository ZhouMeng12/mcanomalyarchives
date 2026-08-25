# Purplehand 触手武器设计文档

> 日期: 2026-07-28 | 状态: 已确认

## 1. 概述

将 MCreator 已生成的 `Purplehand` 物品改造成一把双模式触手武器：
- **左键**：近战挥砍攻击
- **右键**：发射触手尖刺弹射物（有冷却，武器留在手上）

## 2. 当前状态

MCreator 已生成以下文件：

| 文件 | 说明 | 再生风险 |
|---|---|---|
| `item/PurplehandItem.java` | 当前 `extends Item`，ATTACK_DAMAGE+3，ATTACK_SPEED-2.8，附魔0，无耐久 | **会被覆盖** |
| `client/renderer/item/PurplehandItemRenderer.java` | SpecialModelRenderer + Geo 模型 + 动画 | 会被覆盖 |
| `init/StrangerecordModItems.java` | 已注册 `purplehand` | **会被覆盖** |
| `init/StrangerecordModEntities.java` | 实体注册，有 user code block | 会被覆盖 |
| `init/StrangerecordModEntityRenderers.java` | 实体渲染器注册 | 会被覆盖 |
| `init/StrangerecordModSounds.java` | 音效注册 | 会被覆盖 |
| `StrangerecordMod.java` | 主 Mod 类，有 user code block | **不受影响** |

## 3. 设计方案

### 3.1 武器属性

| 属性 | 值 | 说明 |
|---|---|---|
| 名称 | Purplehand | 已有 |
| 稀有度 | RARE | 已有 |
| 耐久 | 无 (damageCount=0) | 已有 |
| 附魔 | 禁用 (enchantability=0) | 已有 |
| 创造标签页 | StrangeRecord | 已有 |
| 近战伤害 | 12 (base damage) | 通过 SwordItem 的 Tier |
| 攻击速度 | 1.6 (Tier attack speed) | 通过 SwordItem 的 Tier |
| 远程伤害 | 25 | 弹射物命中时计算 |
| 冷却 | 40 tick (2秒) | player.getCooldowns() |
| 蓄力动画 | UseAnim.SPEAR | 12 tick 蓄力 |
| 音效 | 1 个远程发射音效 | 新增 |

### 3.2 文件变更清单

#### 新增文件

1. **`item/PurplehandItem.java`** — 修改现有文件
   - 改为 `extends SwordItem`（使用自定义 `Tier`）
   - 覆写 `use()` → 设置 `useDuration(12)`
   - 覆写 `releaseUsing()` → 生成 TentacleSpikeEntity + 播放音效 + 设置冷却
   - 覆写 `getUseAnimation()` → 返回 `UseAnim.SPEAR`
   - 覆写 `isEnchantable()` → `false`

2. **`entity/TentacleSpikeEntity.java`** — 新增
   - `extends ThrowableProjectile`
   - 无重力 (`getGravity() → 0`)
   - 飞行速度 3.0
   - 寿命 40 tick
   - `onHitEntity()`: 造成 25 点伤害 + 击退
   - `onHitBlock()`: 播放碎裂效果，discard()

3. **`client/renderer/TentacleSpikeRenderer.java`** — 新增（暂用简单渲染）
   - 用 item sprite 或简单几何体渲染弹射物

4. **`client/renderer/TentacleSpikeModel.java`** — 新增（暂用简单模型）
   - 简单尖刺几何体模型

#### 修改文件

5. **`init/StrangerecordModEntities.java`** — 在 user code block（第58行）中添加弹射物实体类型
   ```java
   public static final DeferredHolder<EntityType<?>, EntityType<TentacleSpikeEntity>> TENTACLE_SPIKE = ...;
   ```

6. **`StrangerecordMod.java`** — 在 mod constructor user code block（第54行）中
   - 注册实体 ENTITY_TYPES：`StrangerecordModEntities.REGISTRY.register(modEventBus)`（已有）
   - 直接注册音效：`SoundEvent sound = SoundEvent.createVariableRangeEvent(...); Registry.register(...)`
   - 订阅客户端事件注册渲染器

7. **`init/StrangerecordModEntityRenderers.java`** — MCreator 自动再生文件，每次构建后需重新添加渲染器注册行

> 注意：`StrangerecordModSounds.java` 和 `StrangerecordModEntityRenderers.java` 均无 user code block，音效直接在 `StrangerecordMod.java` 构造函数中注册；渲染器注册在每个构建后需手动添加

#### 不需要的文件（暂缓）

- ~~TentacleSpike 精细模型~~ → 后续迭代
- ~~Boss 掉落逻辑~~ → 后续迭代
- ~~音效 ogg 文件~~ → MCreator 中导入

### 3.3 核心数据流

```
右键按下 → PurplehandItem.use()
         → player.startUsingItem(hand) [useDuration=12 tick]
         → 播放 SPEAR 蓄力动画

蓄力完成 → PurplehandItem.releaseUsing()
         → 生成 TentacleSpikeEntity
         → entity.shoot(player.pitch, player.yaw, 0, 3.0, 0)
         → 播放发射音效
         → player.getCooldowns().addCooldown(this, 40)
         → 武器留在手上

飞行中   → TentacleSpikeEntity.tick()
         → 无重力直线飞行
         → 40 tick 后自动 despawn

命中实体 → TentacleSpikeEntity.onHitEntity()
         → target.hurt(damageSources, 25.0)
         → discard()
```

### 3.4 自定义 Tier

```java
public static final Tier PURPLEHAND_TIER = new Tier() {
    public int getUses() { return 0; }          // 无限耐久
    public float getSpeed() { return 1.6f; }    // 攻击速度
    public float getAttackDamageBonus() { return 12f; }  // 基础伤害加成
    public int getEnchantmentValue() { return 0; }
    public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    public TagKey<Block> getIncorrectBlocksForDrops() { return ...; }
};
```

### 3.5 MCreator 再生防护策略

`PurplehandItem.java` 已被锁定，不会被 MCreator 覆盖。其他仍需注意的文件：

| 文件 | 策略 |
|---|---|
| `StrangerecordModEntities.java` | 使用 user code block 添加 TENTACLE_SPIKE |
| `StrangerecordModEntityRenderers.java` | 构建后手动添加渲染器注册行 |
| `StrangerecordModSounds.java` | 无 user code block，音效直接在 StrangerecordMod.java 注册 |
| `StrangerecordMod.java` | 使用 user code block，不受影响 |

### 3.6 近战伤害说明

由于 `SwordItem` 使用 Tier 的 `getAttackDamageBonus()` 返回的是**额外伤害加成**（加在玩家空手伤害1.0之上），`getAttackDamageBonus() = 12f` 意味着总近战伤害 ≈ 12 + 默认属性 ≈ 约 13 点。
MCreator 当前设置 `damageVsEntity: 4.0` 转化为 `ATTACK_DAMAGE +3`。切换到 SwordItem 后，该值由 Tier 控制。

## 4. 测试方法

1. `/give @p strangerecord:purplehand` 获取武器
2. 左键攻击生物 → 验证近战伤害 ~13
3. 右键蓄力 → 观察 SPEAR 动画 → 弹射物发射
4. 弹射物命中生物 → 验证 25 点远程伤害
5. 右键后立即再按右键 → 验证冷却 2 秒期间无法再次使用
6. 附魔台 → 验证 Purplehand 不出现附魔选项
