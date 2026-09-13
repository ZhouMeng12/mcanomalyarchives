# -*- coding: utf-8 -*-
"""覆写 UO-008 的档案正文（canonical 基线 + 工作区 language_map 两处都要改）。

为什么不能只跑 inject_language_map.py：那个脚本只补"缺失"的键，
而已有的 uo008.info / uo008.trait 是早先的占位（写着"档案残缺 / 辐射污染"），必须替换。
"""
import io
import json
import os
import re
import shutil
import time

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

WS = "mcanomalyarchives.mcreator"
CANON = {
    "zh_cn": "tools/mcreator-guard/canonical/zh_cn.extra.txt",
    "en_us": "tools/mcreator-guard/canonical/en_us.extra.txt",
}

# 依据正片 BV1YiGt6vEbK 的旁白转写（docs/video-research/uo-008-name-tag.md）
TEXT = {
    "zh_cn": {
        "codex.mcanomalyarchives.uo008.info": "深褐色的异常命名牌，出自一处矿井里的矿车宝藏。它倒置了命名的因果——不是按特性起名，而是按名字赋予特性。",
        "codex.mcanomalyarchives.uo008.trait": "只改内核、不改外壳 · 物品与方块必须过铁砧才能命名 · 物品耐久等于名字字数 · 命名不可逆，普通命名牌覆盖无效 · 跨类转化遵循质料守恒，撑不住就以爆炸偿付 · 现存数量被协会[数据删除]",
    },
    "en_us": {
        "codex.mcanomalyarchives.uo008.info": "A dark brown anomalous name tag recovered from a minecart cache inside a mineshaft. It inverts the causality of naming: a thing is not named after its properties - its properties are rewritten to match the name.",
        "codex.mcanomalyarchives.uo008.trait": "Changes the core, never the shell - Items and blocks must be named through an anvil - Item durability equals the number of characters in the name - Naming is irreversible; ordinary name tags cannot overwrite it - Cross-category conversion obeys material conservation and pays any deficit with an explosion - The number of extant tags is [DATA EXPUNGED]",
    },
}


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t, backup=False):
    if backup:
        shutil.copy(p, p + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    io.open(p, "w", encoding="utf-8", newline="").write(t)


# ---------- 1) canonical 基线 ----------
for lg, pairs in TEXT.items():
    path = CANON[lg]
    text = read(path)
    changed = 0
    for k, v in pairs.items():
        pat = re.compile(r'^(\s*"' + re.escape(k) + r'"\s*:\s*)("(?:[^"\\]|\\.)*")(,?)\s*$', re.M)
        new = json.dumps(v, ensure_ascii=False)
        text, n = pat.subn(lambda m: m.group(1) + new + m.group(3), text)
        if n != 1:
            raise SystemExit("基线里 %s 的键 %s 匹配到 %d 次（应为 1）" % (lg, k, n))
        changed += 1
    write(path, text)
    print("OK %s 基线覆写 %d 条" % (lg, changed))

# ---------- 2) 工作区 language_map ----------
raw = read(WS)


def block_range(text, key):
    m = re.search(r'"' + re.escape(key) + r'"\s*:\s*\{', text)
    if not m:
        return None
    i = m.end() - 1
    depth, in_str, esc = 0, False, False
    while i < len(text):
        c = text[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return (m.end(), i)
        i += 1
    return None


lm_open, lm_close = block_range(raw, "language_map")
segment = raw[lm_open:lm_close]
total = 0
for lg, pairs in TEXT.items():
    bopen, bclose = block_range(segment, lg)
    body = segment[bopen:bclose]
    for k, v in pairs.items():
        pat = re.compile(r'("' + re.escape(k) + r'"\s*:\s*)("(?:[^"\\]|\\.)*")')
        new = json.dumps(v, ensure_ascii=False)
        body, n = pat.subn(lambda m: m.group(1) + new, body)
        if n != 1:
            raise SystemExit("language_map[%s] 里 %s 匹配到 %d 次（应为 1）" % (lg, k, n))
        total += 1
    segment = segment[:bopen] + body + segment[bclose:]

shutil.copy(WS, WS + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
write(WS, raw[:lm_open] + segment + raw[lm_close:])

# ---------- 3) 资源 lang 文件（守卫的 insertBefore 只补缺失键，不会更新已有键的值） ----------
LANG = {
    "zh_cn": "src/main/resources/assets/mcanomalyarchives/lang/zh_cn.json",
    "en_us": "src/main/resources/assets/mcanomalyarchives/lang/en_us.json",
}
for lg, path in LANG.items():
    text = read(path)
    for k, v in TEXT[lg].items():
        pat = re.compile(r'^(\s*"' + re.escape(k) + r'"\s*:\s*)("(?:[^"\\]|\\.)*")(,?)\s*$', re.M)
        new = json.dumps(v, ensure_ascii=False)
        text, n = pat.subn(lambda m: m.group(1) + new + m.group(3), text)
        if n != 1:
            raise SystemExit("lang/%s.json 里 %s 匹配到 %d 次（应为 1）" % (lg, k, n))
    json.loads(text)  # 立刻校验是合法 JSON
    write(path, text)
    print("OK lang/%s.json 覆写 UO-008 正文" % lg)

# ---------- 4) 校验 ----------
ws = json.loads(read(WS))
for lg in ("en_us", "zh_cn"):
    d = ws["language_map"][lg]
    print("%s: %d 条  uo008.trait = %s" % (lg, len(d), d["codex.mcanomalyarchives.uo008.trait"][:40] + "..."))
print("元素数:", len(ws["mod_elements"]), " 覆写条数:", total)
