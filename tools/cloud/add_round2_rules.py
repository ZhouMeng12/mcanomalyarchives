# -*- coding: utf-8 -*-
"""给这一轮改动（不换向 / 频率降低 / 能过门 / 半透明）补守卫规则与自检。

注意：中文串里不要出现英文双引号（上次就因为这个脚本自己语法错、整轮都没生效）。
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

ENTITY = "src/main/java/net/mcreator/mcanomalyarchives/entity/SmallStangeCloudEntity.java"
BIG_RENDERER = "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/StangeCloudRenderer.java"
SMALL_RENDERER = "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/SmallStangeCloudRenderer.java"
PASS_MIXIN = "src/main/java/net/mcreator/mcanomalyarchives/mixin/CloudPortalPassMixin.java"

P = "tools/mcreator-guard/manifest.json"
m = json.load(io.open(P, encoding="utf-8"))
have = {i["id"] for i in m["items"]}

for item in m["items"]:
    if item["id"] == "small-cloud-portal-mixin-registered":
        if '"CloudPortalPassMixin"' not in item["mustContain"]:
            item["mustContain"].append('"CloudPortalPassMixin"')
        item["why"] = ("mixin 配置里必须同时登记 CloudPortalEmitMixin（吐云）与 CloudPortalPassMixin（让小伪云能过门）："
                       "少了它们 mixin 根本不加载，门不吐云 / 小伪云过不去，而且都不报错")

NEW = [
    {
        "id": "small-cloud-portal-pass-mixin",
        "kind": "contains",
        "why": "小伪云自己能过云室门。门原本的条件是『能跨维度 + 带云化效果』（给玩家用的），这里给条件开个后门。"
               "注入条件类而不是给云挂云化效果：云化效果自带灰白贴图、失去 AI 那套表现，挂在云上会把它变成一坨灰的",
        "path": PASS_MIXIN,
        "fixHint": "从 git 恢复 mixin/CloudPortalPassMixin.java（并确认 mixins.json 里登记了它）",
        "mustContain": [
            "@Mixin(CloudDemChuanSongMenTongGuoTiaoJianProcedure.class)",
            "cir.setReturnValue(true);",
            "instanceof SmallStangeCloudEntity",
        ],
    },
    {
        "id": "small-cloud-portal-cooldown",
        "kind": "contains",
        "why": "过门冷却：原版传送时会拿 getDimensionChangingDelay() 当冷却，小伪云把它设成 1400（比寿命 1200 还长），"
               "一只云一生只过一次门，不会在两个门之间反复弹",
        "path": ENTITY,
        "fixHint": "补回 public int getDimensionChangingDelay() 覆盖与 PORTAL_COOLDOWN_TICKS = 1400",
        "mustContain": ["public int getDimensionChangingDelay()", "PORTAL_COOLDOWN_TICKS = 1400"],
    },
    {
        "id": "small-cloud-straight-line",
        "kind": "noMatch",
        "why": "反向检查：小伪云**永远不换向**（作者 2026-09-18：都不换向吧），只沿出场那个轴一直平移。"
               "别把巡航换向那套（pickHeading / DRIFT_TURN_INTERVAL）加回来",
        "fixHint": "删掉换向逻辑，只保留出生时由朝向还原的 heading 与匀速平移",
        "glob": "SmallStangeCloudEntity.java",
        "searchRoot": "src/main/java/net/mcreator/mcanomalyarchives/entity",
        "pattern": "pickHeading|DRIFT_TURN_INTERVAL",
    },
    {
        "id": "cloud-family-translucent-big",
        "kind": "contains",
        "why": "伪云系列的半透明（作者 2026-09-18）：大伪云渲染器要走 translucent 渲染类型",
        "path": BIG_RENDERER,
        "fixHint": "把 getRenderType 覆盖补回来，返回 RenderType.entityTranslucent(getTextureLocation(entity))",
        "mustContain": ["entityTranslucent"],
    },
    {
        "id": "cloud-family-translucent-small",
        "kind": "contains",
        "why": "伪云系列的半透明：小伪云渲染器要走 translucent 渲染类型",
        "path": SMALL_RENDERER,
        "fixHint": "把 getRenderType 覆盖补回来，返回 RenderType.entityTranslucent(getTextureLocation(entity))",
        "mustContain": ["entityTranslucent"],
    },
]
for item in NEW:
    if item["id"] not in have:
        m["items"].append(item)
        print("+ 规则", item["id"])

with io.open(P, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(m, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("规则总数", len(m["items"]))

T = "tools/cloud/test_small_cloud.py"
s = io.open(T, encoding="utf-8").read()

old = 'check_true("没有任何上下浮动（作者：不要上下飘）",\n           "BOB" not in entity and "LAUNCH_LIFT" not in entity)'
new = old + '''
check_true("永远不换向：没有换向逻辑（作者 2026-09-18）",
           "pickHeading" not in entity and "DRIFT_TURN_INTERVAL" not in entity)
check_true("过门冷却比寿命长 → 一生只过一次门",
           "public int getDimensionChangingDelay()" in entity and "PORTAL_COOLDOWN_TICKS = 1400" in entity)
check_true("小伪云自己能过门（注入通行条件类，不动生成区）",
           "@Mixin(CloudDemChuanSongMenTongGuoTiaoJianProcedure.class)" in
           read("src/main/java/net/mcreator/mcanomalyarchives/mixin/CloudPortalPassMixin.java")
           and '"CloudPortalPassMixin"' in read("src/main/resources/mcanomalyarchives.mixins.json"))
for _label, _path in (("大伪云", "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/StangeCloudRenderer.java"),
                      ("小伪云", "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/SmallStangeCloudRenderer.java")):
    check_true("%s渲染器走半透明渲染类型" % _label, "entityTranslucent" in read(_path))'''
assert old in s, "找不到锚点"
s = s.replace(old, new)

old2 = "check_true(\"平均出货间隔在'偶尔'的合理区间（15~120 秒）\", 15.0 <= seconds <= 120.0,"
new2 = "check_true(\"平均出货间隔在'偶尔'的合理区间（60~180 秒）\", 60.0 <= seconds <= 180.0,"
assert old2 in s, "找不到间隔断言"
s = s.replace(old2, new2)
io.open(T, "w", encoding="utf-8", newline="").write(s)
print("自检脚本已更新")
