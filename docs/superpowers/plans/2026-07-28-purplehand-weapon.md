# Purplehand 触手武器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 MCreator 生成的 Purplehand Item 改造成双模式触手武器（左键近战 + 右键发射弹射物带冷却）

**Architecture:** PurplehandItem extends SwordItem（自定义 Tier）→ 右键 use/releaseUsing 生成 TentacleSpikeEntity（extends ThrowableProjectile）→ 无重力直线飞行 → 命中造成25伤害

**Tech Stack:** NeoForge 1.21.x, Minecraft 1.21.x

---

### Task 1: 创建 TentacleSpikeEntity 弹射物实体

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/entity/TentacleSpikeEntity.java`

- [ ] **Step 1: 编写 TentacleSpikeEntity**

```java
package net.mcreator.strangerecord.entity;

import net.mcreator.strangerecord.init.StrangerecordModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class TentacleSpikeEntity extends ThrowableProjectile {

    public static final float DAMAGE = 25.0F;
    public static final float SPEED = 3.0F;
    public static final int LIFETIME = 40;

    public TentacleSpikeEntity(EntityType<? extends TentacleSpikeEntity> type, Level level) {
        super(type, level);
    }

    public TentacleSpikeEntity(Level level, LivingEntity shooter) {
        super(StrangerecordModEntities.TENTACLE_SPIKE.get(), shooter, level);
    }

    @Override
    protected double getGravity() {
        return 0.0;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        DamageSource source = this.damageSources().thrown(this, this.getOwner());
        result.getEntity().hurt(source, DAMAGE);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SHULKER_BULLET_HIT, SoundSource.NEUTRAL, 0.5F, 1.0F);
        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > LIFETIME) {
            this.discard();
        }
    }
}
```

---

### Task 2: 注册 TentacleSpikeEntity 实体类型

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/init/StrangerecordModEntities.java:58-59`

- [ ] **Step 1: 在 user code block 中添加实体类型注册**

在 `// Start of user code block custom entities` 和 `// End of user code block custom entities` 之间添加：

```java
public static final DeferredHolder<EntityType<?>, EntityType<TentacleSpikeEntity>> TENTACLE_SPIKE = register("tentacle_spike",
        EntityType.Builder.<TentacleSpikeEntity>of(TentacleSpikeEntity::new, MobCategory.MISC)
                .setShouldReceiveVelocityUpdates(true)
                .setTrackingRange(64)
                .setUpdateInterval(1)
                .sized(0.5f, 0.5f));
```

并在文件顶部 import 区域添加：
```java
import net.mcreator.strangerecord.entity.TentacleSpikeEntity;
```

- [ ] **Step 2: 验证 StrangerecordModEntities.REGISTRY 已在 StrangerecordMod.java 中注册**

确认 `StrangerecordMod.java:62` 已有 `StrangerecordModEntities.REGISTRY.register(modEventBus)` — 无需额外操作。

---

### Task 3: 创建弹射物渲染器（复用 purplechushou 模型）

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/client/renderer/TentacleSpikeRenderer.java`

- [ ] **Step 1: 编写渲染器，复用已注册的 Modelpurplechushou**

```java
package net.mcreator.strangerecord.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import net.mcreator.strangerecord.entity.TentacleSpikeEntity;
import net.mcreator.strangerecord.client.model.Modelpurplechushou;

public class TentacleSpikeRenderer extends MobRenderer<TentacleSpikeEntity, LivingEntityRenderState, Modelpurplechushou> {

    private static final ResourceLocation TEXTURE = ResourceLocation.parse("strangerecord:textures/item/qq20260728-194647.png");

    public TentacleSpikeRenderer(EntityRendererProvider.Context context) {
        super(context, new Modelpurplechushou(context.bakeLayer(Modelpurplechushou.LAYER_LOCATION)), 0.25f);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public ResourceLocation getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
```

> `Modelpurplechushou.LAYER_LOCATION` 已在 `StrangerecordModModels.java:25` 注册，无需额外配置。

---

### Task 4: 注册渲染器

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/init/StrangerecordModEntityRenderers.java`

- [ ] **Step 1: 在 registerEntityRenderers 方法中添加渲染器注册**

在 `event.registerEntityRenderer(...PURPLE_DOG.get()...);` 之后添加：

```java
event.registerEntityRenderer(StrangerecordModEntities.TENTACLE_SPIKE.get(), TentacleSpikeRenderer::new);
```

并在文件顶部 import 区域添加：
```java
import net.mcreator.strangerecord.client.renderer.TentacleSpikeRenderer;
```

> 注意：此文件会被 MCreator 重新生成，构建后需重新添加此行。

---

### Task 5: 注册发射音效 + 静态字段

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/StrangerecordMod.java`

- [ ] **Step 1: 添加静态字段声明**

在 `StrangerecordMod.java` 的 "mod methods" user code block（第89行 `// Start of user code block mod methods` 之后）添加：

```java
public static SoundEvent PURPLEHAND_SHOOT;
```

- [ ] **Step 2: 在 mod constructor user code block 中注册音效**

在第54行 `// Start of user code block mod constructor` 和第55行 `// End of user code block mod constructor` 之间添加：

```java
PURPLEHAND_SHOOT = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath(MODID, "purplehand_shoot"));
Registry.register(BuiltInRegistries.SOUND_EVENT,
        ResourceLocation.fromNamespaceAndPath(MODID, "purplehand_shoot"),
        PURPLEHAND_SHOOT);
```

- [ ] **Step 3: 添加必要的 import**

在 `StrangerecordMod.java` 顶部 import 区域添加（如果尚不存在）：

```java
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
```

- [ ] **Step 4: 音效文件**

在 `src/main/resources/assets/strangerecord/sounds/` 下放置 `purplehand_shoot.ogg`，并在 `sounds.json` 中声明：

```json
{
  "purplehand_shoot": {
    "sounds": [
      {
        "name": "strangerecord:purplehand_shoot",
        "stream": false
      }
    ]
  }
}
```

---

### Task 6: 改造 PurplehandItem 为 SwordItem + 远程能力

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/item/PurplehandItem.java`

- [ ] **Step 1: 重写 PurplehandItem.java**

```java
package net.mcreator.strangerecord.item;

import net.mcreator.strangerecord.StrangerecordMod;
import net.mcreator.strangerecord.entity.TentacleSpikeEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.crafting.Ingredient;

public class PurplehandItem extends SwordItem {

    private static final Tier PURPLEHAND_TIER = new Tier() {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 1.6F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 12.0F;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_WOODEN_TOOL;
        }
    };

    public PurplehandItem(Properties properties) {
        super(PURPLEHAND_TIER, properties.rarity(Rarity.RARE));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 12;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (!(entity instanceof Player player)) return false;

        int useTime = this.getUseDuration(stack, entity) - timeCharged;
        if (useTime < 0) useTime = 0;
        if (useTime > 2) return false;

        if (!level.isClientSide) {
            Vec3 look = player.getLookAngle();
            TentacleSpikeEntity spike = new TentacleSpikeEntity(level, player);
            spike.setPos(player.getX() + look.x * 1.5,
                    player.getEyeY() + look.y * 1.5 - 0.1,
                    player.getZ() + look.z * 1.5);
            spike.shoot(look.x, look.y, look.z, TentacleSpikeEntity.SPEED, 0);
            level.addFreshEntity(spike);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    StrangerecordMod.PURPLEHAND_SHOOT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

            player.getCooldowns().addCooldown(this, 40);
        }

        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
```

**关键点：**
- `extends SwordItem` → 近战伤害由 Tier 的 `getAttackDamageBonus()` 控制 (=12，总伤害≈13)
- `use()` → `player.startUsingItem(hand)` 开始蓄力
- `getUseDuration()` → 返回 12 tick
- `getUseAnimation()` → `UseAnim.SPEAR` 显示矛蓄力动画
- `releaseUsing()` → 蓄力完成时生成 TentacleSpikeEntity 并发射
- 弹射物生成位置：玩家前方 1.5 格，眼睛高度，避免弹射物生成时卡在玩家碰撞箱内
- `player.getCooldowns().addCooldown(this, 40)` → 2秒冷却
- `isEnchantable()` → 返回 false 禁用附魔

---

### Task 7: 构建与测试

- [ ] **Step 1: 构建项目**

```powershell
./gradlew build
```

- [ ] **Step 2: 修复编译错误（如有）**

如果缺少 import，根据 IDE 提示补充。

- [ ] **Step 3: 启动游戏并测试**

```
/give @p strangerecord:purplehand
```

1. 左键攻击生物 → 验证近战伤害 ~13（两下杀死僵尸≈20血）
2. 右键蓄力 → 观察 SPEAR 动画（手臂后拉约0.6秒）→ 弹射物发射
3. 弹射物命中生物 → 验证 25 点远程伤害（一下杀死多数普通生物）
4. 右键后立即再按右键 → 验证冷却2秒期间无法再次使用（物品栏有冷却动画）
5. 附魔台 → 验证 Purplehand 不出现附魔选项

---

### Task 8: 提交代码

- [ ] **Step 1: 提交所有变更**

```powershell
git add src/main/java/net/mcreator/strangerecord/entity/TentacleSpikeEntity.java
git add src/main/java/net/mcreator/strangerecord/client/renderer/TentacleSpikeRenderer.java
git add src/main/java/net/mcreator/strangerecord/init/StrangerecordModEntities.java
git add src/main/java/net/mcreator/strangerecord/init/StrangerecordModEntityRenderers.java
git add src/main/java/net/mcreator/strangerecord/StrangerecordMod.java
git add src/main/java/net/mcreator/strangerecord/item/PurplehandItem.java
git add src/main/resources/assets/strangerecord/sounds/purplehand_shoot.ogg
git add src/main/resources/assets/strangerecord/sounds.json
git add docs/superpowers/specs/2026-07-28-purplehand-weapon-design.md
git add docs/superpowers/plans/2026-07-28-purplehand-weapon.md
git commit -m "feat: add Purplehand dual-mode tentacle weapon with melee and ranged spike projectile"
```
