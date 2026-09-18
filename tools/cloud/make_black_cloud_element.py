# -*- coding: utf-8 -*-
"""从小伪云元素派生黑吸伪云元素（elements/BlackStangeCloud.mod.json）。

与小伪云**同模型同尺寸**，只把贴图换成乌黑那张，其余照抄：
  mobName       Black Devouring Cloud
  mobModelName  ModelStrangeCloud      ← 同一个模型（作者：模型用小伪云模型）
  mobModelTexture  cloud_black.png     ← 只有这一项与大小伪云不同
  modelWidth 4.0 / modelHeight 0.67 / visualScale 1.0（真缩放写在手写渲染器里）
  spawnThisMob false（只从传送门或刷怪蛋出现，不进自然生成表）
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "elements/SmallStangeCloud.mod.json")
DST = os.path.join(ROOT, "elements/BlackStangeCloud.mod.json")

with io.open(SRC, encoding="utf-8") as fh:
    doc = json.load(fh)

assert doc["_type"] == "livingentity", doc["_type"]
d = doc["definition"]
assert d["mobModelName"] == "ModelStrangeCloud", d["mobModelName"]

before = {k: d.get(k) for k in ("mobName", "mobModelTexture", "modelWidth", "modelHeight", "spawnThisMob")}
d["mobName"] = "Black Devouring Cloud"
d["mobModelTexture"] = "cloud_black.png"
d["hasSpawnEgg"] = True
d["creativeTabs"] = [{"value": "CUSTOM:StrangeRecord"}]

with io.open(DST, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(doc, fh, ensure_ascii=False, indent=2)
    fh.write("\n")

print("写出 elements/BlackStangeCloud.mod.json")
print("改前:", json.dumps(before, ensure_ascii=False))
print("改后:", json.dumps({k: d.get(k) for k in before}, ensure_ascii=False))
