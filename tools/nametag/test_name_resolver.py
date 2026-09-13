# -*- coding: utf-8 -*-
"""名字解析的离线自检：不需要启动游戏，直接对着生成出来的索引文件验规则。

验的是 Java 侧 NameResolver / NameRules / MaterialUnits 的判定规则（在 Python 里等价复现），
以及三条来自正片的关键期望：
  1. 正片里出现的名字必须解析得到（鸡 / 下界合金镐 / 钻石块 / 牛 / 猪 / 绵羊 …）
  2. 玩家习惯叫法必须解析得到（原石 / 羊 / 狗 …）—— Java 版官方译名和口语不一致
  3. 未命中必须返回 None（正片设定：只认主流叫法）
"""
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

IDX_DIR = "src/main/resources/data/mcanomalyarchives/name_index"
FAILED = []


def load(name):
    path = os.path.join(IDX_DIR, name)
    if not os.path.isfile(path):
        FAILED.append("缺少文件 %s" % path)
        return {}
    with io.open(path, encoding="utf-8") as fh:
        return json.load(fh)


def strip_meta(d):
    return {k: v for k, v in d.items() if not k.startswith("__")}


index = {}
for f in ("zh_cn.json", "en_us.json"):
    for k, v in strip_meta(load(f)).items():
        index.setdefault(k, v)
aliases = {}
for f in ("alias_zh_cn.json", "alias_en_us.json"):
    for k, v in strip_meta(load(f)).items():
        aliases.setdefault(k, v)

lower_index = {k.lower(): v for k, v in index.items()}
lower_alias = {k.lower(): v for k, v in aliases.items()}

PRIORITY = {"item": 0, "block": 1, "entity": 2}  # ResolvedName.Kind 的声明顺序


def resolve(raw, preferred=None):
    key = (raw or "").strip()
    hit = index.get(key) or aliases.get(key) or lower_index.get(key.lower()) or lower_alias.get(key.lower())
    if not hit:
        return None
    if preferred:
        for token in hit:
            if token.split(":")[0] == preferred:
                return token
    return hit[0]


# ---------- 1) 正片里出现过的名字 ----------
VIDEO = [
    ("鸡", "entity:minecraft:chicken"),
    ("牛", "entity:minecraft:cow"),
    ("猪", "entity:minecraft:pig"),
    ("绵羊", "entity:minecraft:sheep"),
    ("下界合金镐", "item:minecraft:netherite_pickaxe"),
    ("木锹", "item:minecraft:wooden_shovel"),
    ("木棍", "item:minecraft:stick"),
    ("钻石块", "block:minecraft:diamond_block"),
    ("金块", "block:minecraft:gold_block"),
    ("石头", "block:minecraft:stone"),
    ("水", "block:minecraft:water"),
    ("马铃薯", "item:minecraft:potato"),
]

# ---------- 2) 口语别名（Java 版官方译名和玩家叫法不一致的那些） ----------
ALIASED = [
    ("原石", "block:minecraft:stone"),      # 官方是"石头"
    ("羊", "entity:minecraft:sheep"),        # 官方是"绵羊"
    ("狗", "entity:minecraft:wolf"),         # 官方是"狼"
    ("土豆", "item:minecraft:potato"),       # 官方是"马铃薯"——正片开场事故用的就是这个词
    ("木铲", "item:minecraft:wooden_shovel"),  # 官方是"木锹"
    ("木头", "block:minecraft:oak_log"),
    ("钻石狗", "item:minecraft:diamond_pickaxe"),  # 正片听写成"钻石狗/钻石钢"，其实指钻石镐
]

# ---------- 3) 必须解析不到 ----------
UNKNOWN = ["地蛋", "大狗", "寰宇支配之剑", "饕幾嘅魃邋"]

# ---------- 4) 同名同类优先（"钻石块"贴在方块上要选 block） ----------
PREFER = [("石头", "block", "block:minecraft:stone"), ("石头", "item", "block:minecraft:stone")]


def check(label, got, want):
    ok = got == want
    print("  [%s] %-34s -> %s" % ("OK " if ok else "FAIL", label, got))
    if not ok:
        FAILED.append("%s: 得到 %r，期望 %r" % (label, got, want))


print("=== 1. 正片里出现过的名字 ===")
for name, want in VIDEO:
    check(name, resolve(name), want)

print("=== 2. 口语别名 ===")
for name, want in ALIASED:
    check(name, resolve(name), want)

print("=== 3. 未命中（只认主流叫法）===")
for name in UNKNOWN:
    got = resolve(name)
    ok = got is None
    print("  [%s] %-34s -> %s" % ("OK " if ok else "FAIL", name, got))
    if not ok:
        FAILED.append("%s 应该解析不到，却得到 %r" % (name, got))

print("=== 4. 同名同类优先 ===")
for name, preferred, want in PREFER:
    check("%s (prefer=%s)" % (name, preferred), resolve(name, preferred), want)

print("=== 5. 索引规模 ===")
print("  显示名条目 %d（index）+ %d（alias）" % (len(index), len(aliases)))
kinds = {}
for v in index.values():
    for token in v:
        kinds[token.split(":")[0]] = kinds.get(token.split(":")[0], 0) + 1
print("  索引内 id 数量：", kinds)
if len(index) < 1000:
    FAILED.append("索引条目过少（%d），生成脚本可能没跑对" % len(index))

print()
if FAILED:
    print("自检失败 %d 项：" % len(FAILED))
    for f in FAILED:
        print("  -", f)
    sys.exit(1)
print("自检全部通过")
