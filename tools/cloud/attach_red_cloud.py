# -*- coding: utf-8 -*-
"""从小伪云元素派生红伪云元素（elements/RedStangeCloud.mod.json），并挂进工作区。

与小伪云同模型同尺寸，只把贴图换成虞美人主色的红：
  mobName          Red Stange Cloud
  mobModelTexture  cloud_red.png
  其余（modelWidth/Height、visualScale、spawnThisMob=false、免疫、创造标签）照抄。

红伪云**不从传送门自然出现**（它只能由小伪云碰到虞美人变来），所以这里不下发概率，
只给刷怪蛋方便测试。语言词条四处齐备（工作区 language_map + canonical + 守卫 expectAll，
lang 产物交给 guard.ps1 apply）。
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
SRC_ELEM = "elements/SmallStangeCloud.mod.json"
DST_ELEM = "elements/RedStangeCloud.mod.json"
ELEMENT_NAME = "RedStangeCloud"
REGISTRY_NAME = "red_stange_cloud"
ELEMENT_FILES = [
    "src/main/java/net/mcreator/mcanomalyarchives/entity/RedStangeCloudEntity.java",
    "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/RedStangeCloudRenderer.java",
    "src/main/resources/assets/mcanomalyarchives/models/item/red_stange_cloud_spawn_egg.json",
    "src/main/resources/assets/mcanomalyarchives/items/red_stange_cloud_spawn_egg.json",
]
NEW_LANG = {
    "zh_cn": [("entity.mcanomalyarchives.red_stange_cloud", "红伪云"),
              ("item.mcanomalyarchives.red_stange_cloud_spawn_egg", "红伪云刷怪蛋")],
    "en_us": [("entity.mcanomalyarchives.red_stange_cloud", "Red Stange Cloud"),
              ("item.mcanomalyarchives.red_stange_cloud_spawn_egg", "Red Stange Cloud Spawn Egg")],
}


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t, backup=False):
    if backup:
        shutil.copy(p, p + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    io.open(p, "w", encoding="utf-8", newline="").write(t)


# ---- 1) 元素 ----
doc = json.load(io.open(SRC_ELEM, encoding="utf-8"))
d = doc["definition"]
assert d["mobModelName"] == "ModelStrangeCloud"
d["mobName"] = "Red Stange Cloud"
d["mobModelTexture"] = "cloud_red.png"
d["hasSpawnEgg"] = True
d["creativeTabs"] = [{"value": "CUSTOM:StrangeRecord"}]
with io.open(DST_ELEM, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(doc, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("写出", DST_ELEM, "贴图 =", d["mobModelTexture"], "尺寸 =", d["modelWidth"], d["modelHeight"])

# ---- 2) mod_elements ----
raw = read(WS)
if '"%s"' % ELEMENT_NAME in raw:
    print("-- 工作区已有", ELEMENT_NAME)
else:
    entry = NL.join(
        ["    {", '      "name": "%s",' % ELEMENT_NAME, '      "type": "livingentity",',
         '      "compiles": true,', '      "locked_code": true,',
         '      "registry_name": "%s",' % REGISTRY_NAME, '      "metadata": {', '        "files": ['] +
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
    print("OK 元素数 %d，已挂载 %s" % (len(ws["mod_elements"]), any(e["name"] == ELEMENT_NAME for e in ws["mod_elements"])))

# ---- 3) language_map ----
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


raw = read(WS)
lm_open, lm_close = block_range(raw, "language_map")
segment = raw[lm_open:lm_close]
changed = 0
for lg in ("zh_cn", "en_us"):
    bopen, bclose = block_range(segment, lg)
    body = segment[bopen:bclose]
    have = set(re.findall(r'"((?:[^"\\]|\\.)*)"\s*:', body))
    add = [(k, v) for k, v in NEW_LANG[lg] if k not in have]
    if add:
        head = re.sub(r",\s*$", "", body.rstrip())
        head = head if head.rstrip().endswith("{") else head + ","
        lines = []
        for i, (k, v) in enumerate(add):
            tail = "" if i == len(add) - 1 else ","
            lines.append("      " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) + tail)
        body = head + NL + NL.join(lines) + NL
        changed += len(add)
    segment = segment[:bopen] + body + segment[bclose:]
if changed:
    shutil.copy(WS, WS + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    write(WS, raw[:lm_open] + segment + raw[lm_close:])
json.loads(read(WS))
print("language_map 新增 %d 条" % changed)

# ---- 4) canonical + expectAll ----
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
with io.open(MANIFEST, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(manifest, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("守卫 expectAll 新增 %d 个键（接着跑 guard.ps1 apply）" % touched)
