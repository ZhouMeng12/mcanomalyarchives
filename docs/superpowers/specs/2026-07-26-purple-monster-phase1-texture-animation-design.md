# 紫怪阶段1 纹理切换 & 动画设计

## 背景

紫怪目前所有阶段统一使用 `purple.png` 纹理。需要在阶段1和常态使用 `purpleboy.png`，阶段1结束后通过动画过渡切换到 `purple.png` 再进入阶段2。

## 需求

| 阶段 | 纹理 | 动画 |
|---|---|---|
| 常态（自然生成） | `purpleboy` | 默认 idle/walk |
| 阶段1 开始 (`/purplegui 1`) | `purpleboy` | 播放 `swayhand`（hold_on_last_frame） |
| 阶段1 结束（紫色按钮） | `purpleboy` → `purple` | 播放 `model.hand` → 切换纹理 → 开阶段2 GUI |
| 阶段2 | `purple` | 不需要玩 `model.hand` |

## 架构

### 方案选型

选择方案 A（客户端驱动）：动画是纯客户端渲染，由 `PurpleMonsterScreen` 本地触发动画 → 等播完 → 切换纹理 → 开新 GUI。不需要服务端等待动画同步，避免硬编码 tick 计数。

### 数据流

```
阶段1开始
  PurpleGuiCommand → 生成紫怪(perform mode, texture=purpleboy) → 触发 swayhand → 开GUI

阶段1灰色按钮（"没事"）
  客户端 → RemovePurpleMonsterPacket → 服务端删除紫怪 → 不变

阶段1紫色按钮（"救救我"）
  客户端：关GUI → 触发 model.hand(0.5s) → 等待播完 → setTextureVariant(1:purple)
         → 发 TransitionToPhase2Packet → 服务端开阶段2GUI
```

## 修改清单

### 1. PurpleMonsterEntity.java

- 新增 `SYNCED_DATA` `TEXTURE_VARIANT`（int），默认 `0`（purpleboy），`1`（purple）
- 新增 `setTextureVariant(int)` / `getTextureVariant()`
- 新增 `triggerSwayhand()` / `triggerModelHand()` 通过 animation controller 触发

### 2. PurpleMonsterRenderer.java（GeoModel 内）

- `getTextureResource()` 根据 entity 的 `TEXTURE_VARIANT` 返回对应纹理：
  - `0` → `strangerecord:textures/entities/purpleboy.png`
  - `1` → `strangerecord:textures/entities/purple.png`

### 3. purplemonster.animation.json

- 将 bedrock 动画 `swayhand`（hold_on_last_frame, 0.5s）合并进 GeckoLib 动画文件
- `model.hand` 与现有 `animation.purplemonster.hand` 内容相同，直接在代码中复用该动画名

### 4. PurpleMonsterScreen.java

- `renderPhase1()` 不做变更
- `onPhase1GrayClicked()` — 不改（保持现有删除逻辑）
- `onPhase1PurpleClicked()` — **新流程**：
  1. 关闭当前 GUI（`onClose()`）
  2. 存储紫怪引用
  3. 通过 `PurpleMonsterPhase1ClientHandler` 启动客户端状态机

### 5. 新建 PurpleMonsterPhase1ClientHandler.java

客户端 tick 状态机（参考已有的 `PurpleDogPettingClientHandler`）：

| 状态 | 动作 | 持续 |
|---|---|---|
| PLAY_ANIM | 触发紫怪 `model.hand` 动画 | 10 tick (0.5s) |
| SWITCH_TEXTURE | 设置 `TEXTURE_VARIANT = 1` | 1 tick |
| OPEN_PHASE2 | 发 `TransitionToPhase2Packet` 到服务端 | 1 tick → DONE |

### 6. 新建 TransitionToPhase2Packet.java

客户端 → 服务端网络包，携带紫怪 entityId。服务端接收后直接打开阶段2 GUI（跳过 transition handler 的删除/重生逻辑）。

### 7. PurplePhaseTransitionHandler.java

- 保持不变（阶段4的过渡逻辑不动）
- 阶段2 走新的 `TransitionToPhase2Packet`，不经过 transition handler

### 8. PurpleGuiCommand.java

- 生成紫怪时设置 texture variant 为 `0`（purpleboy）
- 触发 `swayhand` 动画后再开 GUI

## 不修改的文件

- `RemovePurpleMonsterPacket` — 灰色按钮逻辑不变
- `PurpleDogPettingClientHandler` — 不影响
- `PurpleDogEntity` — 不影响

## 注意事项

- `purpleboy.png` 已存在于 `textures/entities/` 目录
- `swayhand` 动画需从 `bedrock_animations/model1.animation.json` 合并到 `geckolib/animations/purplemonster.animation.json`
- `model.hand` 动画内容与现有 `animation.purplemonster.hand` 一致，直接引用即可
- 客户端状态机注册在 `ClientTickEvent` 或 Screen tick 中
