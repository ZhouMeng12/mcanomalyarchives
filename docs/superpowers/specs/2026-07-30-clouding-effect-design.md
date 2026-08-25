# 云化效果系统设计规格

## 摘要

当生物获得云化（CLOUDING）效果时：
- **贴图**变为灰度近白色（保留原始纹理细节）
- **非玩家实体**失去 AI，沿 X/Z 轴缓慢飘移（类似伪云 StrangeCloudEntity）
- **玩家**仅视觉变白，保持正常操控

此外，云水瓶右键非敌对生物可施加 3 分钟云化效果。

---

## 当前状态

| 文件 | 现状 |
|------|------|
| `potion/CloudingMobEffect.java` | 空壳，仅颜色 `-8740680`，无 tick 逻辑 |
| `entity/StangeCloudEntity.java` | 伪云实体，沿 X/Z 飘移 + 飞行控制 |
| `item/CloudWaterButtleItem.java` | 食物类 Item，饮用无云化效果 |
| shader 目录 | 不存在 |

---

## 改动清单

### 1. `potion/CloudingMobEffect.java` — 添加效果逻辑

**修改**：重写 `applyEffectTick()` 和 `shouldApplyEffectTickThisTick()`

```
shouldApplyEffectTickThisTick(duration, amplifier):
  return true   // 每 tick 生效

applyEffectTick(entity, amplifier):
  if entity is Player → return（玩家仅视觉效果）
  // 非玩家
  entity.setNoAi(true)
  entity.setNoGravity(true)
  // 沿 facesDirection 在 X/Z 轴缓慢飘移
  drift = entity.getLookAngle() * 0.03
  entity.setDeltaMovement(drift.x, 0, drift.z)
```

**依赖**：`LivingEntity`、`ServerPlayer`（排除用 `instanceof`）

---

### 2. Shader 文件 — 灰度变白

#### `assets/strangerecord/shaders/core/rendertype_clouding.json`

```json
{
  "blend": { "func": "add", "srcrgb": "srcalpha", "dstrgb": "1-srcalpha" },
  "vertex": "rendertype_clouding",
  "fragment": "rendertype_clouding",
  "attributes": ["Position", "Color", "UV0", "UV1", "UV2", "Normal"],
  "samplers": [{ "name": "Sampler0" }],
  "uniforms": [
    { "name": "ModelViewMat", "type": "mat4", "count": 1, "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "ProjMat", "type": "mat4", "count": 1, "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "ColorModulator", "type": "float", "count": 4, "values": [1,1,1,1] },
    { "name": "Light0_Direction", "type": "float", "count": 3, "values": [0,0,0] },
    { "name": "Light1_Direction", "type": "float", "count": 3, "values": [0,0,0] },
    { "name": "FogStart", "type": "float", "count": 1, "values": [0] },
    { "name": "FogEnd", "type": "float", "count": 1, "values": [1] },
    { "name": "FogColor", "type": "float", "count": 4, "values": [0,0,0,0] },
    { "name": "GameTime", "type": "float", "count": 1, "values": [0] }
  ]
}
```

#### `assets/strangerecord/shaders/core/rendertype_clouding.vsh`

标准顶点着色器（pass-through，与 Minecraft 内置 `rendertype_entity_cutout.vsh` 相同）

#### `assets/strangerecord/shaders/core/rendertype_clouding.fsh`

片段着色器核心逻辑：
```glsl
#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec3 normal;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) discard;
    
    // 灰度 = 0.299R + 0.587G + 0.114B
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    // 亮化到接近白色
    float bright = mix(gray, 1.0, 0.7);
    
    fragColor = vec4(vec3(bright), color.a) * vertexColor * ColorModulator;
}
```

---

### 3. `client/renderer/CloudingRenderLayer.java` — 渲染层

**新建**：RenderLayer 实现，使用自定义 `RenderType` 引用云化 shader

```java
public class CloudingRenderLayer<T extends LivingEntity, S extends LivingEntityRenderState>
    extends RenderLayer<T, S> {
    
    private static final RenderType CLOUDING_RENDERTYPE = 
        RenderType.create("clouding", 
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(
                    () -> GameRenderer.getRendertypeCloudingShader()))  // 自定义 shader
                .setTextureState(new RenderStateShard.TextureStateShard(
                    ResourceLocation...))  // 实体自身纹理
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(false)
        );
}
```

**注册**：通过 `EntityRenderersEvent.AddLayers` 事件注册到 `LivingEntityRenderer`

**备选方案**（shader 不可行时）：
不创建 shader，使用纯白色 `TextureAtlas` 位置 + 标准 `RenderType.entityCutoutNoCull()`，实现纯白轮廓。

---

### 4. `item/CloudWaterButtleItem.java` — 右键施云化

**修改**：重写 `interactLivingEntity()`

```java
@Override
public InteractionResult interactLivingEntity(ItemStack stack, Player player, 
        LivingEntity target, InteractionHand hand) {
    if (target instanceof Mob mob && mob.isAggressive()) {
        return InteractionResult.PASS;  // 敌对生物不施加
    }
    if (!player.level().isClientSide()) {
        target.addEffect(new MobEffectInstance(
            StrangerecordModMobEffects.CLOUDING, 3600, 0));  // 3 分钟
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            player.addItem(new ItemStack(Items.GLASS_BOTTLE));
        }
    }
    return InteractionResult.SUCCESS;
}
```

---

### 5. 效果结束恢复 — `CloudingEffectHandler.java`

**新建**：事件监听类

```java
@EventBusSubscriber
public class CloudingEffectHandler {
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() == StrangerecordModMobEffects.CLOUDING.get() 
            && event.getEntity() instanceof Mob mob) {
            mob.setNoAi(false);
            mob.setNoGravity(false);
        }
    }
}
```

---

### 6. `StrangerecordMod.java` — Shader 注册

在模组主类中添加：
```java
@SubscribeEvent
public static void registerShaders(RegisterShadersEvent event) {
    // 注册 rendertype_clouding shader
}
```

或在 `client` 包中独立的客户端事件监听器中注册。

---

## 边界条件

| 场景 | 行为 |
|------|------|
| 玩家获得云化 | 仅贴图变白，正常操控 |
| 非敌对生物获得云化 | 失 AI + X/Z 飘移 + 贴图变白 |
| 云水瓶右键敌对怪物 | 不施加效果 |
| 云化自然到期 | AI 自动恢复 |
| /effect clear | AI 自动恢复 |
| 云化实体死亡 | 自然清理 |
| shader 加载失败 | 回退到纯白轮廓方案 |

---

## 验证

1. 饮用云水 → 检查贴图变白但可正常移动
2. 云水瓶右键牛/羊 → 确认变白 + 失 AI + 飘移
3. 云水瓶右键僵尸 → 确认不生效
4. 等待 3 分钟 → 确认 AI 恢复、颜色恢复
5. /effect clear → 确认立即恢复
