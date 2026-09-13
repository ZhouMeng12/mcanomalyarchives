# -*- coding: utf-8 -*-
"""修掉根因：粉羊与伪云的 MCreator 元素定义里的默认值是错的。

背景：守卫里有两条规则（pink-sheep-registration / stange-cloud-registration）一直在事后修生成代码，
而 MCreator 每重新生成一次代码就会把这两处改回元素定义里的值——2026-09-13 一天内就复现了两次。

对照过元素字段与生成结果的关系（同一份 workspace 里可验证）：
    Qu.mod.json          mobSpawningType=ambient   → 生成的 MobCategory.AMBIENT
    SittingEnity.mod.json mobSpawningType=ambient  → MobCategory.AMBIENT
    Anbula.mod.json      mobSpawningType=creature  → MobCategory.CREATURE
    PinkSheep.mod.json   mobSpawningType=monster   → MobCategory.MONSTER   ← 错，应为 creature
    StangeCloud.mod.json mobSpawningType=creature  → MobCategory.CREATURE  ← 错，应为 ambient
所以 mobSpawningType 就是 MobCategory 的源头，改元素才是根因修复，守卫那两条从此不再需要反复还原。

要改的：
  PinkSheep   spawn monster → creature, modelWidth/Height 0.6x1.8 → 0.9x1.3（生成 sized(0.9f, 1.3f)）
  StangeCloud spawn creature → ambient, trackingRange 64 → 128（生成 setTrackingRange(128)）

注意：这两个元素 JSON 里有大小写只差一个字母的重复键（mx / Mx），
PowerShell 的 ConvertFrom-Json 会因为重复键直接报错，所以这里只做**定点文本替换**，
绝不用 json 往返 —— 那会顺手改掉 MCreator 的排版甚至丢键。
"""
import io
import os
import re
import shutil
import time

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

EDITS = {
    "elements/PinkSheep.mod.json": [
        ('"mobSpawningType": "monster"', '"mobSpawningType": "creature"'),
        ('"modelWidth": 0.6', '"modelWidth": 0.9'),
        ('"modelHeight": 1.8', '"modelHeight": 1.3'),
    ],
    "elements/StangeCloud.mod.json": [
        ('"mobSpawningType": "creature"', '"mobSpawningType": "ambient"'),
        ('"trackingRange": 64', '"trackingRange": 128'),
    ],
}

changed_any = False
for path, pairs in EDITS.items():
    raw = io.open(path, encoding="utf-8").read()
    original = raw
    for old, new in pairs:
        # 同一文件里可能有多个同名字段（比如别的子对象），必须唯一才敢改
        count = raw.count(old)
        if count == 0:
            if raw.count(new) >= 1:
                print("  -- %s 已经是 %s" % (os.path.basename(path), new))
                continue
            raise SystemExit("%s 找不到 %r" % (path, old))
        if count != 1:
            raise SystemExit("%s 里 %r 出现 %d 次，不敢盲改" % (path, old, count))
        raw = raw.replace(old, new)
        print("  OK %-28s %s  ->  %s" % (os.path.basename(path), old, new))
    if raw != original:
        shutil.copy(path, path + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
        io.open(path, "w", encoding="utf-8", newline="").write(raw)
        changed_any = True
        # 立刻回读校验：重复键文件不能用 json 解析，只能做定点断言
        check = io.open(path, encoding="utf-8").read()
        for _, new in pairs:
            if new not in check:
                raise SystemExit("%s 回读失败：%r 没写进去" % (path, new))

print("DONE", "有改动" if changed_any else "无需改动")
