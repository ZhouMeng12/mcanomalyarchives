# -*- coding: utf-8 -*-
"""红伪云：守卫规则 + 独立自检脚本，并把它加进见闻录 UO-001 的解锁 id。"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

SMALL = "src/main/java/net/mcreator/mcanomalyarchives/entity/SmallStangeCloudEntity.java"
RED = "src/main/java/net/mcreator/mcanomalyarchives/entity/RedStangeCloudEntity.java"
RENDERER = "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/RedStangeCloudRenderer.java"
LISTENER = "src/main/java/net/mcreator/mcanomalyarchives/events/PlayerLookAtCornPoppyListener.java"
CODEX = "src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java"

# ---- 见闻录：UO-001 的解锁 id 加上红伪云 ----
s = io.open(CODEX, encoding="utf-8").read()
if "red_stange_cloud" not in s:
    s = s.replace("mcanomalyarchives:black_stange_cloud\"),",
                  "mcanomalyarchives:black_stange_cloud|mcanomalyarchives:red_stange_cloud\"),", 1)
    io.open(CODEX, "w", encoding="utf-8", newline="").write(s)
    print("OK 见闻录 UO-001 解锁 id 加上 red_stange_cloud")

P = "tools/mcreator-guard/manifest.json"
m = json.load(io.open(P, encoding="utf-8"))
have = {i["id"] for i in m["items"]}

NEW = [
    {
        "id": "red-cloud-element",
        "kind": "contains",
        "why": "红伪云的 MCreator 元素：与小伪云同模型同尺寸、贴图换成虞美人主色的红、不自然生成",
        "path": "elements/RedStangeCloud.mod.json",
        "fixHint": "重跑 python tools/cloud/attach_red_cloud.py",
        "mustContain": ['"_type": "livingentity"', '"mobModelName": "ModelStrangeCloud"',
                        '"mobModelTexture": "cloud_red.png"', '"modelWidth": 4.0', '"spawnThisMob": false'],
    },
    {
        "id": "red-cloud-registration",
        "kind": "contains",
        "why": "红伪云的实体注册行（生成区）",
        "path": "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java",
        "fixHint": "补回 RED_STANGE_CLOUD 的 register(...) 与 registerAttributes 里的 event.put(...)",
        "mustContain": ["RED_STANGE_CLOUD = register(\"red_stange_cloud\"",
                        "event.put(RED_STANGE_CLOUD.get(), RedStangeCloudEntity.createAttributes().build())"],
    },
    {
        "id": "red-cloud-renderer-registration",
        "kind": "contains",
        "why": "渲染器注册行（生成区）：丢了红伪云会看不见",
        "path": "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntityRenderers.java",
        "fixHint": "补回 RED_STANGE_CLOUD.get(), RedStangeCloudRenderer::new",
        "mustContain": ["RED_STANGE_CLOUD.get(), RedStangeCloudRenderer::new"],
    },
    {
        "id": "red-cloud-renderer",
        "kind": "contains",
        "why": "红伪云渲染器：红色贴图 + 与小伪云同一套缩放 + 半透明",
        "path": RENDERER,
        "fixHint": "从 git 恢复 client/renderer/RedStangeCloudRenderer.java",
        "mustContain": ["textures/entities/cloud_red.png", "RedStangeCloudEntity.RENDER_SCALE", "entityTranslucent"],
    },
    {
        "id": "red-cloud-entity",
        "kind": "contains",
        "why": "红伪云实体：继承小伪云（同一个模型尺寸与行为），但**不再吸虞美人** —— "
               "否则它碰到下一朵花就会再变一只红伪云，一朵花换一只云没完没了",
        "path": RED,
        "fixHint": "从 git 恢复 entity/RedStangeCloudEntity.java（extends SmallStangeCloudEntity + canAbsorbPoppy=false）",
        "mustContain": ["extends SmallStangeCloudEntity", "protected boolean canAbsorbPoppy()", "return false;"],
    },
    {
        "id": "small-cloud-absorbs-poppy",
        "kind": "contains",
        "why": "小伪云碰到虞美人 → 那朵虞美人变回普通虞美人（原版罂粟）、云自己变红伪云、运动状态原样交接。"
               "作者 2026-09-18 的原话：让小伪云（不是黑吸）碰到模组里的虞美人后会变红",
        "path": SMALL,
        "fixHint": "补回 canAbsorbPoppy() / findCornPoppy() / absorbPoppyAndTurnRed() / handOffMotionTo()",
        "mustContain": ["protected boolean canAbsorbPoppy()", "protected BlockPos findCornPoppy(",
                        "protected void absorbPoppyAndTurnRed(", "Blocks.POPPY.defaultBlockState()",
                        "McanomalyarchivesModEntities.RED_STANGE_CLOUD.get()", "handOffMotionTo(red)"],
    },
    {
        "id": "red-cloud-addiction",
        "kind": "contains",
        "why": "「看红伪云会上瘾」完全接在既有虞美人上瘾系统里（同一套 10 秒上瘾/升级/定身/丧命），"
               "漏了这几处就等于只会看不会上瘾，而且不报错",
        "path": LISTENER,
        "fixHint": "补回 isPlayerLookingAtRedCloud / isEntityLookingAtRedCloud / findRedCloudInView 与 watchingCloud 标记",
        "mustContain": ["isPlayerLookingAtRedCloud(", "isEntityLookingAtRedCloud(", "findRedCloudInView(",
                        "CLOUD_COS_LIMIT", "boolean watchingCloud;", "!state.watchingCloud"],
    },
    {
        "id": "red-cloud-assets",
        "kind": "exists",
        "why": "红贴图 + 刷怪蛋三件套",
        "fixHint": "重跑 python tools/cloud/make_red_texture.py 与 register_red_cloud.py",
        "exists": [
            "src/main/resources/assets/mcanomalyarchives/textures/entities/cloud_red.png",
            "src/main/resources/assets/mcanomalyarchives/textures/item/red_stange_cloud_spawn_egg_generated.png",
            "src/main/resources/assets/mcanomalyarchives/models/item/red_stange_cloud_spawn_egg.json",
            "src/main/resources/assets/mcanomalyarchives/items/red_stange_cloud_spawn_egg.json",
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
        key = "mcanomalyarchives:red_stange_cloud"
        if key not in item["mustContain"]:
            item["mustContain"].append(key)

with io.open(P, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(m, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("守卫规则 %d -> %d" % (len(m["items"]) - added, len(m["items"])))
