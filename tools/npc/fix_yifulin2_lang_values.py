# -*- coding: utf-8 -*-
"""把 Yifulin2（灰色伊芙琳）的两条词条**值**写进 lang 产物。

guard 的 insertBefore 只负责"插入缺失的键"，不会更新已存在的值 ——
而 MCreator 生成时已经写了 `"entity.mcanomalyarchives.yifulin_2": "Yifulin"`，
所以这里要单独把值改成与 canonical 一致的「灰色伊芙琳 / Gray Yifulin」。
"""
import io
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

KEYS = {
    "zh_cn": [("entity.mcanomalyarchives.yifulin_2", "灰色伊芙琳"),
              ("item.mcanomalyarchives.yifulin_2_spawn_egg", "灰色伊芙琳刷怪蛋")],
    "en_us": [("entity.mcanomalyarchives.yifulin_2", "Gray Yifulin"),
              ("item.mcanomalyarchives.yifulin_2_spawn_egg", "Gray Yifulin Spawn Egg")],
}

for lg, pairs in KEYS.items():
    path = "src/main/resources/assets/mcanomalyarchives/lang/%s.json" % lg
    text = io.open(path, encoding="utf-8").read()
    n = 0
    for key, value in pairs:
        pat = re.compile(r'^(\s*"' + re.escape(key) + r'"\s*:\s*)("(?:[^"\\]|\\.)*")(,?)\s*$', re.M)
        if pat.search(text):
            text = pat.sub(lambda m: m.group(1) + json.dumps(value, ensure_ascii=False) + m.group(3), text)
            n += 1
    json.loads(text)  # 严格校验
    io.open(path, "w", encoding="utf-8", newline="").write(text)
    print("%s：改写 %d 条" % (os.path.basename(path), n))
