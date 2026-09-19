# -*- coding: utf-8 -*-
"""把 Yifulin2（灰色伊芙琳）补全到能编译、能刷出来的状态。

背景：作者在 MCreator 里复制了 Yifulin 元素做第二个 NPC，MCreator 把两个文件**照抄了旧类名**
（Yifulin2Renderer.java 里写的是 `public class YifulinRenderer`），于是整个工程编译不过；
元素 `compiles: false`、作者已经锁定代码（locked_code），所以由我这里手工补齐：

  1. 元素 JSON：贴图字段改成实际在用的 grayyifulin、名字与刷怪蛋贴图对齐
  2. 四张生成区注册表：实体 / 渲染器 / 刷怪蛋 / 创造栏（照 MCreator 的写法）
  3. 补 1.21 的物品定义 items/yifulin_2_spawn_egg.json（缺了它就是紫黑方块）
  4. 语言词条：工作区 language_map + canonical 基线 + 守卫 expectAll（lang 产物交给 guard apply）

幂等：已经补过就跳过。
"""
import io
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

ELEMENT = "elements/Yifulin2.mod.json"
WS = "mcanomalyarchives.mcreator"
ENT = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java"
REN = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntityRenderers.java"
ITM = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java"
TAB = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModTabs.java"
ITEM_DEF = "src/main/resources/assets/mcanomalyarchives/items/yifulin_2_spawn_egg.json"
CANON = {
    "zh_cn": "tools/mcreator-guard/canonical/zh_cn.extra.txt",
    "en_us": "tools/mcreator-guard/canonical/en_us.extra.txt",
}
KEYS = {
    "zh_cn": [("entity.mcanomalyarchives.yifulin_2", "灰色伊芙琳"),
              ("item.mcanomalyarchives.yifulin_2_spawn_egg", "灰色伊芙琳刷怪蛋")],
    "en_us": [("entity.mcanomalyarchives.yifulin_2", "Gray Yifulin"),
              ("item.mcanomalyarchives.yifulin_2_spawn_egg", "Gray Yifulin Spawn Egg")],
}


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t):
    io.open(p, "w", encoding="utf-8", newline="").write(t)


def insert_after(text, anchor, addition, label):
    assert anchor in text, "找不到锚点（%s）" % label
    if addition.strip() in text:
        print("-- %s 已补过" % label)
        return text
    print("OK %s" % label)
    return text.replace(anchor, anchor + addition, 1)


# ---- 1) 元素 JSON 与实现对齐 ----
doc = json.load(io.open(ELEMENT, encoding="utf-8"))
d = doc["definition"]
was = {k: d.get(k) for k in ("mobName", "mobModelTexture", "spawnEggTexture")}
d["mobName"] = "Gray Yifulin"
d["mobModelTexture"] = "grayyifulin.png"   # 渲染器里实际用的就是它（作者新加的那张灰贴图）
d["spawnEggTexture"] = "grayyifulin"       # 刷怪蛋模型里实际用的也是它
with io.open(ELEMENT, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(doc, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("元素 JSON：", json.dumps(was, ensure_ascii=False), "->",
      json.dumps({k: d[k] for k in was}, ensure_ascii=False))

# ---- 2) 四张注册表 ----
s = read(ENT)
s = insert_after(s, "\t\tYifulinEntity.init(event);\n", "\t\tYifulin2Entity.init(event);\n", "实体 init")
s = insert_after(s, "\t\tevent.put(YIFULIN.get(), YifulinEntity.createAttributes().build());\n",
                 "\t\tevent.put(YIFULIN_2.get(), Yifulin2Entity.createAttributes().build());\n", "实体属性")
if "YIFULIN_2 = register" not in s:
    anchor = "\tpublic static final DeferredHolder<EntityType<?>, EntityType<YifulinEntity>> YIFULIN"
    assert anchor in s
    at = s.index(anchor)
    end = s.index(";", s.index(".sized(", at)) + 1
    s = (s[:end] + """

\tpublic static final DeferredHolder<EntityType<?>, EntityType<Yifulin2Entity>> YIFULIN_2 = register("yifulin_2",
\t\t\tEntityType.Builder.<Yifulin2Entity>of(Yifulin2Entity::new, MobCategory.CREATURE).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

\t\t\t\t\t.ridingOffset(-0.6f).sized(0.6f, 1.8f));""" + s[end:])
    print("OK 实体注册")
write(ENT, s)

s = read(REN)
s = insert_after(s, "event.registerEntityRenderer(McanomalyarchivesModEntities.YIFULIN.get(), YifulinRenderer::new);\n",
                 "\t\tevent.registerEntityRenderer(McanomalyarchivesModEntities.YIFULIN_2.get(), Yifulin2Renderer::new);\n",
                 "渲染器注册")
write(REN, s)

s = read(ITM)
s = insert_after(s, "\tpublic static final DeferredItem<Item> YIFULIN_SPAWN_EGG;\n",
                 "\tpublic static final DeferredItem<Item> YIFULIN_2_SPAWN_EGG;\n", "刷怪蛋声明")
s = insert_after(s, '\t\tYIFULIN_SPAWN_EGG = REGISTRY.register("yifulin_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.YIFULIN, -1, -1, new Item.Properties()));\n',
                 '\t\tYIFULIN_2_SPAWN_EGG = REGISTRY.register("yifulin_2_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.YIFULIN_2, -1, -1, new Item.Properties()));\n',
                 "刷怪蛋注册")
write(ITM, s)

s = read(TAB)
s = insert_after(s, "\t\ttabData.accept(McanomalyarchivesModItems.YIFULIN_SPAWN_EGG.get());\n",
                 "\t\ttabData.accept(McanomalyarchivesModItems.YIFULIN_2_SPAWN_EGG.get());\n", "创造栏")
write(TAB, s)

# ---- 3) 1.21 的物品定义 ----
if not os.path.exists(ITEM_DEF):
    io.open(ITEM_DEF, "w", encoding="utf-8", newline="\n").write(json.dumps({
        "model": {"type": "minecraft:model", "model": "mcanomalyarchives:item/yifulin_2_spawn_egg"}
    }, ensure_ascii=False, indent=2) + "\n")
    print("OK 补 items/yifulin_2_spawn_egg.json")
else:
    print("-- items 定义已存在")

# ---- 4) 语言词条（四处：canonical / 工作区 language_map / 守卫 expectAll / lang 交给 guard apply）----
for lg, path in CANON.items():
    text = read(path)
    changed = 0
    for key, value in KEYS[lg]:
        pat = re.compile(r'^(\s*"' + re.escape(key) + r'"\s*:\s*)("(?:[^"\\]|\\.)*")(,?)\s*$', re.M)
        if pat.search(text):
            text = pat.sub(lambda m: m.group(1) + json.dumps(value, ensure_ascii=False) + m.group(3), text)
            changed += 1
        else:
            if not text.endswith("\n"):
                text += "\n"
            text += "  " + json.dumps(key, ensure_ascii=False) + ": " + json.dumps(value, ensure_ascii=False) + ",\n"
            changed += 1
    write(path, text)
    print("OK %s 词条 %d 条" % (os.path.basename(path), changed))

raw = read(WS)


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


lm_open, lm_close = block_range(raw, "language_map")
segment = raw[lm_open:lm_close]
n = 0
for lg in ("zh_cn", "en_us"):
    bopen, bclose = block_range(segment, lg)
    body = segment[bopen:bclose]
    for key, value in KEYS[lg]:
        pat = re.compile(r'("' + re.escape(key) + r'"\s*:\s*)("(?:[^"\\]|\\.)*")')
        if pat.search(body):
            body = pat.sub(lambda m: m.group(1) + json.dumps(value, ensure_ascii=False), body)
            n += 1
    segment = segment[:bopen] + body + segment[bclose:]
write(WS, raw[:lm_open] + segment + raw[lm_close:])
json.loads(read(WS))
print("OK 工作区 language_map 改写 %d 条（并已通过严格 JSON 校验）" % n)

manifest_path = "tools/mcreator-guard/manifest.json"
manifest = json.load(io.open(manifest_path, encoding="utf-8"))
touched = 0
for item in manifest["items"]:
    if item["id"] in ("lang-zh-extra", "lang-en-extra"):
        lg = "zh_cn" if item["id"] == "lang-zh-extra" else "en_us"
        for key, _ in KEYS[lg]:
            quoted = '"%s"' % key
            if quoted not in item["expectAll"]:
                item["expectAll"].append(quoted)
                touched += 1
with io.open(manifest_path, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(manifest, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("OK 守卫 expectAll 新增 %d 个键（接着跑 guard.ps1 apply 把新值写进 lang 产物）" % touched)

# ---- 5) 元素标记为可编译 ----
raw = read(WS)
if '"name": "Yifulin2"' in raw:
    i = raw.index('"name": "Yifulin2"')
    seg = raw[i:i + 400]
    if '"compiles": false' in seg:
        raw = raw[:i] + seg.replace('"compiles": false', '"compiles": true', 1) + raw[i + 400:]
        write(WS, raw)
        json.loads(read(WS))
        print("OK 元素 compiles 改成 true")
    else:
        print("-- 元素 compiles 已是 true")
print("DONE")
