# -*- coding: utf-8 -*-
"""转化时间轴的离线自检：把 Java 侧的公式在 Python 里复现一遍，验设计文档里的数字。

Java 侧实现：anomaly/nametag/NameTagTransform.java
  durationTicks = clamp(名字字符数 × 200 + 质料差额 × 4, 600, 6000)
  progress      = (当前刻 − 开始刻) / duration
  stageOf(p)    = p >= 1 ? 4 : (int)(p × 4)
"""
import sys

MIN_TICKS = 600
MAX_TICKS = 6000
TICKS_PER_CHAR = 200
TICKS_PER_DEFICIT = 4
STAGES = 4

FAILED = []


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def duration_ticks(name, budget, need):
    chars = max(1, len(name))
    deficit = max(0, need - budget)
    return clamp(chars * TICKS_PER_CHAR + deficit * TICKS_PER_DEFICIT, MIN_TICKS, MAX_TICKS)


def progress(game_time, start, duration):
    if duration <= 0:
        return 1.0
    return max(0.0, min(1.0, (game_time - start) / float(duration)))


def stage_of(p):
    return STAGES if p >= 1.0 else int(p * STAGES)


def check(label, got, want, tol=0):
    ok = abs(got - want) <= tol if isinstance(got, (int, float)) else got == want
    print("  [%s] %-46s -> %s" % ("OK " if ok else "FAIL", label, got))
    if not ok:
        FAILED.append("%s: 得到 %r，期望 %r" % (label, got, want))


print("=== 1. 时长公式（设计文档 §4.1 的例子）===")
# "鸡"只有 1 个字 → 200 tick，被下限抬到 600（30 秒）
ticks = duration_ticks("鸡", 300, 10)
check("牛→「鸡」（1 字，质料盈余）→ 被下限抬到 30 秒", ticks, MIN_TICKS)
check("  折合秒数", ticks / 20.0, 30.0)
# 5 个字的名字才到 1000 tick
check("牛→「下界合金镐」（5 字，盈余）", duration_ticks("下界合金镐", 300, 10), 1000)
check("  折合秒数", duration_ticks("下界合金镐", 300, 10) / 20.0, 50.0)
check("羊→「金块」（2 字，亏 1100）", duration_ticks("金块", 100, 1200), 4800)
check("  折合秒数", duration_ticks("金块", 100, 1200) / 20.0, 240.0)
check("牛→「金块」（2 字，亏 900）", duration_ticks("金块", 300, 1200), 4000)

print("=== 2. 上下夹取 ===")
check("极短名字 + 质料充足 → 取下限 30 秒", duration_ticks("鸡", 9999, 1), MIN_TICKS)
check("超长名字 + 巨额亏空 → 取上限 5 分钟", duration_ticks("饕幾嘅魃邋饕幾嘅魃邋饕幾嘅魃邋", 1, 99999), MAX_TICKS)

print("=== 3. 进度与阶段 ===")
check("刚开始", progress(100, 100, 1000), 0.0)
check("走一半", progress(600, 100, 1000), 0.5)
check("走完", progress(1100, 100, 1000), 1.0)
check("超时也不会超过 1", progress(99999, 100, 1000), 1.0)
check("负时长容错", progress(100, 100, 0), 1.0)
for p, want in ((0.0, 0), (0.24, 0), (0.25, 1), (0.5, 2), (0.74, 2), (0.75, 3), (0.999, 3), (1.0, 4)):
    check("阶段 %.3f" % p, stage_of(p), want)

print("=== 4. 单调性：名字越长/越重，转化越慢 ===")
prev = 0
for name, budget, need in (("鸡", 300, 10), ("绵羊", 300, 100), ("金块", 300, 1200), ("钻石块", 300, 900)):
    d = duration_ticks(name, budget, need)
    print("    %-6s budget=%-5d need=%-5d %5d tick = %5.1f 秒" % (name, budget, need, d, d / 20.0))
    if name == "金块":
        prev = d
if duration_ticks("金块", 300, 1200) <= duration_ticks("鸡", 300, 10):
    FAILED.append("更重的名字应该更慢，但金块没有比鸡慢")

print()
if FAILED:
    print("自检失败 %d 项：" % len(FAILED))
    for f in FAILED:
        print("  -", f)
    sys.exit(1)
print("自检全部通过")
