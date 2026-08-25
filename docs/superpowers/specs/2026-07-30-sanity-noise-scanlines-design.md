# 低理智噪点+扫描线效果设计

## 目标

在佩戴神经检测仪且理智 ≤ 49 时，画面叠加均匀黑白雪花噪点 + 水平扫描线效果，模拟老旧CRT/监控摄像头故障感。性能优化为核心约束——不能像旧方案那样逐像素 fill 导致卡顿。

## 当前状态

- `SanityScreenEffects.noiseIntensity` / `noiseDensity` — 已有计算逻辑，理智降低时渐进增强
- `SanityClientHandler.noisePattern[256]` — 已生成但从未用于渲染（纯孤立代码）
- `SanityClientHandler.onRenderGuiPost` — 目前只渲染暗角+去饱和，未处理噪声
- 旧方案已删除：曾使用 `graphics.fill()` 逐像素绘制噪声，帧率极差

## 设计方案：纹理平铺 + 扫描线

### 效果层次

```
理智 | 噪点透明度 | 扫描线透明度 | 效果
---|---|---|---
75-100 | 0 | 0 | 无
50-74 | 15-25% | 7-12% | 轻微雪花+稀疏扫描线
25-49 | 25-45% | 12-22% | 明显雪花+扫描线
0-24 | 45-70% | 22-35% | 强烈雪花+密集扫描线
```

### 渲染顺序（在 onRenderGuiPost 中）

```
1. 噪点纹理平铺（最底层）
2. 扫描线（中层，覆盖在噪点上）
3. 暗角（已有，不改动）
4. 去饱和灰色覆盖（已有，不改动）
```

### 噪点纹理实现

**方法：预生成 NativeImage → 平铺 blit**

- 纹理尺寸：512×512（固定，不随分辨率变化）
- 每5 tick 重新随机填充纹理像素（黑白各50%概率，带alpha通道）
- 用 `GuiGraphics.blit()` 平铺至全屏（TextureRender 或内联着色器绑定）
- MC 渲染方式：
  1. 创建 `NativeImage(128, 128, false)` 
  2. 遍历像素：`Math.random() < density` → 白色或黑色
  3. 上传为 `DynamicTexture` → 获取 `ResourceLocation`
  4. `guiGraphics.blit(textureId, x, y, 0, 0, tileW, tileH, tileW, tileH)` 循环平铺

或者更简单的方案：
  1. 在 `onRenderGuiPost` 中用 `RenderSystem` 开关混合模式
  2. 使用 MC 的 `GuiGraphics.innerBlit()` 或直接 Tessellator 绘制带 alpha 的纹理四边形

**推荐简化方案（避免 DynamicTexture 复杂注册）：**

直接在渲染循环中，用 `guiGraphics.fill()` 画128x128 的随机像素块——但不是逐像素！是逐**tile**：
- 直接把屏幕分成 512×512 的 tile 网格
- 每个 tile 根据 `noiseIntensity` 随机选择：全透明 / 全白半透 / 全黑半透
- 每5 tick 重新随机

这样在 1920x1080 下只有 `4 × 3 = 12`  次 fill 调用（每个 tile 一次），比之前的上万次少两个数量级。

### 扫描线实现

```java
int lineGap = 3;
int alpha = (int)(scanlineAlpha * 255) << 24;
for (int y = 0; y < height; y += lineGap) {
    guiGraphics.fill(0, y, width, y + 1, alpha);
}
```

1080p下 ~360 次 fill，开销极低。

### 触发条件

- 佩戴检测仪（`SanityMonitorDetector.isWearingSanityMonitor(player)` 返回 true）
- 理智值 ≤ 49（复用 `SanityScreenEffects.noiseIntensity`）
- 理智 > 49 或未佩戴检测仪 → 不渲染任何噪点/扫描线

## 修改文件

### `SanityClientHandler.java`

在 `onRenderGuiPost` 方法中，暗角/去饱和渲染之后新增：

1. 检查 `noiseIntensity > 0`
2. 生成/刷新 tile 状态（每3 tick）
3. 渲染噪点 tile 网格
4. 渲染扫描线

需要新增字段：
- `noiseTileStates` — boolean[] 数组，记录每个 tile 是否显示、显示白还是黑
- `noiseUpdateCounter` — 更新计数器

### `SanityScreenEffects.java`

不改动。已有 `noiseIntensity` 和 `noiseDensity` 计算逻辑满足需求。

## 不涉及

- 不新增文件
- 不新增 Mixin
- 不改动 `SanityMonitorHudOverlay`（HUD 保持不变）
- 不改动理智数据层

## 假设

1. Tile 噪点方案比真正的纹理平铺简单，512px tile 粗粒度雪花感更接近老电视效果
2. 12 次 fill + 360 次 fill ≈ 372 次 fill/帧，性能几乎无影响
3. 更新间隔5 tick = 每秒4次刷新，雪花闪烁自然

## 验证

1. 编译通过
2. 佩戴检测仪 + `/sanity reduce 60` → 理智 ≤ 49 → 出现雪花+扫描线
3. `/sanity reduce 80` → 理智 = 20 → 雪花和扫描线明显增强
4. 摘下检测仪 → 效果立即消失
5. FPS 无明显下降
