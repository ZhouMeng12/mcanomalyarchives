# 理智系统 设计文档

**日期:** 2026-07-13
**模组:** 诡异见闻录 (strangerecord)
**Minecraft:** 1.21.8 / NeoForge 21.8.31

---

## 1. 概述

为玩家添加克苏鲁风格的"理智值"(Sanity)系统。理智值是一个 0-100 的持久化数值，受特定恐怖事件扣减，低理智时触发纯负面屏幕效果。理智可通过自然恢复和特定行为恢复。死亡时重置为满值。

---

## 2. 数据模型

### 2.1 PlayerSanity

使用 NeoForge `AttachmentType` 附加到 `Player` 实体，持久化到玩家 NBT。

```java
public class PlayerSanity {
    public static final int MAX_SANITY = 100;
    public static final int MIN_SANITY = 0;

    private int sanity = MAX_SANITY;        // 当前理智值
}
```

- **范围**: 0-100，初始 100
- **持久化**: 通过 AttachmentType 的 Codec 序列化到玩家 NBT
- **同步**: NeoForge 自动同步 Attachment 到客户端
- **特殊规则**: 玩家死亡时理智重置为 100

### 2.2 注册

在 `init/StrangerecordModAttachments.java` 中注册 AttachmentType。

---

## 3. 理智下降事件

所有事件触发式一次性扣除，服务端计算。

| 触发事件 | 扣除值 | 冷却/条件 |
|---------|--------|----------|
| 进入云之维度 | -35 | 同一玩家 5 分钟冷却 |
| 靠近 SadPoppy（愤怒虞美人） | -5 | 128格内，每 30 秒一次 |
| 获得悲伤效果 | -8 × 悲伤等级 | Sad 效果施加时触发 |
| 喝伪云水 | -25 | 使用 CloudWaterBottle / CloudWaterItem 完成饮用时 |
| 受到普通伤害 | -1 / 每 2 点伤害 | 按实际受到伤害量计算，最少 -1 |
| 受到脑损伤伤害 | -8 | Braindamage 伤害类型（降低，原 -15） |
| 异钓竿钓到 Tier 3 生物 | -20 | 诡异度 0.70~1.00 档位（凋零、监守者等） |

**钓鱼档位参考**（来自异钓竿设计）：

| Tier | 诡异度 | 距离 | 生物示例 |
|------|--------|------|---------|
| 0 | 0.00~0.30 | ~2-6格 | 猪、羊、僵尸、骷髅 |
| 1 | 0.25~0.50 | ~5-9格 | 末影人、烈焰人、恶魂 |
| 2 | 0.45~0.75 | ~8-12格 | 远古守卫者、安布拉等 |
| 3 | 0.70~1.00 | ~11-15格 | 凋零、监守者 |

---

## 4. 低理智效果

纯屏幕效果，无数值 UI。客户端 Mixin 注入渲染管线实现。

| 理智范围 | 效果 |
|---------|------|
| 75~100 | 无效果，正常 |
| 50~74 | 屏幕边缘一圈暗角（低频闪烁），饱和度轻微降低 |
| 25~49 | 暗角加深 + 饱和度明显降低 + FOV 微幅随机抖动 + 偶尔幻听 |
| 0~24 | 暗角极深 + 饱和度大幅降低（近乎灰白） + FOV 明显抖动 + 更高频幻听 + 移动方向偶尔随机偏移 + 周期性脑损伤伤害 |

### 4.1 暗角 (Vignette)

屏幕四角/边缘出现黑色渐变晕影，理智越低越深、范围越大。通过 Mixin `LevelRenderer` 的 `renderLevel` 或 `renderGui` 阶段叠加半透明遮罩纹理。

### 4.2 饱和度降低

通过 Mixin 修改渲染时的颜色饱和度参数。0~24 范围内画面接近灰白。

### 4.3 FOV 抖动

`GameRenderer.getFov()` 在基础 FOV 上叠加 ±2~5 度的随机偏移（低频正弦波 + 噪声），模拟眩晕感。

### 4.4 幻听

随机播放模组内已有的诡异音效（如 `hei_.ogg`），音量低，间隔 10-30 秒随机。

### 4.5 移动偏移

`MovementInput` 方向有 ~5% 概率被随机旋转一个小角度，模拟失控感。仅在 0~24 范围触发。

### 4.6 周期性脑损伤伤害

当理智在 0~24 范围时，每 10 秒（200 ticks）受到 1 点脑损伤（Braindamage）伤害。**此伤害不会触发理智扣除**，避免无限循环。此伤害用于体现低理智时精神崩溃对身体的反馈。

---

## 5. 理智恢复与血量联动

理智恢复不再有受伤冷却，改为根据玩家当前血量比例动态变化。

### 5.1 血量驱动恢复/扣除

| 血量比例 | 理智变化 | 速率 |
|---------|---------|------|
| ≥ 80% | 恢复 | +1 / 每 2 秒（40 ticks） |
| 50% ~ 79% | 恢复 | +1 / 每 4 秒（80 ticks） |
| 20% ~ 49% | 恢复 | +1 / 每 8 秒（160 ticks） |
| < 20% | 扣除 | -1 / 每 5 秒（100 ticks） |

- 血量比例 = 当前生命值 / 最大生命值
- 高血量时理智恢复快，血量越低恢复越慢
- 残血（<20%）时理智不恢复反而缓慢下降
- 此机制每 tick 在 PlayerTick 中检查，不依赖受伤冷却

### 5.2 椅子恢复

- 速率：+3 / 每 2 秒（40 ticks）
- 条件：玩家坐在模组 Chair 方块上
- 检测方式：检查玩家骑乘的实体是否为 `SittingEnityEntity`
- 椅子恢复**不受血量影响**，固定速率（坐椅子是主动休息行为）

---

## 6. 架构

```
┌─────────────────────────────────────────────────┐
│                  Server Side                      │
│  ┌──────────────────┐  ┌──────────────────────┐ │
│  │ PlayerSanity     │  │ SanityEventHandler    │ │
│  │ (AttachmentType)  │◄─│ - 维度切换            │ │
│  │ - sanity: int     │  │ - SadPoppy 范围       │ │
│  └──────┬───────────┘  │ - Sad 效果施加        │ │
│         │              │ - 喝伪云水            │ │
│         ▼              │ - 伤害事件（缩放）     │ │
│  ┌──────────────────┐  │ - 钓鱼 Tier3          │ │
│  │ SanityRecoveryHandler                      │ │
│  │ - 血量驱动恢复/扣除                         │ │
│  │ - Chair 坐椅子检测                          │ │
│  │ - 低理智脑损伤伤害                          │ │
│  └──────────────────┘  └──────────────────────┘ │
│         │                                         │
│         ▼ 网络同步（Attachment 自动同步）          │
├─────────────────────────────────────────────────┤
│                  Client Side                      │
│  ┌──────────────────┐  ┌──────────────────────┐ │
│  │ SanityClientData  │  │ SanityScreenEffects   │ │
│  │ (客户端缓存)       │  │ - 暗角渲染            │ │
│  └──────────────────┘  │ - 饱和度修改           │ │
│                         │ - FOV 抖动             │ │
│                         │ - 幻声音效             │ │
│                         │ - 移动偏移             │ │
│                         └──────────────────────┘ │
└─────────────────────────────────────────────────┘
```

---

## 7. 文件改动

### 7.1 新增文件

| 文件 | 路径 | 说明 |
|------|------|------|
| `PlayerSanity.java` | `src/main/java/net/mcreator/strangerecord/sanity/PlayerSanity.java` | 理智数据类，含 Codec 序列化 |
| `StrangerecordModAttachments.java` | `src/main/java/net/mcreator/strangerecord/init/StrangerecordModAttachments.java` | 注册 AttachmentType |
| `SanityEventHandler.java` | `src/main/java/net/mcreator/strangerecord/events/SanityEventHandler.java` | 服务端事件监听，扣减理智 |
| `SanityRecoveryHandler.java` | `src/main/java/net/mcreator/strangerecord/events/SanityRecoveryHandler.java` | PlayerTick 恢复逻辑 |
| `SanityClientHandler.java` | `src/main/java/net/mcreator/strangerecord/client/SanityClientHandler.java` | 客户端理智缓存 + 屏幕效果调度 |
| `SanityScreenEffects.java` | `src/main/java/net/mcreator/strangerecord/client/SanityScreenEffects.java` | 暗角、饱和度、FOV、幻听、移动偏移实现 |

### 7.2 修改文件

| 文件 | 改动 |
|------|------|
| `events/StrangeFishingRodLootHandler.java` | 钓到 Tier3 生物时触发理智扣除 |
| `events/PlayerLookAtCornPoppyListener.java` | Sad 效果施加时触发理智扣除 |
| `item/CloudWaterButtleItem.java` | 饮用完成时触发理智扣除 |
| `item/CloudWaterItem.java` | 饮用完成时触发理智扣除 |
| `mixin/` (LevelRenderer 相关 Mixin) | 注入暗角渲染、饱和度修改、FOV 修改 |
| `StrangerecordMod.java` | 注册 AttachmentType 到 ModEventBus |

### 7.3 新增目录

- `src/main/java/net/mcreator/strangerecord/sanity/` — 理智系统独立包

---

## 8. 假设与决策

1. **NeoForge AttachmentType**: 使用 NeoForge 1.21.x 推荐的数据附加方式，通过 Codec 序列化到玩家 NBT。
2. **不注册自定义药水效果**: 低理智效果通过 Mixin 直接注入渲染管线，而非使用 MobEffect。画面扭曲由客户端独立实现。
3. **Chair 检测**: 通过检查玩家 `getVehicle()` 是否为 `SittingEnityEntity` 实例判断是否坐在椅子上。
4. **云之维度检测**: 通过 `PlayerChangedDimensionEvent` 或检查 `player.level().dimension()`。
5. **SadPoppy 范围检测**: 复用 `CornPoppyAngryListener` 中已有的 SadPoppy 128格范围逻辑，或在该 Listener 中新增理智扣除。
6. **脑损伤伤害**: 通过 `LivingHurtEvent` 检查 `DamageSource` 类型匹配 `braindamage`。
7. **钓鱼 Tier3**: 在 `StrangeFishingRodLootHandler` 生成生物时，若生物属于 Tier3 池，触发理智扣除。Tier3 生物列表：`wither`、`warden`。
8. **屏幕效果优先级**: 暗角 > 饱和度 > FOV > 幻听 > 移动偏移。所有效果按理智值线性插值强度。
9. **伤害缩放扣减**: 普通伤害理智扣除 = max(1, floor(damage / 2))。例如 3 点伤害扣 1 理智，8 点伤害扣 4 理智。
10. **低理智脑损伤伤害豁免**: 低理智触发的周期性脑损伤伤害不会再次扣减理智，通过标记或检查伤害来源跳过 SanityEventHandler。
11. **血量驱动恢复**: 不使用受伤冷却，每 tick 基于 `player.getHealth() / player.getMaxHealth()` 计算恢复速率。

---

## 9. 边界情况

| 场景 | 处理 |
|------|------|
| 理智值已为 0 时再次扣减 | 保持为 0，不溢出 |
| 理智值已为 100 时恢复 | 保持为 100，不溢出 |
| 玩家离线 | Attachment 数据随玩家 NBT 持久化，重新上线后恢复 |
| 创造模式/观察者模式 | 不扣减理智，不应用屏幕效果 |
| 和平模式 | 理智系统正常工作（不受难度影响） |
| 服务端无 mod | Attachment 默认值 100，无效果 |
| 同时多个事件触发 | 每个事件独立处理，累加扣减 |
| 椅子被破坏时玩家坐在上面 | 检测 player.getVehicle() 为 null 时停止椅子恢复 |

---

## 10. 验证步骤

1. 编译通过: `gradlew build`
2. 进入游戏，确认理智初始值 100
3. 进入云之维度，确认理智 -35
4. 获得 Sad 效果，确认理智 -8×等级
5. 喝伪云水，确认理智 -25
6. 受不同量伤害，确认理智扣除按 damage/2 缩放（最小 1）
7. 进入 SadPoppy 128格范围，确认每30秒 -5
8. 用异钓竿钓到 Tier3 生物（凋零/监守者），确认 -20
9. 理智降到 50 以下，确认屏幕出现暗角
10. 理智降到 25 以下，确认饱和度降低 + FOV 抖动 + 幻听
11. 理智降到 0~24，确认移动偏移 + 周期性脑损伤伤害（且此伤害不扣理智）
12. 满血时等待，确认理智按血量比例恢复（≥80%时最快）
13. 残血（<20%）时等待，确认理智缓慢下降而非恢复
14. 坐上椅子，确认理智恢复 +3/2秒（不受血量影响）
15. 死亡后，确认理智重置为 100
16. 退出重进，确认理智值持久化保留
