# -*- coding: utf-8 -*-
"""给小伪云补上守卫规则（tools/mcreator-guard/manifest.json）。

背景：这个工作区里 MCreator 会重写生成区。手写的机制、注册行、词条一旦被它盖掉，
**往往不会报编译错误，只是功能悄悄没了**。所以每加一个特性就在这里留一条规则。

manifest.json 本身就是 json.dumps(indent=2, ensure_ascii=False) 的格式，
所以直接 load → 追加 → dump，diff 只会多出新增的条目。
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)
PATH = "tools/mcreator-guard/manifest.json"

ENTITY = "src/main/java/net/mcreator/mcanomalyarchives/entity/SmallStangeCloudEntity.java"
RENDERER = "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/SmallStangeCloudRenderer.java"
EMITTER = "src/main/java/net/mcreator/mcanomalyarchives/anomaly/cloud/SmallCloudEmitter.java"
PORTAL = "src/main/java/net/mcreator/mcanomalyarchives/anomaly/cloud/SmallCloudEmittingPortalBlock.java"
MOD_BLOCKS = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModBlocks.java"
MOD_ENTITIES = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java"
MOD_RENDERERS = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntityRenderers.java"
MOD_ITEMS = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java"
BIG_CLOUD = "src/main/java/net/mcreator/mcanomalyarchives/entity/StangeCloudEntity.java"
CODEX = "src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java"

NEW_ITEMS = [
    {
        "id": "small-cloud-element",
        "kind": "contains",
        "why": "小伪云的 MCreator 元素：尺寸/缩放/不自然生成都写在这里。元素一丢，MCreator 会按默认模板重写这个实体（0.6×1.8 的碰撞箱、允许自然生成）",
        "path": "elements/SmallStangeCloud.mod.json",
        "fixHint": "重跑 python tools/cloud/make_small_cloud_element.py 重新派生（源头是 elements/StangeCloud.mod.json）",
        "mustContain": [
            "\"_type\": \"livingentity\"",
            "\"mobModelName\": \"ModelStrangeCloud\"",
            "\"modelWidth\": 4.0",
            "\"modelHeight\": 0.67",
            "\"spawnThisMob\": false",
        ],
    },
    {
        "id": "small-cloud-entity-registration",
        "kind": "contains",
        "why": "小伪云在生成区注册表里的注册行与属性/生成规则钩子（生成区，MCreator 重生成会覆盖）",
        "path": MOD_ENTITIES,
        "fixHint": "补回 SMALL_STANGE_CLOUD 的 register(...) 行、registerAttributes 里的 event.put(...)、以及 registerSpawnPlacements 里的 SmallStangeCloudEntity.init(event);",
        "mustContain": [
            "SMALL_STANGE_CLOUD = register(\"small_stange_cloud\"",
            "event.put(SMALL_STANGE_CLOUD.get(), SmallStangeCloudEntity.createAttributes().build())",
            "SmallStangeCloudEntity.init(event);",
        ],
    },
    {
        "id": "small-cloud-renderer-registration",
        "kind": "contains",
        "why": "渲染器注册行（生成区）：丢了小伪云会变成不可见的实体（有碰撞、能被命令选中，但看不到）",
        "path": MOD_RENDERERS,
        "fixHint": "补回 event.registerEntityRenderer(McanomalyarchivesModEntities.SMALL_STANGE_CLOUD.get(), SmallStangeCloudRenderer::new);",
        "mustContain": ["SMALL_STANGE_CLOUD.get(), SmallStangeCloudRenderer::new"],
    },
    {
        "id": "small-cloud-portal-registration",
        "kind": "contains",
        "why": "门户方块注册行被换成会吐云的子类。**这条最阴**：回退成 CloudDemPortalBlock::new 不会报错、不会崩，只是门从此不再吐小伪云",
        "path": MOD_BLOCKS,
        "fixHint": "把 CLOUD_DEM_PORTAL 的注册行改成 SmallCloudEmittingPortalBlock::new（见 anomaly/cloud/SmallCloudEmittingPortalBlock.java）",
        "mustContain": ["SmallCloudEmittingPortalBlock::new"],
    },
    {
        "id": "small-cloud-entity-behaviour",
        "kind": "contains",
        "why": "小伪云的关键行为常量与朝向约定：出场方向靠朝向在客户端还原（headingFromYaw），缩放必须是 2.6667（短横边 2 格），寿命 60 秒",
        "path": ENTITY,
        "fixHint": "从 git 恢复 src/main/java/net/mcreator/mcanomalyarchives/entity/SmallStangeCloudEntity.java（数值推导见 docs/design/uo-001-small-cloud.md）",
        "mustContain": [
            "RENDER_SCALE = 2.6667f",
            "LIFETIME_TICKS = 1200",
            "public static float yawFor(double dx, double dz)",
            "public static Vec3 headingFromYaw(float yaw)",
            "instanceof SmallStangeCloudEntity",
        ],
    },
    {
        "id": "small-cloud-renderer-scale",
        "kind": "contains",
        "why": "渲染缩放写在渲染器里（元素里的 visualScale 留 1.0）。丢了它小伪云会变成 1.5 格宽的模型，跟大伪云的比例对不上",
        "path": RENDERER,
        "fixHint": "把 scale() 改回 poseStack.scale(SmallStangeCloudEntity.RENDER_SCALE, ...) 并指向 ModelStrangeCloud",
        "mustContain": ["SmallStangeCloudEntity.RENDER_SCALE", "ModelStrangeCloud"],
    },
    {
        "id": "small-cloud-portal-random-tick",
        "kind": "contains",
        "why": "「只要门存在就吐」靠的就是门方块自己的随机刻（不调 super，保持门里不刷僵尸猪灵）",
        "path": PORTAL,
        "fixHint": "补回 extends CloudDemPortalBlock 与 randomTick 里的 SmallCloudEmitter.tryEmit(level, pos, state, random);",
        "mustContain": ["extends CloudDemPortalBlock", "SmallCloudEmitter.tryEmit(level, pos, state, random);"],
    },
    {
        "id": "small-cloud-emitter-logic",
        "kind": "contains",
        "why": "吐云的节奏与并发上限（每门约 45 秒一只、最多 2 只），以及『出场方向只通过朝向传递』这条免网络包的约定",
        "path": EMITTER,
        "fixHint": "从 git 恢复 src/main/java/net/mcreator/mcanomalyarchives/anomaly/cloud/SmallCloudEmitter.java",
        "mustContain": ["EMIT_ONE_IN = 12", "MAX_PER_PORTAL = 2", "yawFor(exit.getStepX(), exit.getStepZ())"],
    },
    {
        "id": "small-cloud-damage-exclusion",
        "kind": "contains",
        "why": "大伪云的伤害盒要排除小伪云，否则门吐出来的小云会被旁边的大云每 tick 打",
        "path": BIG_CLOUD,
        "fixHint": "在 applyCloudDamage 的排除判断里加回 || entity instanceof SmallStangeCloudEntity",
        "mustContain": ["instanceof net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity"],
    },
    {
        "id": "small-cloud-spawn-egg",
        "kind": "contains",
        "why": "小伪云刷怪蛋的注册行（生成区）：作者测试时用它直接放一只，不必真去搭门",
        "path": MOD_ITEMS,
        "fixHint": "补回 SMALL_STANGE_CLOUD_SPAWN_EGG 的 register(...) 行（贴图由 tools/cloud/make_egg_assets.py 生成）",
        "mustContain": ["SMALL_STANGE_CLOUD_SPAWN_EGG = REGISTRY.register(\"small_stange_cloud_spawn_egg\""],
    },
    {
        "id": "small-cloud-assets",
        "kind": "exists",
        "why": "刷怪蛋的贴图/模型/物品定义三件套：任一侧丢了刷怪蛋就是紫黑方块",
        "fixHint": "重跑 python tools/cloud/make_egg_assets.py",
        "exists": [
            "src/main/resources/assets/mcanomalyarchives/textures/item/small_stange_cloud_spawn_egg_generated.png",
            "src/main/resources/assets/mcanomalyarchives/models/item/small_stange_cloud_spawn_egg.json",
            "src/main/resources/assets/mcanomalyarchives/items/small_stange_cloud_spawn_egg.json",
        ],
    },
    {
        "id": "codex-uo001-multi-id",
        "kind": "contains",
        "why": "UO-001 的解锁 id 支持用 | 分隔多个：见过小伪云也算解明 UO-001（作者 2026-09-16：小伪云不单独开条目）",
        "path": CODEX,
        "fixHint": "把 UO-001 那行改回 \"mcanomalyarchives:stange_cloud|mcanomalyarchives:small_stange_cloud\"，并保留 scan() 里按 | 拆分的逻辑",
        "mustContain": [
            "mcanomalyarchives:stange_cloud|mcanomalyarchives:small_stange_cloud",
            "entry.match().split(",
        ],
    },
    {
        "id": "cloud-dem-dimension-id",
        "kind": "noMatch",
        "why": "云之维度的 id 是 cloud_dem。历史上两处写成 clouddem（StangeCloudSpawnHandler / StangeCloudEntity），后果是云之维度里一只伪云都不生成 —— 这次一并修了，别再写回去",
        "fixHint": "把 \"clouddem\" 改回 \"cloud_dem\"（只匹配带引号的字符串字面量，注释里提到这个词不算）",
        "glob": "*.java",
        "searchRoot": "src/main/java",
        "pattern": "clouddem\"",
    },
]

LANG_KEYS = [
    "\"entity.mcanomalyarchives.small_stange_cloud\"",
    "\"item.mcanomalyarchives.small_stange_cloud_spawn_egg\"",
]

with io.open(PATH, encoding="utf-8") as fh:
    manifest = json.load(fh)

have = {item["id"] for item in manifest["items"]}
added = 0
for item in NEW_ITEMS:
    if item["id"] in have:
        print("-- 已有规则", item["id"])
        continue
    manifest["items"].append(item)
    added += 1

# 词条：塞进 expectAll，check 才会因为"缺键"失败，apply 才会把 canonical 里的键插进 lang
for item in manifest["items"]:
    if item["id"] in ("lang-zh-extra", "lang-en-extra"):
        for key in LANG_KEYS:
            if key not in item["expectAll"]:
                item["expectAll"].append(key)

with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(manifest, fh, ensure_ascii=False, indent=2)
    fh.write("\n")

print("新增 %d 条规则，规则总数 %d" % (added, len(manifest["items"])))
