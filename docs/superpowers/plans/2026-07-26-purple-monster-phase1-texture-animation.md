# 紫怪阶段1 纹理切换 & 动画 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 紫怪常态/阶段1使用 purpleboy 纹理，阶段1开始时播 swayhand 动画，阶段1紫色按钮选择后不删怪、播 model.hand 动画后切换 purple 纹理再进入阶段2。

**Architecture:** 客户端驱动动画 + 纹理切换。PurpleMonsterEntity 新增 TEXTURE_VARIANT synced data；Renderer 根据 variant 返回不同纹理；PurpleMonsterScreen 点击紫色按钮后关闭 GUI，由 PurpleMonsterPhase1ClientHandler 客户端 tick 状态机播放动画、切换纹理、发包开阶段2 GUI。

**Tech Stack:** Java, NeoForge, GeckoLib, Minecraft 1.21

---

### Task 1: PurpleMonsterEntity — 添加 TEXTURE_VARIANT synced data 和动画触发方法

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/entity/PurpleMonsterEntity.java`

- [ ] **Step 1: 添加 TEXTURE_VARIANT synced data**

在 `PERFORM_MODE` 定义下方添加：

```java
private static final EntityDataAccessor<Integer> TEXTURE_VARIANT =
    SynchedEntityData.defineId(PurpleMonsterEntity.class, EntityDataSerializers.INT);
```

- [ ] **Step 2: 注册 synced data 到 defineSynchedData**

在 `builder.define(PERFORM_MODE, false);` 后添加：

```java
builder.define(TEXTURE_VARIANT, 0);
```

- [ ] **Step 3: 添加 getter/setter 方法**

在 `isPerformMode()` 方法后添加：

```java
public int getTextureVariant() {
    return this.entityData.get(TEXTURE_VARIANT);
}

public void setTextureVariant(int variant) {
    this.entityData.set(TEXTURE_VARIANT, variant);
}
```

- [ ] **Step 4: 添加动画触发方法**

在类末尾 `getAnimatableInstanceCache()` 之前添加：

```java
public void triggerSwayhand() {
    if (!this.level().isClientSide()) return;
    triggerAnim("controller", "animation.purplemonster.swayhand");
}

public void triggerModelHand() {
    if (!this.level().isClientSide()) return;
    triggerAnim("controller", "animation.purplemonster.hand");
}

private void triggerAnim(String controllerName, String animName) {
    getAnimatableInstanceCache().getManagerForId(0).ifPresent(manager -> {
        manager.getAnimationControllers().get(controllerName).forceAnimationReset();
        manager.getAnimationControllers().get(controllerName).setAnimation(
            RawAnimation.begin().thenPlay(animName));
    });
}
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/entity/PurpleMonsterEntity.java
git commit -m "feat: add TEXTURE_VARIANT synced data and animation trigger methods to PurpleMonsterEntity"
```

---

### Task 2: PurpleMonsterRenderer — 动态纹理

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/client/renderer/PurpleMonsterRenderer.java`

- [ ] **Step 1: 修改 getTextureResource 动态返回纹理**

替换 `getTextureResource` 方法：

```java
@Override
public ResourceLocation getTextureResource(GeoRenderState state) {
    // 通过 state 无法直接获取 entity，需要重载带 entity 参数的方法
    return ResourceLocation.parse("strangerecord:textures/entities/purpleboy.png");
}

@Override
public ResourceLocation getTextureResource(PurpleMonsterEntity entity) {
    if (entity.getTextureVariant() == 1) {
        return ResourceLocation.parse("strangerecord:textures/entities/purple.png");
    }
    return ResourceLocation.parse("strangerecord:textures/entities/purpleboy.png");
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/client/renderer/PurpleMonsterRenderer.java
git commit -m "feat: dynamic texture based on TEXTURE_VARIANT in PurpleMonsterRenderer"
```

---

### Task 3: 合并 swayhand 动画到 GeckoLib 文件

**Files:**
- Modify: `src/main/resources/assets/strangerecord/geckolib/animations/purplemonster.animation.json`

- [ ] **Step 1: 添加 swayhand 动画**

在 `animation.purplemonster.walk` 结束 `}` 后插入（与 walk 平级）：

```json
"animation.purplemonster.swayhand": {
    "loop": "hold_on_last_frame",
    "animation_length": 0.5,
    "bones": {
        "right_arm": {
            "rotation": {
                "0.0": [0, 0, 0],
                "0.5": [-72.5, 0, 0]
            }
        }
    }
},
```

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/assets/strangerecord/geckolib/animations/purplemonster.animation.json
git commit -m "feat: add swayhand animation (hold_on_last_frame, right arm swing)"
```

---

### Task 4: 新建 TransitionToPhase2Packet.java

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/network/TransitionToPhase2Packet.java`

- [ ] **Step 1: 创建 packet 类**

```java
package net.mcreator.strangerecord.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.mcreator.strangerecord.StrangerecordMod;
import net.minecraft.server.level.ServerPlayer;

@EventBusSubscriber
public record TransitionToPhase2Packet(int purpleEntityId) implements CustomPacketPayload {
	public static final Type<TransitionToPhase2Packet> TYPE =
		new Type<>(ResourceLocation.fromNamespaceAndPath(StrangerecordMod.MODID, "transition_to_phase2"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TransitionToPhase2Packet> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT,
			TransitionToPhase2Packet::purpleEntityId,
			TransitionToPhase2Packet::new
		);

	@Override
	public Type<TransitionToPhase2Packet> type() {
		return TYPE;
	}

	public static void handleData(final TransitionToPhase2Packet message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				if (context.player() instanceof ServerPlayer player) {
					PacketDistributor.sendToPlayer(player,
						new OpenPurpleGuiPacket(message.purpleEntityId, 2, 0));
				}
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		StrangerecordMod.addNetworkMessage(TYPE, STREAM_CODEC, TransitionToPhase2Packet::handleData);
	}
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/network/TransitionToPhase2Packet.java
git commit -m "feat: add TransitionToPhase2Packet for direct Phase 2 GUI open (no entity removal/respawn)"
```

---

### Task 5: 新建 PurpleMonsterPhase1ClientHandler.java

**Files:**
- Create: `src/main/java/net/mcreator/strangerecord/event/PurpleMonsterPhase1ClientHandler.java`

- [ ] **Step 1: 创建客户端状态机**

```java
package net.mcreator.strangerecord.event;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

import net.mcreator.strangerecord.entity.PurpleMonsterEntity;
import net.mcreator.strangerecord.network.TransitionToPhase2Packet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(value = Dist.CLIENT)
public class PurpleMonsterPhase1ClientHandler {
	private static final Map<UUID, Phase1Transition> TRANSITIONS = new ConcurrentHashMap<>();
	private static final int ANIM_TICKS = 10; // model.hand 动画 0.5s = 10 ticks

	public static void startTransition(UUID playerUUID, int purpleEntityId) {
		TRANSITIONS.put(playerUUID, new Phase1Transition(purpleEntityId));
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;

		Phase1Transition transition = TRANSITIONS.get(player.getUUID());
		if (transition == null) return;

		if (mc.level == null) {
			TRANSITIONS.remove(player.getUUID());
			return;
		}

		Entity entity = mc.level.getEntity(transition.purpleEntityId);
		if (entity instanceof PurpleMonsterEntity monster) {
			transition.tick++;

			switch (transition.phase) {
				case PLAY_ANIM:
					// 第一帧触发动画
					if (transition.tick == 1) {
						monster.triggerModelHand();
					}
					if (transition.tick >= ANIM_TICKS) {
						transition.phase = Phase1Transition.Phase.SWITCH_TEXTURE;
						transition.tick = 0;
					}
					break;

				case SWITCH_TEXTURE:
					monster.setTextureVariant(1); // purple
					transition.phase = Phase1Transition.Phase.OPEN_PHASE2;
					break;

				case OPEN_PHASE2:
					player.connection.send(new ServerboundCustomPayloadPacket(
						new TransitionToPhase2Packet(transition.purpleEntityId)));
					TRANSITIONS.remove(player.getUUID());
					break;
			}
		} else {
			// 实体已经不存在，直接清理
			TRANSITIONS.remove(player.getUUID());
		}
	}

	private static class Phase1Transition {
		final int purpleEntityId;
		int phase = Phase.PLAY_ANIM;
		int tick = 0;

		Phase1Transition(int purpleEntityId) {
			this.purpleEntityId = purpleEntityId;
		}

		static class Phase {
			static final int PLAY_ANIM = 0;
			static final int SWITCH_TEXTURE = 1;
			static final int OPEN_PHASE2 = 2;
		}
	}
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/event/PurpleMonsterPhase1ClientHandler.java
git commit -m "feat: add PurpleMonsterPhase1ClientHandler - client-side state machine for Phase 1 -> Phase 2 transition"
```

---

### Task 6: PurpleMonsterScreen — 修改 onPhase1PurpleClicked 和 onClose

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/client/screen/PurpleMonsterScreen.java`

- [ ] **Step 1: 添加 import**

在文件顶部 import 区域添加：

```java
import net.mcreator.strangerecord.event.PurpleMonsterPhase1ClientHandler;
```

- [ ] **Step 2: 修改 onPhase1PurpleClicked**

替换 `onPhase1PurpleClicked()` 方法：

```java
private void onPhase1PurpleClicked() {
    this.minecraft.player.displayClientMessage(Component.literal("§d[紫怪] 同化 +1"), false);
    // 关闭 GUI，启动客户端状态机：播 model.hand → 换纹理 → 开阶段2
    if (this.minecraft != null && this.minecraft.player != null) {
        PurpleMonsterPhase1ClientHandler.startTransition(this.minecraft.player.getUUID(), this.purpleEntityId);
    }
    this.minecraft.setScreen(null);
}
```

- [ ] **Step 3: 修改 onClose — 阶段1不删怪**

将 `onClose()` 改为：

```java
@Override
public void onClose() {
    if (phase == 1) {
        // 阶段1不能自动删除紫怪（可能需要保留到阶段2）
        // 只有灰色按钮被点击时才应该删除
    } else {
        this.removePurpleEntity(false);
    }
    super.onClose();
}
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/client/screen/PurpleMonsterScreen.java
git commit -m "feat: Phase 1 purple button triggers client-side animation/texture transition, no entity removal"
```

---

### Task 7: PurpleGuiCommand — 阶段1生成时触发 swayhand 动画

**Files:**
- Modify: `src/main/java/net/mcreator/strangerecord/commands/PurpleGuiCommand.java`

- [ ] **Step 1: 处理 phase 1 生成逻辑**

在 `executeOpen` 方法中，将 phase 判断改为：

```java
if (phase == 4) {
    // 阶段4逻辑不变...
    // (保持现有代码)
} else if (phase == 1) {
    // 阶段1：紫怪在玩家正前方
    double frontX = -Math.sin(yawRad) * 2.0;
    double frontZ = Math.cos(yawRad) * 2.0;
    double spawnX = player.getX() + frontX;
    double spawnZ = player.getZ() + frontZ;
    double spawnY = player.getY();

    PurpleMonsterEntity purpleMonster = new PurpleMonsterEntity(StrangerecordModEntities.PURPLE_MONSTER.get(), serverLevel);
    purpleMonster.setPos(spawnX, spawnY, spawnZ);
    purpleMonster.setXRot(0.0F);
    purpleMonster.setYRot(player.getYRot() + 180.0F);
    purpleMonster.yBodyRot = player.getYRot() + 180.0F;
    purpleMonster.yHeadRot = player.getYRot() + 180.0F;
    purpleMonster.setPerformMode(player.getUUID());
    // 纹理默认已经是 purpleboy (variant=0)，不需要额外设置
    serverLevel.addFreshEntity(purpleMonster);

    // 触发 swayhand 动画
    purpleMonster.triggerSwayhand();

    PacketDistributor.sendToPlayer(player, new OpenPurpleGuiPacket(purpleMonster.getId(), phase, 0));
    ctx.getSource().sendSuccess(() -> Component.literal("打开紫怪界面 - 阶段" + phase), true);
} else {
    // 阶段2：现有逻辑不变
    // (保持现有代码)
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/net/mcreator/strangerecord/commands/PurpleGuiCommand.java
git commit -m "feat: Phase 1 /purplegui 1 spawns monster with purpleboy texture and swayhand animation"
```

---

### Task 8: 构建验证

- [ ] **Step 1: 构建项目**

```bash
./gradlew build
```

- [ ] **Step 2: 检查编译错误**

确认没有编译错误。如有错误，根据错误信息修复。

---

## 自审

1. **Spec coverage**: 对照 spec 的6个修改点逐一检查，全部覆盖。动画文件 (swayhand)、实体 (texture variant)、渲染器 (dynamic texture)、Screen (phase1 逻辑)、ClientHandler (状态机)、Command (触发 swayhand)。
2. **Placeholder scan**: 无 TBD/TODO，所有代码均为完整实现。
3. **Type consistency**: `int` variant (0/1)、`triggerSwayhand()`/`triggerModelHand()` 方法名统一、`TransitionToPhase2Packet` 包含 `purpleEntityId`、`Phase1Transition` 内部类 phase 用 int 常量。
