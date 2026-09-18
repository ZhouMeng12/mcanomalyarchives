# -*- coding: utf-8 -*-
"""从大伪云元素派生小伪云元素（elements/SmallStangeCloud.mod.json）。

改的就这几项，其余全部照抄（模型/贴图/免疫/AI/创造标签等）：
  mobName        Small Stange Cloud
  modelWidth     4.0   （MCreator 的碰撞箱是正方形，按大伪云的先例取"长边"）
  modelHeight    0.67
  visualScale    1.0   ← 元素里留 1.0（MCreator 界面是整数更稳）；真正的缩放写在
                         手写的 SmallStangeCloudRenderer 里（2.6667，短横边=2格）
  spawnThisMob   false ← 它只从传送门里出来，不进自然生成表、不生成 biome_modifier
  trackingRange  64
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "elements/StangeCloud.mod.json")
DST = os.path.join(ROOT, "elements/SmallStangeCloud.mod.json")

with io.open(SRC, encoding="utf-8") as fh:
    doc = json.load(fh)

assert doc["_type"] == "livingentity", doc["_type"]
assert "_fv" in doc, "元素文件缺少 _fv（MCreator 版本号）"

d = doc["definition"]
assert d["mobModelName"] == "ModelStrangeCloud", d["mobModelName"]
assert d["mobModelTexture"] == "texture.png", d["mobModelTexture"]

before = {k: d.get(k) for k in ("mobName", "modelWidth", "modelHeight", "visualScale", "spawnThisMob", "trackingRange")}

d["mobName"] = "Small Stange Cloud"
d["modelWidth"] = 4.0
d["modelHeight"] = 0.67
d["visualScale"] = {"fixedValue": 1.0}
d["spawnThisMob"] = False
d["trackingRange"] = 64

# 刷怪蛋：创造标签沿用 StrangeRecord；颜色 -1/-1（贴图自带颜色，与伪云蛋同为自定义贴图模式）
d["hasSpawnEgg"] = True
d["creativeTabs"] = [{"value": "CUSTOM:StrangeRecord"}]

with io.open(DST, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(doc, fh, ensure_ascii=False, indent=2)
    fh.write("\n")

print("写出:", os.path.relpath(DST, ROOT))
print("改前:", json.dumps(before, ensure_ascii=False))
after = {k: d.get(k) for k in before}
print("改后:", json.dumps(after, ensure_ascii=False))
print("模型/贴图:", d["mobModelName"], "/", d["mobModelTexture"])
print("免疫抽样:", {k: d[k] for k in ("immuneToPlayer", "immuneToExplosion", "disableCollisions", "flyingMob", "mobSpawningType")})
