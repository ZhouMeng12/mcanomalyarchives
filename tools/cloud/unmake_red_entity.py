# -*- coding: utf-8 -*-
"""把"红伪云"从独立生物改成小伪云的一个变种（作者 2026-09-18）。

之前那版注册了独立实体 red_stange_cloud（元素/实体类/渲染器/刷怪蛋/注册表/词条）。
作者要的是"红伪云作为伪云的一个变种，不要再加一个生物了"，
所以这里把独立实体那一整套拆掉，只留下：
  * 贴图 textures/entities/cloud_red.png（变种渲染用）
  * 实体上的同步标记 isRed()/setRed()（在 SmallStangeCloudEntity 里，手工改的那部分）
  * 渲染器按标记换贴图（SmallStangeCloudRenderer）
  * 上瘾识别按标记过滤（PlayerLookAtCornPoppyListener）

拆掉的东西：元素文件、工作区 mod_elements 条目、四张注册表里的行、
刷怪蛋三件套、红伪云与刷怪蛋的词条（四处）、见闻录里多加的那个 id、守卫里指向被删文件的规则。
"""
import io
import json
import os
import re

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
DEAD_KEYS = ["entity.mcanomalyarchives.red_stange_cloud",
             "item.mcanomalyarchives.red_stange_cloud_spawn_egg"]
DEAD_FILES = [
    "elements/RedStangeCloud.mod.json",
    "src/main/java/net/mcreator/mcanomalyarchives/entity/RedStangeCloudEntity.java",
    "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/RedStangeCloudRenderer.java",
    "src/main/resources/assets/mcanomalyarchives/items/red_stange_cloud_spawn_egg.json",
    "src/main/resources/assets/mcanomalyarchives/models/item/red_stange_cloud_spawn_egg.json",
    "src/main/resources/assets/mcanomalyarchives/textures/item/red_stange_cloud_spawn_egg_generated.png",
]
DEAD_RULES = {"red-cloud-element", "red-cloud-registration", "red-cloud-renderer-registration",
              "red-cloud-renderer", "red-cloud-entity"}


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t):
    io.open(p, "w", encoding="utf-8", newline="").write(t)


def drop_key(text, key):
    """删掉一个 "key": "value" 条目，不留悬空逗号（Gson 容错、严格解析器不容错）。"""
    body = re.escape(key)
    new, n = re.subn(r'\s*"' + body + r'"\s*:\s*"(?:[^"\\]|\\.)*"\s*,\s*', "", text)
    if n:
        return new, n
    return re.subn(r',\s*"' + body + r'"\s*:\s*"(?:[^"\\]|\\.)*"', "", text)


# ---- 1) 删文件 ----
for p in DEAD_FILES:
    if os.path.exists(p):
        os.remove(p)
        print("删除", p)

# ---- 2) 注册表里的行 ----
removals = {
    "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java": [
        re.compile(r'\n\tpublic static final DeferredHolder<EntityType<\?>, EntityType<RedStangeCloudEntity>> RED_STANGE_CLOUD = register\("red_stange_cloud",.*?\.sized\(4f, 0\.67f\)\);\n', re.S),
        re.compile(r'\n\t\tRedStangeCloudEntity\.init\(event\);'),
        re.compile(r'\n\t\tevent\.put\(RED_STANGE_CLOUD\.get\(\), RedStangeCloudEntity\.createAttributes\(\)\.build\(\)\);'),
    ],
    "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntityRenderers.java": [
        re.compile(r'\n\t\tevent\.registerEntityRenderer\(McanomalyarchivesModEntities\.RED_STANGE_CLOUD\.get\(\), RedStangeCloudRenderer::new\);'),
    ],
    "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java": [
        re.compile(r'\n\tpublic static final DeferredItem<Item> RED_STANGE_CLOUD_SPAWN_EGG;'),
        re.compile(r'\n\t\tRED_STANGE_CLOUD_SPAWN_EGG = REGISTRY\.register\("red_stange_cloud_spawn_egg".*?\);'),
    ],
    "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModTabs.java": [
        re.compile(r'\n\t\ttabData\.accept\(McanomalyarchivesModItems\.RED_STANGE_CLOUD_SPAWN_EGG\.get\(\)\);'),
    ],
}
for path, pats in removals.items():
    text = read(path)
    total = 0
    for pat in pats:
        text, n = pat.subn("", text)
        total += n
    write(path, text)
    print("%s：删掉 %d 处" % (os.path.basename(path), total))

# ---- 3) 见闻录里多加的那个 id ----
codex = "src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java"
text = read(codex)
new = text.replace("mcanomalyarchives:black_stange_cloud|mcanomalyarchives:red_stange_cloud\"),",
                   "mcanomalyarchives:black_stange_cloud\"),")
if new != text:
    write(codex, new)
    print("见闻录：去掉 red_stange_cloud 这个 id（变种与普通小伪云同类，用同一个 id 解锁）")

# ---- 4) 工作区：mod_elements 条目 + 词条 ----
raw = read(WS)
i = raw.find('"name": "RedStangeCloud"')
if i >= 0:
    start = raw.rfind("{", 0, i)
    depth, j, in_str, esc = 0, start, False, False
    while j < len(raw):
        c = raw[j]
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
                    break
        j += 1
    end = j + 1
    # 连前后逗号/换行一起吃掉
    tail = raw[end:end + 2]
    if raw[start - 1:start].strip() == ",":
        start -= 1
    elif tail.strip().startswith(","):
        end += 2 if tail.startswith(",\r") else 1
    raw = raw[:start] + raw[end:]
    print("工作区：已移除 mod_elements 里的 RedStangeCloud 条目")

for key in DEAD_KEYS:
    raw, n = drop_key(raw, key)
    print("工作区 language_map：删词条 %s x%d" % (key, n))
raw = re.sub(r",(\s*[}\]])", r"\1", raw)
json.loads(raw)
write(WS, raw)

# ---- 5) canonical / lang 产物 ----
for path in list(CANON.values()) + list(LANG.values()):
    text = read(path)
    total = 0
    for key in DEAD_KEYS:
        text, n = drop_key(text, key)
        total += n
    text = re.sub(r",(\s*[}\]])", r"\1", text)
    if path in LANG.values():
        json.loads(text)
    write(path, text)
    print("%s：删掉 %d 条词条" % (os.path.basename(path), total))

# ---- 6) 守卫：删掉指向被删文件的规则，补一条"不许再出现独立实体"的反向检查 ----
m = json.load(io.open(MANIFEST, encoding="utf-8"))
before = len(m["items"])
m["items"] = [i for i in m["items"] if i["id"] not in DEAD_RULES]
for item in m["items"]:
    if item["id"] == "codex-uo001-multi-id":
        item["mustContain"] = [x for x in item["mustContain"] if "red_stange_cloud" not in x]
    if item["id"] in ("red-cloud-assets",):
        item["exists"] = [p for p in item["exists"] if "spawn_egg" not in p]
        item["why"] = "红伪云变种用的那张红贴图（它不再有独立的刷怪蛋：变种不是新生物）"
        item["fixHint"] = "重跑 python tools/cloud/make_red_texture.py"
    if item["id"] in ("lang-zh-extra", "lang-en-extra"):
        item["expectAll"] = [k for k in item["expectAll"] if "red_stange_cloud" not in k]

for item in m["items"]:
    if item["id"] == "red-cloud-addiction":
        item["mustContain"] = ["isPlayerLookingAtRedCloud(", "isEntityLookingAtRedCloud(", "findRedCloudInView(",
                               "CLOUD_COS_LIMIT", "boolean watchingCloud;", "!state.watchingCloud", "isRed()"]
        item["why"] = ("「看红伪云会上瘾」接在既有虞美人上瘾系统里（同一套 10 秒上瘾/升级/定身/丧命）；"
                       "红伪云不是独立生物，靠 SmallStangeCloudEntity.isRed() 这个同步标记区分")

if "no-separate-red-entity" not in {i["id"] for i in m["items"]}:
    m["items"].append({
        "id": "no-separate-red-entity",
        "kind": "noMatch",
        "why": "反向检查：红伪云是**小伪云的变种**，不许再注册成一个独立生物"
               "（作者 2026-09-18：红伪云作为伪云的一个变种，不要再加一个生物）",
        "fixHint": "红伪云靠 SmallStangeCloudEntity.isRed() 同步标记 + 渲染器换贴图实现；"
                   "不要再加 register(\"red_stange_cloud\") 或刷怪蛋",
        "glob": "*.java",
        "searchRoot": "src/main/java",
        "pattern": "red_stange_cloud|RED_STANGE_CLOUD|RedStangeCloud",
    })
    print("+ 规则 no-separate-red-entity")

with io.open(MANIFEST, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(m, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("守卫规则 %d -> %d" % (before, len(m["items"])))
print("DONE")
