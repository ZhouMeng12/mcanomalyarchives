# -*- coding: utf-8 -*-
"""收尾守卫：把 small-cloud-absorbs-poppy 的契约从"生成红实体"改成"置变种标记"。"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)
P = "tools/mcreator-guard/manifest.json"
m = json.load(io.open(P, encoding="utf-8"))
for item in m["items"]:
    if item["id"] == "small-cloud-absorbs-poppy":
        item["mustContain"] = ["protected boolean canAbsorbPoppy()", "protected BlockPos findCornPoppy(",
                               "protected void absorbPoppy(", "Blocks.POPPY.defaultBlockState()", "setRed(true);"]
        item["why"] = ("小伪云碰到虞美人 → 那朵虞美人变回普通虞美人（原版罂粟）、云自己变红。"
                       "作者 2026-09-18 的原话：让小伪云（不是黑吸）碰到模组里的虞美人后会变红；"
                       "红伪云是同一个生物的变种（见 no-separate-red-entity），所以这里是置标记而不是生成新实体")
        item["fixHint"] = "补回 canAbsorbPoppy() / findCornPoppy() / absorbPoppy()（内部 setRed(true)）"
        print("OK small-cloud-absorbs-poppy 已改为变种契约")
with io.open(P, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(m, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
