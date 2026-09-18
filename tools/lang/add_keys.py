# -*- coding: utf-8 -*-
"""往四处同时加语言词条（少一处就会被 MCreator 或守卫写回去）：

  1. mcanomalyarchives.mcreator 的 language_map （MCreator 重写 lang 的来源）
  2. tools/mcreator-guard/canonical/{zh_cn,en_us}.extra.txt （守卫的插入基线）
  3. 守卫 manifest 里 lang-*-extra 的 expectAll （让 check 会因缺键失败 → apply 才插得进 lang 产物）
  4. src/main/resources/assets/mcanomalyarchives/lang/*.json （当前产物；**不自己追加**，交给 guard apply）

用法：把要加的词条写进下面的 KEYS 再跑本脚本，然后跑 guard.ps1 apply。
沿用 tools/cloud/attach_small_cloud.py 的 block_range / add_keys 做法（那两个坑都在注释里）。
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

KEYS = {
    "zh_cn": [
        ("nametag.mcanomalyarchives.not_for_anomaly", "改写不了——它本身就是异常"),
    ],
    "en_us": [
        ("nametag.mcanomalyarchives.not_for_anomaly", "It cannot be rewritten - it is an anomaly itself"),
    ],
}


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t):
    io.open(p, "w", encoding="utf-8", newline="").write(t)


def block_range(text, key):
    m = re.search(r'"' + re.escape(key) + r'"\s*:\s*\{', text)
    if not m:
        return None
    i, depth, in_str, esc = m.end() - 1, 0, False, False
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


def add_keys(body, pairs, indent):
    have = set(re.findall(r'"((?:[^"\\]|\\.)*)"\s*:', body))
    add = [(k, v) for k, v in pairs if k not in have]
    if not add:
        return body, 0
    head = re.sub(r",\s*$", "", body.rstrip())
    head = head if head.rstrip().endswith("{") else head + ","
    lines = []
    for i, (k, v) in enumerate(add):
        tail = "" if i == len(add) - 1 else ","
        lines.append(indent + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) + tail)
    return head + NL + NL.join(lines) + NL, len(add)


# 1) 工作区 language_map（按语言块分别改，否则 zh/en 会互相覆盖）
raw = read(WS)
lm_open, lm_close = block_range(raw, "language_map")
assert lm_open is not None, "工作区里找不到 language_map"
segment = raw[lm_open:lm_close]
changed = 0
for lg in ("zh_cn", "en_us"):
    bopen, bclose = block_range(segment, lg)
    assert bopen is not None, "找不到 language_map.%s" % lg
    body, n = add_keys(segment[bopen:bclose], KEYS[lg], "      ")
    changed += n
    segment = segment[:bopen] + body + segment[bclose:]
if changed:
    write(WS, raw[:lm_open] + segment + raw[lm_close:])
json.loads(read(WS))
print("工作区 language_map：新增 %d 条" % changed)

# 2) canonical 基线
for lg, path in CANON.items():
    text = read(path)
    have = set(re.findall(r'^\s*"((?:[^"\\]|\\.)*)"\s*:', text, re.M))
    add = [(k, v) for k, v in KEYS[lg] if k not in have]
    if not add:
        print("-- %s 已是最新" % os.path.basename(path))
        continue
    if not text.endswith(NL):
        text += NL
    text += NL.join("  " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) + "," for k, v in add) + NL
    write(path, text)
    print("OK %s 新增 %d 条" % (os.path.basename(path), len(add)))

# 3) 守卫 expectAll（加进去，check 才会因为缺键失败、apply 才会插进 lang 产物）
manifest = json.load(io.open(MANIFEST, encoding="utf-8"))
touched = 0
for item in manifest["items"]:
    if item["id"] in ("lang-zh-extra", "lang-en-extra"):
        lg = "zh_cn" if item["id"] == "lang-zh-extra" else "en_us"
        for key, _ in KEYS[lg]:
            quoted = '"%s"' % key
            if quoted not in item["expectAll"]:
                item["expectAll"].append(quoted)
                touched += 1
with io.open(MANIFEST, "w", encoding="utf-8", newline=NL) as fh:
    json.dump(manifest, fh, ensure_ascii=False, indent=2)
    fh.write(NL)
print("守卫 expectAll：新增 %d 个键（接着跑 guard.ps1 apply 把它们插进 lang）" % touched)
