# -*- coding: utf-8 -*-
"""命名牌词条收尾：删掉"解释性文本"词条、把两条提示改短。

作者要求：「去掉解释性文本，没有必要」。所以删掉
  nametag.mcanomalyarchives.tag_hint  （教玩家怎么用）
  nametag.mcanomalyarchives.tooltip   （"命名：X · 剩余 N 次"的机械读数）
并去掉 unknown / need_name 里的解释性括注。

四处都要改，少一处就会被 MCreator 或守卫写回来：
  1. tools/mcreator-guard/canonical/{zh_cn,en_us}.extra.txt（守卫的插入基线）
  2. mcanomalyarchives.mcreator 的 language_map（MCreator 重写 lang 的来源）
  3. src/main/resources/assets/mcanomalyarchives/lang/{zh_cn,en_us}.json（当前产物）
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
LANG = {
    "zh_cn": "src/main/resources/assets/mcanomalyarchives/lang/zh_cn.json",
    "en_us": "src/main/resources/assets/mcanomalyarchives/lang/en_us.json",
}
REMOVE = ["nametag.mcanomalyarchives.tag_hint", "nametag.mcanomalyarchives.tooltip"]
REPLACE = {
    "zh_cn": {
        "nametag.mcanomalyarchives.need_name": "这张命名牌上什么都没写",
        "nametag.mcanomalyarchives.unknown": "档案里没有「%s」这个名字",
    },
    "en_us": {
        "nametag.mcanomalyarchives.need_name": "This tag has nothing written on it",
        "nametag.mcanomalyarchives.unknown": "The archives hold no name like \"%s\"",
    },
}


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t, backup=False):
    if backup:
        shutil.copy(p, p + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    io.open(p, "w", encoding="utf-8", newline="").write(t)


def drop_key(text, key):
    """删掉一个 "key": "value" 条目，并且**不留下悬空逗号**。

    分两种情况：条目后面有逗号就连逗号一起删；条目是块里最后一条（没有后置逗号）就删前置逗号。
    上一版只删"含可选后置逗号"的形式，遇到最后一条会留下 `"上一条": "...",\n}` 这种非法 JSON
    ——Gson 容错能吃下，严格解析器不能，脚本自己就崩了。
    """
    body = re.escape(key)
    new, n = re.subn(r'\s*"' + body + r'"\s*:\s*"(?:[^"\\]|\\.)*"\s*,\s*', "", text)
    if n:
        return new, n
    new, n = re.subn(r',\s*"' + body + r'"\s*:\s*"(?:[^"\\]|\\.)*"', "", text)
    return new, n


def clean_json_lines(text, lg, label):
    removed = replaced = 0
    for key in REMOVE:
        text, n = drop_key(text, key)
        removed += n
    for key, value in REPLACE[lg].items():
        pat = re.compile(r'^(\s*"' + re.escape(key) + r'"\s*:\s*)("(?:[^"\\]|\\.)*")(,?)\s*$', re.M)
        text, n = pat.subn(lambda m: m.group(1) + json.dumps(value, ensure_ascii=False) + m.group(3), text)
        replaced += n
    # 兜底：Gson 容错能接受悬空逗号，严格 JSON 解析器不能
    text = re.sub(r",(\s*[}\]])", r"\1", text)
    print("  %-34s 删除 %d 条 / 改写 %d 条" % (label, removed, replaced))
    return text


# ---- 1) canonical 基线 ----
for lg, path in CANON.items():
    text = clean_json_lines(read(path), lg, "canonical/" + os.path.basename(path))
    write(path, text)

# ---- 2) 资源 lang 文件 ----
for lg, path in LANG.items():
    text = clean_json_lines(read(path), lg, "lang/" + lg + ".json")
    json.loads(text)
    write(path, text)

# ---- 3) 工作区 language_map ----
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


raw = read(WS)
# 必须按语言块分别改：zh_cn 与 en_us 里的键名完全一样，
# 全文件范围的正则会把两边互相覆盖（上一版就踩了这个坑）。
lm_open, lm_close = block_range(raw, "language_map")
assert lm_open is not None, "工作区里找不到 language_map"
segment = raw[lm_open:lm_close]
for lg in ("zh_cn", "en_us"):
    r = block_range(segment, lg)
    assert r, "工作区里找不到 language_map.%s" % lg
    bopen, bclose = r
    body = segment[bopen:bclose]
    for key in REMOVE:
        body, _ = drop_key(body, key)
    for key, value in REPLACE[lg].items():
        pat = re.compile(r'("' + re.escape(key) + r'"\s*:\s*)("(?:[^"\\]|\\.)*")')
        body, n = pat.subn(lambda m: m.group(1) + json.dumps(value, ensure_ascii=False), body)
        if n != 1:
            raise SystemExit("language_map.%s 里 %s 匹配 %d 次（应为 1）" % (lg, key, n))
    body = re.sub(r",(\s*[}\]])", r"\1", body)
    segment = segment[:bopen] + body + segment[bclose:]

raw = raw[:lm_open] + segment + raw[lm_close:]
json.loads(raw)  # 工作区文件必须是严格合法 JSON，MCreator 才吃得下
shutil.copy(WS, WS + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
write(WS, raw)

ws = json.loads(read(WS))
for lg in ("en_us", "zh_cn"):
    d = ws["language_map"][lg]
    gone = [k for k in REMOVE if k in d]
    print("%s: %d 条, 残留解释性词条 %s" % (lg, len(d), gone or "无"))
print("元素数:", len(ws["mod_elements"]))
