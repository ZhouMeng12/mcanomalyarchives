# -*- coding: utf-8 -*-
"""黑吸伪云：守卫规则 + 独立自检脚本。

守卫规则（manifest.json）盯的是"MCreator 重写生成区时不会报错、只是功能消失"那类漂移；
自检脚本（tools/cloud/test_black_cloud.py）盯的是数值与行为契约（与小伪云同尺寸、乌黑而非纯黑、
无伤害无染水、两道闸门、吞完必消散）。
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

ENTITY = "src/main/java/net/mcreator/mcanomalyarchives/entity/BlackStangeCloudEntity.java"
RENDERER = "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/BlackStangeCloudRenderer.java"
EMITTER = "src/main/java/net/mcreator/mcanomalyarchives/anomaly/cloud/SmallCloudEmitter.java"
CONFIG = "src/main/java/net/mcreator/mcanomalyarchives/config/ServerGameplayConfig.java"
PASS_MIXIN = "src/main/java/net/mcreator/mcanomalyarchives/mixin/CloudPortalPassMixin.java"

P = "tools/mcreator-guard/manifest.json"
m = json.load(io.open(P, encoding="utf-8"))
have = {i["id"] for i in m["items"]}

NEW = [
    {
        "id": "black-cloud-element",
        "kind": "contains",
        "why": "黑吸伪云的 MCreator 元素：与小伪云同模型同尺寸、贴图换乌黑、不自然生成。元素一丢，"
               "MCreator 会按默认模板重写这个实体（0.6×1.8 的碰撞箱、允许自然生成）",
        "path": "elements/BlackStangeCloud.mod.json",
        "fixHint": "重跑 python tools/cloud/make_black_cloud_element.py",
        "mustContain": ['"_type": "livingentity"', '"mobModelName": "ModelStrangeCloud"',
                        '"mobModelTexture": "cloud_black.png"', '"modelWidth": 4.0',
                        '"modelHeight": 0.67', '"spawnThisMob": false'],
    },
    {
        "id": "black-cloud-registration",
        "kind": "contains",
        "why": "黑吸伪云的实体注册行（生成区，MCreator 重生成时按元素自己产出，这条防被改坏）",
        "path": "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java",
        "fixHint": "补回 BLACK_STANGE_CLOUD 的 register(...) 行与 registerAttributes 里的 event.put(...)",
        "mustContain": ["BLACK_STANGE_CLOUD = register(\"black_stange_cloud\"",
                        "event.put(BLACK_STANGE_CLOUD.get(), BlackStangeCloudEntity.createAttributes().build())"],
    },
    {
        "id": "black-cloud-renderer-registration",
        "kind": "contains",
        "why": "渲染器注册行（生成区）：丢了黑吸伪云会变成看不见的实体",
        "path": "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntityRenderers.java",
        "fixHint": "补回 BLACK_STANGE_CLOUD.get(), BlackStangeCloudRenderer::new",
        "mustContain": ["BLACK_STANGE_CLOUD.get(), BlackStangeCloudRenderer::new"],
    },
    {
        "id": "black-cloud-renderer",
        "kind": "contains",
        "why": "黑吸伪云的渲染器：乌黑贴图 + 与小伪云同一套缩放 + 半透明",
        "path": RENDERER,
        "fixHint": "从 git 恢复 client/renderer/BlackStangeCloudRenderer.java",
        "mustContain": ["textures/entities/cloud_black.png", "BlackStangeCloudEntity.RENDER_SCALE", "entityTranslucent"],
    },
    {
        "id": "black-cloud-behaviour",
        "kind": "contains",
        "why": "吞方块的关键契约：接触判定 → 两道闸门 → 吞一口（无掉落）→ 自己消散"
               "（作者：碰到方块后消失；范围要大点）",
        "path": ENTITY,
        "fixHint": "从 git 恢复 entity/BlackStangeCloudEntity.java（数值推导见 docs/design/uo-001-black-cloud.md）",
        "mustContain": ["ERASE_RADIUS = 6.0D", "ERASE_MIN = 12", "ERASE_MAX = 24",
                        "level.destroyBlock(candidates.get(i), false)", "RULE_MOBGRIEFING",
                        "ServerGameplayConfig.blackCloudTerrainDamage()", "this.discard();",
                        "protected void tickCloudEffects()", "isProtected("],
    },
    {
        "id": "black-cloud-no-damage",
        "kind": "noMatch",
        "why": "反向检查：黑吸伪云**只吞方块、不伤害生物、不染水**（作者 2026-09-18 定），"
               "别把父类那套伤害盒/染水逻辑搬回来",
        "fixHint": "删掉 applyCloudDamage / convertLavaAndWaterToCloudBlocks 的调用或复制",
        "glob": "BlackStangeCloudEntity.java",
        "searchRoot": "src/main/java/net/mcreator/mcanomalyarchives/entity",
        "pattern": "applyCloudDamage|convertLavaAndWaterToCloudBlocks|DAMAGE_PER_HIT",
    },
    {
        "id": "black-cloud-emitter-chance",
        "kind": "contains",
        "why": "传送门偶尔吐黑的（1/6）：这条丢了就永远只有普通小伪云（不报错）",
        "path": EMITTER,
        "fixHint": "补回 BLACK_ONE_IN = 6 与 BLACK_STANGE_CLOUD 的选取分支",
        "mustContain": ["BLACK_ONE_IN = 6", "McanomalyarchivesModEntities.BLACK_STANGE_CLOUD.get()"],
    },
    {
        "id": "black-cloud-config-toggle",
        "kind": "contains",
        "why": "配置开关（与 mobGriefing 取与）：关掉后黑吸伪云只消散、不吞方块",
        "path": CONFIG,
        "fixHint": "补回 BLACK_CLOUD_TERRAIN_DAMAGE 与 blackCloudTerrainDamage()",
        "mustContain": ["BLACK_CLOUD_TERRAIN_DAMAGE", "public static boolean blackCloudTerrainDamage()",
                        "blackCloudTerrainDamage"],
    },
    {
        "id": "black-cloud-portal-pass",
        "kind": "contains",
        "why": "黑吸伪云也要能过云室门（它是小伪云的子类，被同一个 instanceof 覆盖）",
        "path": PASS_MIXIN,
        "fixHint": "确认 CloudPortalPassMixin 放行的是 SmallStangeCloudEntity 及其子类",
        "mustContain": ["instanceof SmallStangeCloudEntity"],
    },
    {
        "id": "black-cloud-assets",
        "kind": "exists",
        "why": "乌黑贴图 + 刷怪蛋三件套：任一侧丢了云就是紫黑方块或看不见",
        "fixHint": "重跑 python tools/cloud/make_black_texture.py 与 make_black_egg_assets.py",
        "exists": [
            "src/main/resources/assets/mcanomalyarchives/textures/entities/cloud_black.png",
            "src/main/resources/assets/mcanomalyarchives/textures/item/black_stange_cloud_spawn_egg_generated.png",
            "src/main/resources/assets/mcanomalyarchives/models/item/black_stange_cloud_spawn_egg.json",
            "src/main/resources/assets/mcanomalyarchives/items/black_stange_cloud_spawn_egg.json",
        ],
    },
]
added = 0
for item in NEW:
    if item["id"] not in have:
        m["items"].append(item)
        added += 1

for item in m["items"]:
    if item["id"] == "codex-uo001-multi-id":
        key = "mcanomalyarchives:black_stange_cloud"
        if key not in item["mustContain"]:
            item["mustContain"].append(key)
        item["mustContain"] = [x for x in item["mustContain"] if x != "mcanomalyarchives:stange_cloud|mcanomalyarchives:small_stange_cloud"]
        item["fixHint"] = "把 UO-001 那行改回 stange_cloud|small_stange_cloud|black_stange_cloud 三个 id"

with io.open(P, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(m, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("守卫规则 %d → %d" % (len(m["items"]) - added, len(m["items"])))
