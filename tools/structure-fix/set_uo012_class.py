# -*- coding: utf-8 -*-
"""把 UO-012 幸运粉羊的项目等级从自定的 R3 改为作者指定的 R4（中英 + 基线 + CSV + 代码注释）"""
import io, os, sys

OLD_ZH = "R3（本模组自定）"
NEW_ZH = "R4"
OLD_EN = "R3 (set by this mod)"
NEW_EN = "R4"

targets = [
    ("src/main/resources/assets/mcanomalyarchives/lang/zh_cn.json", [(OLD_ZH, NEW_ZH)]),
    ("src/main/resources/assets/mcanomalyarchives/lang/en_us.json", [(OLD_EN, NEW_EN)]),
    ("tools/mcreator-guard/canonical/zh_cn.extra.txt", [(OLD_ZH, NEW_ZH)]),
    ("tools/mcreator-guard/canonical/en_us.extra.txt", [(OLD_EN, NEW_EN)]),
    ("zhcn.csv", [(OLD_ZH, NEW_ZH), (OLD_EN, NEW_EN)]),
    # 代码注释里的说明
    ("src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java",
     [("UO-012 是作者按 Sven 同名视频实现的，wiki 上还没有它的页面，等级由本模组自定。",
       "UO-012 是作者按 Sven 同名视频实现的，wiki 上还没有它的页面，项目等级 R4 由作者指定。")]),
    ("src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java",
     [("UO-012 幸运粉羊是本模组自己按 Sven 同名视频实现的，wiki 上还没有它的页面，等级由本模组自定。",
       "UO-012 幸运粉羊是本模组自己按 Sven 同名视频实现的，wiki 上还没有它的页面，项目等级 R4 由作者指定。")]),
]

for path, pairs in targets:
    if not os.path.exists(path):
        print("跳过（不存在）", path)
        continue
    s = io.open(path, encoding="utf-8", newline="").read()
    before = s
    for old, new in pairs:
        s = s.replace(old, new)
    if s != before:
        io.open(path, "w", encoding="utf-8", newline="").write(s)
        print("已更新", path)
    else:
        print("无变化", path)

# 校验：确认 uo012.level 已是 R4
import json
for lg in ("zh_cn", "en_us"):
    p = "src/main/resources/assets/mcanomalyarchives/lang/%s.json" % lg
    d = json.load(io.open(p, encoding="utf-8"))
    print("  %s -> %s" % (p, d.get("codex.mcanomalyarchives.uo012.level")))
