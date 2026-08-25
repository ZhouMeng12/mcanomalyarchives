# 低理智效果重新设计

## 目标

修复现有低理智效果中的问题，去掉不需要的效果，新增去饱和和幻听，使低理智体验更加沉浸和有压迫感。

## 当前状态

### 理智系统数据层 ✅（不改动）
- `PlayerSanity` — NeoForge AttachmentType，0-100 范围，持久化，死亡重置
- 7种理智降低事件 + HP驱动恢复/扣减 + 椅子恢复
- 理智 <= 24 时每10秒受到1点脑死亡伤害

### 现有画面效果
| 层级 | 理智范围 | 当前效果 | 状态 |
|---|---|---|---|
| 正常 | 75-100 | 无 | ✅ |
| 轻度 | 50-74 | 暗角 + 闪烁 | ⚠️ 暗角几乎不可见 |
| 中度 | 25-49 | 暗角 / FOV抖动 / 洞穴音效 | ⚠️ FOV抖动要删 |
| 重度 | 0-24 | 以上全部 + 脑死亡伤害 | ⚠️ 去饱和/雪花/偏移 未实现 |

### 问题
1. **暗角太弱**：当前用四边黑条（`GuiMixin.fill`），15%高度边条 + 70%透明度，几乎看不到
2. **FOV抖动需要删除**：用户要求去掉
3. **去饱和写了但没渲染**：`SanityScreenEffects.desaturationStrength` 已计算，无渲染实现
4. **幻听太单调**：只有 `AMBIENT_CAVE` 一种音效

## 设计方案

### 不改动层级划分（保持现有）

| 层级 | 理智 | 效果 |
|---|---|---|
| 正常 | 75-100 | 无 |
| 轻度 | 50-74 | 暗角 + 闪烁 |
| 中度 | 25-49 | 暗角 / 去饱和 / 幻听音效 |
| 重度 | 0-24 | 中度全部 + 暗角加强 + 去饱和加深 + 脑死亡伤害 |

### 文件一：`SanityScreenEffects.java`（效果计算层）

**改动：**
1. 删除 `fovJitter` 字段和相关计算（`updateFovJitter` 方法）
2. 删除不再使用的字段：`noiseStrength`, `noiseTimer`, `noiseCooldown`, `movementOffsetChance`, `movementOffsetStrength` ——至少去除雪花和移动偏移的计算逻辑
3. 新增幻听音效触发逻辑：
   - **中度 (25-49)**：每 15-30 秒随机播放 `SoundEvents.AMBIENT_CAVE` 或 `SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD`，音量 0.1-0.25
   - **重度 (0-24)**：每 10-20 秒随机播放 `SoundEvents.AMBIENT_CAVE` / `SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD` / `SoundEvents.ELDER_GUARDIAN_AMBIENT`，音量 0.2-0.4
4. `desaturationStrength` 和 `vignetteStrength` 计算保持现有逻辑不变

### 文件二：`GuiMixin.java`（渲染层）

**改动：**
1. **暗角重做**：用多层径向渐变替代四边黑条
   - 用 `GuiGraphics.fill()` 画多个同心矩形（4-5层），从屏幕边缘向内逐层降低透明度
   - 效果：屏幕四周逐渐变暗，中央保持明亮，形成真正的"隧道视野"
   - 透明度范围：外层 ~70%，最内层 ~10%
   - 每层宽度 = `(vignetteStrength * 屏幕短边 * 0.07)`

2. **去饱和灰色覆盖层**：
   - 当 `desaturationStrength > 0` 时，画全屏灰色矩形
   - 颜色：`(128, 128, 128)` —— 中性灰
   - 透明度：`desaturationStrength * 0.35` —— 最大35%灰色覆盖
   - 理智越低越灰，模拟色彩流失

3. **渲染顺序**（在 `Gui.render` 注入点内）：
   ```
   1. 暗角（先画，在底层）
   2. 去饱和灰色覆盖层（后画，在上层）
   ```

### 文件三：`SanityClientHandler.java`

**改动：**
- 删除 `onComputeFov()` 方法中对 `SanityScreenEffects.fovJitter` 的引用
- 如果 FOV 事件注册仅用于 jitter，则删除整个 FOV 事件注册

## 技术细节

### 暗角实现（伪代码）

```java
// 在 GuiMixin 注入中
float vignette = sanityEffects.getVignetteStrength();
if (vignette > 0) {
    int centerX = width / 2;
    int centerY = height / 2;
    int maxRadius = (int) (Math.min(width, height) * 0.75);
    int layers = 5;
    
    for (int i = 0; i < layers; i++) {
        float t = (float) i / (layers - 1);  // 0.0 → 1.0
        int radius = (int) (maxRadius * (0.5f + t * 0.5f));  // 从内到外
        
        // 每层画一个空心矩形框
        int alpha = (int) (vignette * (0.1f + t * 0.6f) * 255);  // 外层更深
        int color = alpha << 24;  // AARRGGBB，RGB=0（纯黑）
        
        int x0 = centerX - radius;
        int y0 = centerY - radius;
        int x1 = centerX + radius;
        int y1 = centerY + radius;
        
        // 画四边：上、下、左、右
        guiGraphics.fill(0, 0, width, y0, color);           // 上
        guiGraphics.fill(0, y1, width, height, color);       // 下
        guiGraphics.fill(0, y0, x0, y1, color);              // 左
        guiGraphics.fill(x1, y0, width, y1, color);          // 右
    }
}
```

### 幻听音效实现（伪代码）

```java
// 在 SanityScreenEffects 中
private int hallucinationCooldown = 0;

public void tick() {
    // ... 现有逻辑 ...
    
    if (sanity <= 49 && sanity > 24) {
        // 中度：低频幻听
        hallucinationCooldown--;
        if (hallucinationCooldown <= 0) {
            SoundEvent sound = random.nextBoolean() 
                ? SoundEvents.AMBIENT_CAVE 
                : SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD;
            float volume = 0.1f + (1 - sanity / 50f) * 0.15f;
            client.player.playSound(sound, volume, 1.0f);
            hallucinationCooldown = 300 + random.nextInt(300); // 15-30s
        }
    } else if (sanity <= 24) {
        // 重度：高频幻听 + 深海守护者
        hallucinationCooldown--;
        if (hallucinationCooldown <= 0) {
            SoundEvent sound = switch (random.nextInt(3)) {
                case 0 -> SoundEvents.AMBIENT_CAVE;
                case 1 -> SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD;
                default -> SoundEvents.ELDER_GUARDIAN_AMBIENT;
            };
            float volume = 0.2f + (1 - sanity / 25f) * 0.2f;
            client.player.playSound(sound, volume, 1.0f);
            hallucinationCooldown = 200 + random.nextInt(200); // 10-20s
        }
    }
}
```

## 假设与决定

1. **灰色覆盖层替代真·去饱和**：不使用 shader/keying Mixin，避免 1.21.8 渲染API兼容问题，效果类似且可靠
2. **暗角用 fill() 实现**：不引入额外纹理，代码自包含
3. **音效用 client.player.playSound()**：不修改服务端数据，纯客户端效果
4. **删除的字段只删计算逻辑**：不删 getter（可能有外部引用），设为返回默认值

## 不涉及的内容

- 不新增任何文件
- 不新增 Mixin
- 不改动 `SanityScreenEffects` 的 `desaturationStrength` / `vignetteStrength` 计算算法
- 不改动侦测仪 HUD
- 不改动理智数据层（`PlayerSanity` 等）

## 验证步骤

1. 编译通过：`gradlew build`
2. 游戏内测试：
   - 理智降到 50-74：暗角明显可见（隧道视野感）
   - 理智降到 25-49：暗角加深 + 画面变灰 + 随机幻听
   - 理智降到 0-24：全部效果加强 + ELDER_GUARDIAN 低鸣
   - 理智恢复到 75+：所有效果消失
3. FOV 不再抖动
