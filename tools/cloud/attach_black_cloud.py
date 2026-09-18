# -*- coding: utf-8 -*-
"""把黑吸伪云挂进 MCreator 工作区（mod_elements + 四处语言词条）。

与 attach_small_cloud.py 同一套做法（mod_elements 增一条 locked_code=true；
语言词条写 workspace language_map + canonical 基线 + 守卫 expectAll，
**不**自己往 lang 产物里追加 —— 那是 guard.ps1 apply 的活）。
"""
import io
import json
import os
import re
import shutil
import time

NL = "\n"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

WS = "mcanomalyarchives.mcreator"
MANIFEST = "tools/mcreator-guard/manifest.json"
CANON = {
    "zh_cn": "tools/mcreator-guard/canonical/zh_cn.extra.txt",
    "en_us": "tools/mcreator-guard/canonical/en_us.extra.txt",
}
ELEMENT_NAME = "BlackStangeCloud"
REGISTRY_NAME = "black_stange_cloud"
ELEMENT_FILES = [
    "src/main/java/net/mcreator/mcanomalyarchives/entity/BlackStangeCloudEntity.java",
    "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/BlackStangeCloudRenderer.java",
    "src/main/resources/assets/mcanomalyarchives/models/item/black_stange_cloud_spawn_egg.json",
    "src/main/resources/assets/mcanomalyarchives/items/black_stange_cloud_spawn_egg.json",
]
NEW_LANG = {
    "zh_cn": [
        ("entity.mcanomalyarchives.black_stange_cloud", "黑吸伪云"),
        ("item.mcanomalyarchives.black_stange_cloud_spawn_egg", "黑吸伪云刷怪蛋"),
    ],
    "en_us": [
        ("entity.mcanomalyarchives.black_stange_cloud", "Black Devouring Cloud"),
        ("item.mcanomalyarchives.black_stange_cloud_spawn_egg", "Black Devouring Cloud Spawn Egg"),
    ],
}


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t, backup=False):
    if backup:
        shutil.copy(p, p + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
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


# ---- 1) mod_elements ----
raw = read(WS)
if '"%s"' % ELEMENT_NAME in raw:
    print("-- 工作区已有", ELEMENT_NAME)
else:
    entry = NL.join(
        ["    {",
         '      "name": "%s",' % ELEMENT_NAME,
         '      "type": "livingentity",',
         '      "compiles": true,',
         '      "locked_code": true,',
         '      "registry_name": "%s",' % REGISTRY_NAME,
         '      "metadata": {',
         '        "files": ['] +
        ['          "%s"%s' % (f, "," if i < len(ELEMENT_FILES) - 1 else "") for i, f in enumerate(ELEMENT_FILES)] +
        ["        ]", "      },", '      "path": "~/StrangeCloud"', "    }"])
    m2 = re.search(r'"mod_elements"\s*:\s*\[', raw)
    assert m2, "工作区里找不到 mod_elements"
    i, depth, in_str, esc = m2.end() - 1, 0, False, False
    while i < len(raw):
        c = raw[i]
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
            elif c == "[":
                depth += 1
            elif c == "]":
                depth -= 1
                if depth == 0:
                    break
        i += 1
    shutil.copy(WS, WS + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    write(WS, raw[:i].rstrip() + "," + NL + entry + NL + "  " + raw[i:])
    ws = json.loads(read(WS))
    print("OK 工作区元素数:", len(ws["mod_elements"]),
          "已挂载:", any(e["name"] == ELEMENT_NAME for e in ws["mod_elements"]))

# ---- 2) workspace language_map ----
raw = read(WS)
lm_open, lm_close = block_range(raw, "language_map")
assert lm_open is not None, "工作区里找不到 language_map"
segment = raw[lm_open:lm_close]
changed = 0
for lg in ("zh_cn", "en_us"):
    bopen, bclose = block_range(segment, lg)
    assert bopen is not None, "找不到 language_map.%s" % lg
    body, n = add_keys(segment[bopen:bclose], NEW_LANG[lg], "      ")
    changed += n
    segment = segment[:bopen] + body + segment[bclose:]
if changed:
    shutil.copy(WS, WS + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    write(WS, raw[:lm_open] + segment + raw[lm_close:])
json.loads(read(WS))
print("language_map 新增 %d 条" % changed)

# ---- 3) canonical 基线 + 4) 守卫 expectAll ----
for lg, path in CANON.items():
    text = read(path)
    have = set(re.findall(r'^\s*"((?:[^"\\]|\\.)*)"\s*:', text, re.M))
    add = [(k, v) for k, v in NEW_LANG[lg] if k not in have]
    if add:
        if not text.endswith(NL):
            text += NL
        text += NL.join("  " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) + "," for k, v in add) + NL
        write(path, text)
        print("OK %s 新增 %d 条" % (os.path.basename(path), len(add)))

manifest = json.load(io.open(MANIFEST, encoding="utf-8"))
touched = 0
for item in manifest["items"]:
    if item["id"] in ("lang-zh-extra", "lang-en-extra"):
        lg = "zh_cn" if item["id"] == "lang-zh-extra" else "en_us"
        for key, _ in NEW_LANG[lg]:
            quoted = '"%s"' % key
            if quoted not in item["expectAll"]:
                item["expectAll"].append(quoted)
                touched += 1
with io.open(MANIFEST, "w", encoding="utf-8", newline=NL) as fh:
    json.dump(manifest, fh, ensure_ascii=False, indent=2)
    fh.write(NL)
print("守卫 expectAll 新增 %d 个键（接着跑 guard.ps1 apply）" % touched)
