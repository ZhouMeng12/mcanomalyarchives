# -*- coding: utf-8 -*-
"""
把自定义词条写进 MCreator 工作区的 language_map（根因修复）。

背景：MCreator 每次"重新生成代码"都会用工作区里的 language_map 重写 lang 文件，
所以只往 lang/json 里插词条的做法每次都会被冲掉（守卫只能事后补）。
把词条写进 language_map 后，MCreator 会自己把它们生成出来，从此不再丢。

词条来源：tools/mcreator-guard/canonical/{zh_cn,en_us}.extra.txt（我一直在维护的插入式基线）。
"""
import io, json, os, re, shutil, time

CANON = {
    "zh_cn": "tools/mcreator-guard/canonical/zh_cn.extra.txt",
    "en_us": "tools/mcreator-guard/canonical/en_us.extra.txt",
}
WS = "mcanomalyarchives.mcreator"


def parse_canonical(path):
    """canonical 每行形如：  "key": "value",  （末尾逗号可选）"""
    out = {}
    for line in io.open(path, encoding="utf-8"):
        line = line.strip()
        if not line or not line.startswith('"'):
            continue
        line = line.rstrip(",")
        m = re.match(r'^("(?:[^"\\]|\\.)*")\s*:\s*("(?:[^"\\]|\\.)*")$', line)
        if not m:
            print("  跳过无法解析的行:", line[:70])
            continue
        key = json.loads(m.group(1))
        val = json.loads(m.group(2))
        out[key] = val
    return out


canon = {lg: parse_canonical(p) for lg, p in CANON.items()}
for lg, d in canon.items():
    print("%s：canonical 词条 %d 条" % (lg, len(d)))

raw = io.open(WS, encoding="utf-8", newline="").read()
ws = json.loads(raw)
lm = ws.get("language_map", {})
print("工作区现有：en_us %d 条 / zh_cn %d 条" % (len(lm.get("en_us", {})), len(lm.get("zh_cn", {}))))

# 找出每个语言块里缺失的键
missing = {}
for lg in ("en_us", "zh_cn"):
    have = lm.get(lg, {})
    missing[lg] = {k: v for k, v in canon[lg].items() if k not in have}
    print("  需要补进 %s：%d 条" % (lg, len(missing[lg])))

if not any(missing.values()):
    print("无需改动")
    raise SystemExit(0)


def block_range(text, key):
    """定位 "key": { ... } 的区间（字符串感知的括号匹配），返回 (开括号后位置, 闭括号位置)"""
    m = re.search(r'"' + re.escape(key) + r'"\s*:\s*\{', text)
    if not m:
        return None
    i = m.end() - 1
    depth = 0
    in_str = False
    esc = False
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


# language_map 的位置（整体块），再在其中定位 en_us / zh_cn
lm_range = block_range(raw, "language_map")
assert lm_range, "找不到 language_map"
lm_open, lm_close = lm_range
segment = raw[lm_open:lm_close]

for lg in ("zh_cn", "en_us"):   # 从后往前改，前面的偏移才不失效
    if not missing[lg]:
        continue
    r = block_range(segment, lg)
    assert r, "找不到 " + lg
    bopen, bclose = r
    # 该块内已有内容 → 需要给最后一条补逗号（这里直接在末尾插入，前面补一个逗号）
    body = segment[bopen:bclose]
    need_comma = body.strip() != ""
    lines = []
    for k, v in missing[lg].items():
        lines.append('      ' + json.dumps(k, ensure_ascii=False) + ': ' + json.dumps(v, ensure_ascii=False))
    ins = ("," if need_comma else "") + "\r\n" + ",\r\n".join(lines) + "\r\n    "
    segment = segment[:bclose] + ins + segment[bclose:]

shutil.copy(WS, WS + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
io.open(WS, "w", encoding="utf-8", newline="").write(raw[:lm_open] + segment + raw[lm_close:])

# 校验
ws2 = json.loads(io.open(WS, encoding="utf-8").read())
for lg in ("en_us", "zh_cn"):
    d = ws2["language_map"][lg]
    print("写入后 %s：%d 条，其中 codex 词条 %d 条"
          % (lg, len(d), len([k for k in d if k.startswith("codex.")])))
print("元素数未受影响:", len(ws2["mod_elements"]))
