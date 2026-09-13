# -*- coding: utf-8 -*-
"""算"实体变成方块"要多久 —— 把 Java 侧的公式与质料表读出来算，避免手抄漂移。

Java 实现：
  NameTagTransform.durationTicks = clamp(名字字数 × 200 + 质料差额 × 4, 600, 6000) tick
  MaterialUnits.budgetOf(实体)     = round(宽 × 宽 × 高 × 100)      ← 由碰撞箱体积算，不用表
  MaterialUnits.requirement(方块)  = OVERRIDES["block:<id>"] 或 FALLBACK_BLOCK(100)
  完成还要 materialReady：掠夺到的材料 ≥ 需求量，否则停在 100% 等玩家搬材料

用法: python tools/nametag/calc_transform_time.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

TRANSFORM = "src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagTransform.java"
UNITS = "src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/MaterialUnits.java"


def read(p):
    return io.open(p, encoding="utf-8").read()


def java_const(text, name, cast=int):
    m = re.search(r"\b" + name + r"\s*=\s*([0-9._]+)\s*;", text)
    if not m:
        raise SystemExit("读不到常量 " + name)
    return cast(m.group(1).replace("_", ""))


tf = read(TRANSFORM)
mu = read(UNITS)

MIN_TICKS = java_const(tf, "MIN_TICKS")
MAX_TICKS = java_const(tf, "MAX_TICKS")
PER_CHAR = java_const(tf, "TICKS_PER_CHAR")
PER_DEFICIT = java_const(tf, "TICKS_PER_DEFICIT")
ENTITY_SCALE = java_const(mu, "ENTITY_SCALE", float)
FALLBACK_BLOCK = java_const(mu, "FALLBACK_BLOCK")

BLOCK_UNITS = {k: int(v) for k, v in re.findall(r'OVERRIDES\.put\("block:([a-z_:]+)",\s*(\d+)\)', mu)}

print("公式：clamp(字数 × %d + 质料差额 × %d, %d, %d) tick（20 tick = 1 秒）"
      % (PER_CHAR, PER_DEFICIT, MIN_TICKS, MAX_TICKS))
print("实体质料 = round(宽² × 高 × %g)（按碰撞箱算，不走表）" % ENTITY_SCALE)
print("方块质料 = 表里的值，没进表的按 %d\n" % FALLBACK_BLOCK)

# 生物碰撞箱（宽, 高），取自 Minecraft 原版尺寸
MOBS = {
    "鸡": (0.4, 0.7),
    "兔": (0.4, 0.7),
    "猫": (0.6, 0.7),
    "羊": (0.9, 1.3),
    "猪": (0.9, 0.9),
    "牛": (0.9, 1.4),
    "村民": (0.6, 1.95),
    "铁傀儡": (1.4, 2.7),
}
BLOCKS = ["石头", "钻石块", "金块", "铁块", "泥土"]


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def budget(mob):
    w, h = MOBS[mob]
    return max(1, round(w * w * h * ENTITY_SCALE))


def need(block):
    key = "minecraft:" + {"石头": "stone", "钻石块": "diamond_block", "金块": "gold_block",
                          "铁块": "iron_block", "泥土": "dirt"}[block]
    return BLOCK_UNITS.get(key, FALLBACK_BLOCK)


def ticks(mob, name, block):
    chars = max(1, len(name))
    deficit = max(0, need(block) - budget(mob))
    return clamp(chars * PER_CHAR + deficit * PER_DEFICIT, MIN_TICKS, MAX_TICKS), deficit


print("%-6s %-8s %-8s %-6s %-8s %-8s" % ("生物", "质料", "目标方块", "方块质料", "差额", "时长"))
print("-" * 62)
for mob in MOBS:
    for block in BLOCKS:
        t, deficit = ticks(mob, block, block)
        print("%-6s %-8d %-8s %-8d %-8d %5.0f 秒%s"
              % (mob, budget(mob), block, need(block), deficit, t / 20.0,
                 "（撞下限）" if t == MIN_TICKS else ("（撞上限）" if t == MAX_TICKS else "")))
    print()

print("对应关系（作者 2026-09-13 定）：**有就夺、没有就算了、但照样要换** ——")
print("      所以时间到就一定完成，掠夺只是代价与演出，不再是完成条件。")
print("      夺取范围：水平 ±%d 格（%d×%d 的面积）、上下 ±%d 格；"
      % (java_const(read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagCosts.java"), "DRAIN_HALF_EXTENT"),
         java_const(read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagCosts.java"), "DRAIN_HALF_EXTENT") * 2,
         java_const(read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagCosts.java"), "DRAIN_HALF_EXTENT") * 2,
         java_const(read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagCosts.java"), "DRAIN_VERTICAL")))
print("      每 %s tick 扫一趟，每趟 %s 列（一列 %d 格高），%d 列循环一遍。"
      % (java_const(read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagCosts.java"), "DRAIN_INTERVAL_TICKS"),
         java_const(read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagCosts.java"), "DRAIN_COLUMNS_PER_PASS"),
         java_const(read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagCosts.java"), "DRAIN_VERTICAL") * 2 + 1,
         java_const(read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagCosts.java"), "DRAIN_HALF_EXTENT") ** 2 * 4))
print("      金块/金装备直接消除，金矿变石头（深层变深层、下界金矿变下界岩）。")
