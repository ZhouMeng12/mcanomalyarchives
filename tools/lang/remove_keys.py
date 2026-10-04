# -*- coding: utf-8 -*-
"""从四处同时删语言词条（少一处就会被 MCreator 或守卫写回来）：

  1. mcanomalyarchives.mcreator 的 language_map （MCreator 重写 lang 的来源）
  2. tools/mcreator-guard/canonical/{zh_cn,en_us}.extra.txt （守卫的插入基线）
  3. 守卫 manifest 里 lang-*-extra 的 expectAll （清掉点名，否则 check 会因缺键失败）
  4. src/main/resources/assets/mcanomalyarchives/lang/*.json （当前产物）

用法：把要删的词条写进下面的 REMOVE 再跑本脚本，然后跑 guard.ps1 check。
add_keys.py 的逆向操作。

【踩过的坑】删条目必须**整行删**：语言文件与 language_map 都是一条一行，
用 `\\s*"key"\\s*:\\s*"…"\\s*,\\s*` 这种整段正则时，前导/后置的 `\\s*` 会把
相邻的换行与缩进一起吃掉 —— 结果是把上一条和下一条挤成同一行，
连文件里原本的空白行、末尾条目都被连坐（第一版就踩了）。
"""
import io
import json
import os
import re

NL = "\n"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

WS = "mcanomalyarchives.mcreator"
MANIFEST = "tools/mcreator-guard/manifest.json"
CANON = {
    "zh_cn": "tools/mcreator-guard/canonical/zh_cn.extra.txt",
    "en_us": "tools/mcreator-guard/canonical/en_us.extra.txt",
}
LANG = {
    "zh_cn": "src/main/resources/assets/mcanomalyarchives/lang/zh_cn.json",
    "en_us": "src/main/resources/assets/mcanomalyarchives/lang/en_us.json",
}

# 百变命名牌的 11 条提示语 —— 作者要求全部去掉，中英两侧一起删。
# 保留：need_name、not_for_anomaly、egg。
REMOVE = [
    "nametag.mcanomalyarchives.unknown",
    "nametag.mcanomalyarchives.applied",
    "nametag.mcanomalyarchives.locked",
    "nametag.mcanomalyarchives.erased",
    "nametag.mcanomalyarchives.planned",
    "nametag.mcanomalyarchives.unstable",
    "nametag.mcanomalyarchives.exploded",
    "nametag.mcanomalyarchives.not_for_player",
    "nametag.mcanomalyarchives.no_output",
    "nametag.mcanomalyarchives.draining",
    "nametag.mcanomalyarchives.dry",
]
PER_BLOCK = len(REMOVE)  # 每份语言块应当各删掉这么多条


def read(p):
    return io.open(p, encoding="utf-8", newline="").read()


def write(p, t):
    io.open(p, "w", encoding="utf-8", newline="").write(t)


def drop_line(text, key):
    """整行删掉一条 `"key": "value",`，不留悬空逗号，也不动前后两行的排版。"""
    esc = re.escape(key)
    line = r'^[ \t]*"' + esc + r'"[ \t]*:[ \t]*"(?:[^"\\]|\\.)*"'
    text, n = re.subn(line + r'[ \t]*,[ \t]*\r?\n', "", text, flags=re.M)
    if n:
        return text, n
    # 它是块里最后一条（没有后置逗号）：删行的同时得把上一条的逗号收掉，否则 JSON 非法
    m = re.search(line + r'[ \t]*\r?\n', text, flags=re.M)
    if not m:
        return text, 0
    head = re.sub(r',([ \t]*\r?\n)$', r"\1", text[:m.start()])
    return head + text[m.end():], 1


def drop_from_body(body, label):
    n = 0
    for key in REMOVE:
        body, k = drop_line(body, key)
        if k != 1:
            raise SystemExit("%s：%s 删掉了 %d 次（应为 1）—— 格式变了，别硬来" % (label, key, k))
        n += k
    return body, n


def block_range(text, key):
    """定位 "key": { ... } 的区间（字符串感知的括号匹配），返回 (开括号后位置, 闭括号位置)。"""
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


# ---- 1) canonical 基线 + 资源 lang 文件 ----
for lg in ("zh_cn", "en_us"):
    label = "canonical/" + os.path.basename(CANON[lg])
    text, n = drop_from_body(read(CANON[lg]), label)
    write(CANON[lg], text)
    print("  %-30s 删除 %d 条" % (label, n))

    label = "lang/" + lg + ".json"
    text, n = drop_from_body(read(LANG[lg]), label)
    json.loads(text)  # 产物必须是严格合法 JSON（这两个文件本来就不带末尾换行，别动）
    write(LANG[lg], text)
    print("  %-30s 删除 %d 条" % (label, n))

# ---- 2) 工作区 language_map ----
# 必须按语言块分别改：zh_cn 与 en_us 里的键名完全一样，全文件范围的正则会把两边互相覆盖。
raw = read(WS)
lm_open, lm_close = block_range(raw, "language_map")
assert lm_open is not None, "工作区里找不到 language_map"
segment = raw[lm_open:lm_close]
total = 0
for lg in ("zh_cn", "en_us"):
    r = block_range(segment, lg)
    assert r, "工作区里找不到 language_map.%s" % lg
    bopen, bclose = r
    body, n = drop_from_body(segment[bopen:bclose], "language_map." + lg)
    total += n
    segment = segment[:bopen] + body + segment[bclose:]
raw = raw[:lm_open] + segment + raw[lm_close:]
json.loads(raw)  # 工作区文件必须是严格合法 JSON，MCreator 才吃得下
write(WS, raw)
print("工作区 language_map：删除 %d 条（应为 %d）" % (total, PER_BLOCK * 2))

# ---- 3) 守卫 expectAll（不清掉点名，check 会一直因为缺键失败）----
manifest = json.loads(read(MANIFEST))
touched = 0
for item in manifest["items"]:
    if item["id"] in ("lang-zh-extra", "lang-en-extra"):
        kept = [e for e in item["expectAll"] if e.strip('"') not in REMOVE]
        touched += len(item["expectAll"]) - len(kept)
        item["expectAll"] = kept
write(MANIFEST, json.dumps(manifest, ensure_ascii=False, indent=2) + NL)
print("守卫 expectAll：清掉 %d 个点名（接着跑 guard.ps1 check）" % touched)

# ---- 4) 自查：四处都不该再有这些键 ----
print("\n残留自查：")
for label, path in list(CANON.items()) + list(LANG.items()) + [("(workspace)", WS)]:
    left = [k for k in REMOVE if '"%s"' % k in read(path)]
    print("  %-30s %s" % (os.path.basename(path) if label != "(workspace)" else WS, left or "干净"))
