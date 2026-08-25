# Phase 4 → Phase 5 过渡流程设计

## 概述

第四阶段抚摸紫狗完成后，不移除实体，而是：
1. 紫怪模型切换为 `modelpurplephasefour`（使用 `purplephasefive.geo.json`）
2. 玩家视线强制锁定看向紫怪头部
3. 显示第五阶段 UI（屏幕右侧）
4. 点击按钮后解除视线锁定，移除实体

## 当前状态分析

### 现有流程
- `PettingCompletePacket`（服务端处理）：发送 `OpenPurpleGuiPacket(phase=5)` → 移除紫怪和紫狗
- Phase 5 GUI 是独立界面，实体已不存在
- Phase 5 按钮点击时调用 `removePurpleEntity` + `onClose()`

### 关键技术点
| 组件 | 文件 | 当前能力 |
|------|------|---------|
| 同步数据 | `PurpleMonsterEntity.java` | 已有 `TEXTURE_VARIANT`、`PERFORM_MODE`、`PLAYING_SWAYHAND` |
| 模型切换 | `PurpleMonsterRenderer.java` | `getModelResource()` 固定返回 `strangerecord:purplemonster` |
| 纹理切换 | `PurpleMonsterRenderer.java` | `getTextureResource()` 已根据 `textureVariant` 切换 |
| 视线锁定 | `PurpleDogPettingClientHandler.java` | 已有 `setYRot`/`yHeadRot` 锁定模式 |
| 实体移除 | `PettingCompletePacket.java` | 第 49-53 行移除两个实体 |

## 修改设计

### 1. `PurpleMonsterEntity.java` — 新增模型变体同步数据

```java
// 新增（与 TEXTURE_VARIANT 并列）
private static final EntityDataAccessor<Integer> MODEL_VARIANT =
    SynchedEntityData.defineId(PurpleMonsterEntity.class, EntityDataSerializers.INT);

// 在 defineSynchedData 中注册
builder.define(MODEL_VARIANT, 0);

// Getter/Setter
public int getModelVariant() { return this.entityData.get(MODEL_VARIANT); }
public void setModelVariant(int variant) { this.entityData.set(MODEL_VARIANT, variant); }
```

### 2. `PurpleMonsterRenderer.java` — 动态模型切换

**PurpleMonsterRenderState** 新增字段：
```java
public int modelVariant = 0;
```

**extractRenderState** 新增一行：
```java
state.modelVariant = entity.getModelVariant();
```

**PurpleMonsterGeoModel.getModelResource** 动态化：
```java
@Override
public ResourceLocation getModelResource(GeoRenderState state) {
    if (state instanceof PurpleMonsterRenderState rs && rs.modelVariant == 1)
        return ResourceLocation.parse("strangerecord:purplephasefive");
    return ResourceLocation.parse("strangerecord:purplemonster");
}
```

> 注：`strangerecord:purplephasefive` 映射到 `assets/strangerecord/geckolib/models/purplephasefive.geo.json`

### 3. `PettingCompletePacket.java` — 改为设置模型变体

**handleData 方法修改**：不再移除实体，改为设置紫怪 modelVariant

```java
// 修改后
if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
    Entity purpleEntity = serverLevel.getEntity(message.purpleEntityId);
    if (purpleEntity instanceof PurpleMonsterEntity purpleMonster) {
        purpleMonster.setModelVariant(1);  // 切换模型
    }
    // 发送 Phase 5 GUI 打开指令
    PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(message.purpleEntityId, 5, message.dogEntityId));
    // 实体不再移除！
}
```

需要新增 import：
```java
import net.mcreator.strangerecord.entity.PurpleMonsterEntity;
```

### 4. `Phase5CameraHandler.java`（新建）— 客户端视线锁定

路径：`src/main/java/net/mcreator/strangerecord/event/Phase5CameraHandler.java`

```java
@EventBusSubscriber(value = Dist.CLIENT)
public class Phase5CameraHandler {
    private static final Map<UUID, Integer> LOCKED_PLAYERS = new ConcurrentHashMap<>();

    public static void startLock(UUID playerUUID, int purpleEntityId) {
        LOCKED_PLAYERS.put(playerUUID, purpleEntityId);
    }

    public static void stopLock(UUID playerUUID) {
        LOCKED_PLAYERS.remove(playerUUID);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        
        Integer entityId = LOCKED_PLAYERS.get(player.getUUID());
        if (entityId == null) return;
        
        Entity target = mc.level.getEntity(entityId);
        if (target == null) {
            LOCKED_PLAYERS.remove(player.getUUID());
            return;
        }

        // 锁定移动输入
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        player.xxa = 0;
        player.zza = 0;
        player.setSprinting(false);

        // 锁定视线到紫怪头部
        double dx = target.getX() - player.getX();
        double dy = target.getEyeY() - player.getEyeY();
        double dz = target.getZ() - player.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
        player.setYRot(yaw);
        player.yHeadRot = yaw;
        player.setXRot(pitch);
    }
}
```

### 5. `OpenPurpleGuiPacket.java` — 触发视线锁定

在 `handleData` 中，当 `phase == 5` 时启动视线锁定：

```java
public static void handleData(final OpenPurpleGuiPacket message, final IPayloadContext context) {
    if (context.flow() == PacketFlow.CLIENTBOUND) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            // Phase 5: 启动视线锁定（在打开 GUI 之前）
            if (message.phase == 5 && mc.player != null) {
                Phase5CameraHandler.startLock(mc.player.getUUID(), message.entityId);
            }
            mc.setScreen(new PurpleMonsterScreen(message.entityId, message.phase, message.purpleDogEntityId));
            if (mc.player != null) {
                PurpleTransitionClientHandler.unlockPlayer(mc.player.getUUID());
            }
        });
    }
}
```

新增 import：
```java
import net.mcreator.strangerecord.event.Phase5CameraHandler;
```

### 6. `PurpleMonsterScreen.java` — 按钮点击时解除锁定

**onPhase5Good** 和 **onPhase5Bad** 中，在移除实体之前先解锁视线：

```java
private void onPhase5Good() {
    // 解锁视线
    if (this.minecraft != null && this.minecraft.player != null) {
        Phase5CameraHandler.stopLock(this.minecraft.player.getUUID());
    }
    this.minecraft.player.displayClientMessage(Component.literal("§d[紫怪] 同化 +1"), false);
    this.removePurpleEntity(false);
    this.onClose();
}

private void onPhase5Bad() {
    if (this.minecraft != null && this.minecraft.player != null) {
        Phase5CameraHandler.stopLock(this.minecraft.player.getUUID());
    }
    this.minecraft.player.displayClientMessage(Component.literal("§7[紫怪] 祂消失了，仿佛从未存在过。"), false);
    this.removePurpleEntity(false);
    this.onClose();
}
```

修改后在 `onClose()` 中也添加 Phase 5 的解锁保护（防止 ESC 关闭时未解锁）：

```java
@Override
public void onClose() {
    if (phase == 5 && this.minecraft != null && this.minecraft.player != null) {
        Phase5CameraHandler.stopLock(this.minecraft.player.getUUID());
    }
    if (phase != 1) {
        // 阶段1不能自动删除紫怪（需要保留到阶段2）
        this.removePurpleEntity(false);
    }
    super.onClose();
}
```

新增 import：
```java
import net.mcreator.strangerecord.event.Phase5CameraHandler;
```

## Bug 预防清单

| # | 潜在问题 | 预防措施 |
|---|---------|---------|
| 1 | `onPhase5Good/Bad` 调用 `removePurpleEntity` 后又调用 `onClose`，后者对 phase!=1 再次调用 `removePurpleEntity`，导致重复发包 | 接受轻微冗余（服务端对已移除实体会做 no-op），不额外加 flag 增加复杂度 |
| 2 | `PurpleTransitionClientHandler.unlockPlayer` 和 `Phase5CameraHandler` 的视线锁定可能冲突 | `unlockPlayer` 恢复移动能力，`Phase5CameraHandler` 在每 tick 重新锁定。时序上 `unlockPlayer` 先执行没问题，因为下一 tick 的 camera handler 会重新锁定 |
| 3 | Phase 5 模型动画与原始动画文件不兼容（Bone 名称不同） | `purplephasefive.geo.json` 使用独立 bone 层级，`purplemonster.animation.json` 中的动画可能找不到目标 bone。GeckoLib 对此只打 warning 不崩溃。目前可接受，后续可创建独立动画文件 |
| 4 | 玩家在 Phase 5 期间按 ESC 关闭 GUI 导致视线未解锁 | `onClose()` 中添加 Phase 5 特判，主动调用 `stopLock` |
| 5 | 客户端实体不存在（已被其他方式移除）时 `Phase5CameraHandler` 锁定的 entityId 无效 | Handler 中检查 `target == null` 时自动移除锁定 |
| 6 | `OpenPurpleGuiPacket` 中的 `unlockPlayer()` 在 Phase 5 camera lock 之后调用，可能覆盖锁定效果 | 分析确认：`unlockPlayer` 只恢复 movement/input，不恢复视角。`Phase5CameraHandler` 每 tick 强制覆盖 yaw/pitch，时序安全 |

## 变更文件汇总

| 文件 | 操作 | 说明 |
|------|------|------|
| `PurpleMonsterEntity.java` | 修改 | 新增 `MODEL_VARIANT` 同步数据 + getter/setter |
| `PurpleMonsterRenderer.java` | 修改 | 新增 `modelVariant` 到 render state + 动态 `getModelResource` |
| `PettingCompletePacket.java` | 修改 | 移除实体删除代码，改为设置 `modelVariant=1` |
| `Phase5CameraHandler.java` | 新建 | 客户端 tick 处理器，锁定玩家视线到紫怪头部 |
| `OpenPurpleGuiPacket.java` | 修改 | Phase 5 时调用 `Phase5CameraHandler.startLock` |
| `PurpleMonsterScreen.java` | 修改 | Phase 5 按钮/关闭时调用 `Phase5CameraHandler.stopLock` |

## 验证步骤

1. 进入 Phase 4，选择"上前抚摸紫狗"
2. 抚摸动画完成后验证：
   - 紫怪模型变为 Phase 5 模型（`purplephasefive.geo.json`）
   - 紫狗和紫怪都不消失
   - 玩家视线被强制锁定看向紫怪头部
   - Phase 5 GUI 在屏幕右侧显示
3. 点击"好"按钮：视线解锁，实体移除，显示"同化 +1"
4. 点击"不好"按钮：视线解锁，实体移除，显示"祂消失了"
5. 按 ESC 关闭 GUI：视线解锁，实体移除
6. `/purplegui 5` 命令测试：不生成实体时 GUI 正常显示（不崩溃）
