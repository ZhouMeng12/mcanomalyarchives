# -*- coding: utf-8 -*-
"""自检：被命名的生物该继承哪些"性质"（太阳点燃 / 药水反转 / 火焰免疫 / 怕水）。

直接**从 Java 源码里解析**那三张表，再验期望 —— 不手抄，改完表再跑一次就知道有没有写错。
Java 侧：anomaly/nametag/CreatureTraits.java
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

SRC = "src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/CreatureTraits.java"
text = io.open(SRC, encoding="utf-8").read()

FAILED = []


def id_set(name):
    m = re.search(r"Set<String> " + name + r" = Set\.of\((.*?)\);", text, re.S)
    if not m:
        raise SystemExit("读不到 " + name)
    return set(re.findall(r'"(minecraft:[a-z_]+)"', m.group(1)))


UNDEAD = id_set("UNDEAD")
FIREPROOF = id_set("FIREPROOF")
HYDROPHOBIC = id_set("HYDROPHOBIC")


def traits_of(entity_id):
    out = set()
    if entity_id in UNDEAD:
        out |= {"SUN_BURNS", "INVERTED_POTION"}
    if entity_id in FIREPROOF:
        out.add("FIRE_IMMUNE")
    if entity_id in HYDROPHOBIC:
        out.add("WATER_HURTS")
    return out


EXPECT = [
    ("minecraft:zombie", {"SUN_BURNS", "INVERTED_POTION"}),
    ("minecraft:skeleton", {"SUN_BURNS", "INVERTED_POTION"}),
    ("minecraft:wither_skeleton", {"SUN_BURNS", "INVERTED_POTION", "FIRE_IMMUNE"}),
    ("minecraft:blaze", {"FIRE_IMMUNE", "WATER_HURTS"}),
    ("minecraft:enderman", {"WATER_HURTS"}),
    ("minecraft:strider", {"FIRE_IMMUNE"}),
    # 这些是普通生物，不该有任何额外性质
    ("minecraft:cow", set()),
    ("minecraft:chicken", set()),
    ("minecraft:pig", set()),
]

print("从 %s 解析到：亡灵 %d、免疫火焰 %d、怕水 %d" % (SRC.split("/")[-1], len(UNDEAD), len(FIREPROOF), len(HYDROPHOBIC)))
print()
for entity_id, want in EXPECT:
    got = traits_of(entity_id)
    ok = got == want
    print("  [%s] %-28s -> %s" % ("OK " if ok else "FAIL", entity_id, sorted(got) or "（无）"))
    if not ok:
        FAILED.append("%s 期望 %s，实得 %s" % (entity_id, sorted(want), sorted(got)))

print()
if FAILED:
    print("自检失败 %d 项：" % len(FAILED))
    for f in FAILED:
        print("  -", f)
    sys.exit(1)
print("自检全部通过")
