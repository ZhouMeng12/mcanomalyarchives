# 理智监测仪HUD重新设计

## 概述

重写理智监测仪HUD界面，引入动态设备编号、像素风指示灯、4格白色电池、兰达指数（上瘾度）、Minecraft AE Pixel字体，以及基于FE能量的电量系统。

---

## 技术架构

### 数据组件（Data Components）

新增两个 `DataComponentType`，注册在物品上持久化数据：

| 组件 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `serail_number` | `String` | 合成时随机生成 | 大写字母+数字1-9，如 `X7` |
| `energy` | `Integer` | 最大FE值（如 12000） | FE能量值，4格档位=每格3000FE |

**注册位置**: `StrangerecordModAttachments.java` 或新建 `init/StrangerecordModDataComponents.java`

**合成时赋值**: 在 `DetecterItem` 或 craft 事件中，随机生成 `serail_number`，设置初始 `energy` 为最大值。

### 电量系统

**FE容量**: 12000 FE（每格 = 3000 FE）

**消耗速率**: 装备时每 tick 消耗 1 FE（约 20 FE/秒），满电可持续 ~10分钟

**视觉**: 4格白色电池

| FE范围 | 格数 | 颜色 |
|--------|------|------|
| 9001 - 12000 | 4格 | 白色 |
| 6001 - 9000 | 3格 | 白色 |
| 3001 - 6000 | 2格 | 白色 |
| 1 - 3000 | 1格 | 白色 |
| 0 | 0格 | HUD消失 |

**充电**: FE充电器（如其他模组的充电站）右键检测仪可充电（`Item#isBarVisible` + FE capability兼容）

**耗尽行为**: 电量归零后 HUD 完全不显示（opacity=0 或直接 return）

---

## HUD界面设计

### 布局

```
┌────────────────────────────────────────────────────────┐
│ 3 [●] 神经监控-X7              诺德指数: 较高     0  │
│                                                        │
│                                                        │
│                                                        │
│                                                        │
│   ┌──┬──┬──┬──┐ ┐                        2026/07/19   │
│ 7 │██│██│██│██│ │                        14:32:05  4  │
│   └──┴──┴──┴──┘ ┘                                     │
└────────────────────────────────────────────────────────┘
```

### 各部分说明

| 位置 | 元素 | 内容 | 颜色规则 |
|------|------|------|----------|
| 左上 | 像素指示灯 | 12×12像素圆（Canvas绘制，24×24画布×2px像素） | 绿(#99FF00)/黄(#FFCC00)/红(#FF3333)，危险时闪烁 |
| 左上 | 设备编号 | `神经监控-{SERIAL}` | 白色(#FFFFFF) |
| 右上 | 诺德指数 | 7档文字：极高/较高/正常/较低/低/警戒/危险 | 同上颜色规则 |
| 右上 | 兰达指数 | `兰达指数: X.X`，无上瘾时隐藏 | 白色(#FFFFFF) |
| 左下 | 电池 | 白色边框 + 4段白色填充，段间3px间隙 | 白色填充 |
| 右下 | 日期时间 | `yyyy/MM/dd HH:mm:ss` | 白色(#FFFFFF) |
| 四角 | 角标 | "3", "0", "7", "4" | 白色(#FFFFFF) |
| 四角 | 对角线 | 从角落延伸到80px | 半透明白色 |

### ❌ 删除的元素

- 底部中央状态文本（"神经系统稳定"等）
- 兰达指数（已替代）

### 诺德指数映射（7档）

| 理智范围 | 诺德指数 | 指示灯颜色 |
|----------|----------|------------|
| 90-100 | 极高 | 绿 #99FF00 |
| 75-89 | 较高 | 绿 #99FF00 |
| 60-74 | 正常 | 绿 #99FF00 |
| 45-59 | 较低 | 黄 #FFCC00 |
| 30-44 | 低 | 黄 #FFCC00 |
| 15-29 | 警戒 | 红 #FF3333 |
| 0-14 | 危险 | 红闪烁 #FF4444 |

### 兰达指数（上瘾度）

- 来源：玩家拥有 `ADDICTE` 药水效果时显示
- 显示格式：`兰达指数: X.X`
- 数值 = `amplifier + 1`（直接映射）
- 范围：1.0 - 7.0（超过7.0显示7.0）
- 无上瘾效果时隐藏该行

---

## 字体方案

### TTF字体注册

将 `5_Minecraft AE(支持中文).ttf` 注册为Minecraft资源包字体：

1. 文件放置：`assets/strangerecord/font/ae_pixel.ttf`
2. 字体定义：`assets/strangerecord/font/ae_pixel.json`
   ```json
   {
     "providers": [
       {
         "type": "ttf",
         "file": "strangerecord:font/ae_pixel.ttf",
         "shift": [0, 0],
         "size": 11,
         "oversample": 1
       }
     ]
   }
   ```

3. HUD中使用：
   ```java
   Font aeFont = mc.font;
   // 如果字体被正确注册为默认替换，直接用 mc.font
   // 否则需要 getFontManager().createFont() 或使用 resource location
   ```

由于Minecraft 1.21.x的字体系统限制，TTF字体注册为自定义字体后，可以通过 `Minecraft.getInstance().font.displayFont` 方式使用，或直接替换默认ASCII渲染。如果TTF注册复杂度过高，**后备方案**：使用Minecraft默认像素字体（`mc.font`），它本身就是像素风格。

---

## 需要修改的文件

### 1. `SanityMonitorHudOverlay.java`
**路径**: `src/main/java/net/mcreator/strangerecord/client/`
**变更**: 完全重写渲染逻辑
- 删除 `renderBottomCenter` 方法
- 新增 `drawPixelCircle` 方法（像素圆）
- 修改 `renderTopLeft` → 标题改为动态编号
- 修改 `renderTopRight` → 7档诺德指数 + 兰达指数行
- 新增 `renderTopRightLanda` → 上瘾度显示
- 修改 `renderBottomLeft` → 4格白色电池
- 读取物品 DataComponent 获取 serial 和 energy

### 2. `StrangerecordModAttachments.java` 或 新建 `StrangerecordModDataComponents.java`
**变更**: 注册两个 DataComponentType

### 3. `DetecterItem.java`
**路径**: `src/main/java/net/mcreator/strangerecord/item/`
**变更**: 重写以设置默认 DataComponent 值（序列号随机、能量拉满）

### 4. `SanityMonitorDetector.java`
**变更**: 新增 `getDetectorItemStack(player)` 方法返回检测仪 ItemStack，供HUD读取数据

### 5. 字体文件
**新增**: `assets/strangerecord/font/ae_pixel.ttf`
**新增**: `assets/strangerecord/font/ae_pixel.json`

### 6. 不修改的文件
- `SanityMonitorLayer.java`（3D模型渲染，保持不变）
- `SanityClientHandler.java`（理智缓存同步，保持不变）

---

## 关键决策

1. **Data Components 而非 NBT**: 1.21.x 推荐的数据持久化方式
2. **FE能量而非时间计时**: 支持与其他模组充电设备交互
3. **电池仅白色**: 不随电量变色，靠格数直观反映
4. **兰达指数仅上瘾时显示**: 无上瘾时隐藏，避免视觉干扰
5. **字体后备方案**: TTF优先，不可用则退回到默认像素字体
6. **电量耗尽 = 无HUD**: 不在电量0时显示任何HUD元素

---

## 验证步骤

1. 合成检测仪 → 检查物品tooltip是否显示序列号和电量
2. 装备检测仪 → HUD显示，确认标题为随机编号
3. 等待/使用 → 确认电量消耗和格数变化
4. 电量耗尽 → 确认HUD消失
5. 获得上瘾效果 → 确认兰达指数显示
6. 失去上瘾效果 → 确认兰达指数隐藏
7. 切换理智值 → 确认指示灯颜色和诺德指数联动
8. 低理智 → 确认红色闪烁
9. 充电设备交互 → 确认电量恢复
