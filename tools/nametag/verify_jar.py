# -*- coding: utf-8 -*-
"""构建产物自检：确认 jar 里真的带上了百变命名牌需要的一切。

只要有一项缺席，游戏里就会表现为"物品是紫黑方块 / 名字全部解析不出 / 矿井开不出牌子 / 图鉴是问号"，
而编译与构建全程不会有任何报错——所以必须验产物。
"""
import io
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

jars = [f for f in os.listdir("build/libs") if f.endswith(".jar") and "sources" not in f]
if not jars:
    raise SystemExit("build/libs 下没有 jar，先跑 gradlew build")
jar = os.path.join("build/libs", jars[0])
print("jar:", jar, os.path.getsize(jar), "bytes")

FAILED = []
NEED = [
    ("物品贴图", "assets/mcanomalyarchives/textures/item/baibiannametag.png"),
    ("物品模型", "assets/mcanomalyarchives/models/item/baibian_name_tag.json"),
    ("中文名索引", "data/mcanomalyarchives/name_index/zh_cn.json"),
    ("英文名索引", "data/mcanomalyarchives/name_index/en_us.json"),
    ("口语别名表", "data/mcanomalyarchives/name_index/alias_zh_cn.json"),
    ("别名表(英)", "data/mcanomalyarchives/name_index/alias_en_us.json"),
    ("战利品注入", "data/mcanomalyarchives/loot_modifiers/mineshaft_name_tag.json"),
    ("战利品子表", "data/mcanomalyarchives/loot_table/nametag/mineshaft_inject.json"),
    ("GLM 注册表", "data/neoforge/loot_modifiers/global_loot_modifiers.json"),
]
CLASSES = [
    "net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagHandler.class",
    "net/mcreator/mcanomalyarchives/anomaly/nametag/NameTagTicker.class",
    "net/mcreator/mcanomalyarchives/anomaly/nametag/NameResolver.class",
    "net/mcreator/mcanomalyarchives/anomaly/nametag/MaterialUnits.class",
    "net/mcreator/mcanomalyarchives/anomaly/nametag/effects/ItemNaming.class",
    "net/mcreator/mcanomalyarchives/anomaly/nametag/effects/EntityNaming.class",
    "net/mcreator/mcanomalyarchives/item/BaibianNameTagItem.class",
]

with zipfile.ZipFile(jar) as zf:
    names = set(zf.namelist())
    print("\n=== 资源与类 ===")
    for label, path in NEED + [("类:" + os.path.basename(c), c) for c in CLASSES]:
        ok = path in names
        print("  [%s] %-18s %s" % ("OK " if ok else "FAIL", label, path))
        if not ok:
            FAILED.append("jar 缺少 " + path)

    print("\n=== 语言词条 ===")
    for lg, keys in (
        ("zh_cn", ["item.mcanomalyarchives.baibian_name_tag", "nametag.mcanomalyarchives.tooltip",
                   "nametag.mcanomalyarchives.unknown", "codex.mcanomalyarchives.uo008.info"]),
        ("en_us", ["item.mcanomalyarchives.baibian_name_tag", "nametag.mcanomalyarchives.tooltip"]),
    ):
        path = "assets/mcanomalyarchives/lang/%s.json" % lg
        data = json.loads(zf.read(path).decode("utf-8"))
        for k in keys:
            ok = k in data
            print("  [%s] %s : %s" % ("OK " if ok else "FAIL", k, data.get(k, "<缺失>")))
            if not ok:
                FAILED.append("%s 缺少词条 %s" % (path, k))
        if "辐射污染" in json.dumps(data, ensure_ascii=False):
            FAILED.append("%s 里还残留旧的 UO-008 占位文案（辐射污染）" % path)

    print("\n=== 索引内容抽查（直接从 jar 里读）===")
    idx = json.loads(zf.read("data/mcanomalyarchives/name_index/zh_cn.json").decode("utf-8"))
    for name, want in (("鸡", "entity:minecraft:chicken"), ("下界合金镐", "item:minecraft:netherite_pickaxe"),
                       ("钻石块", "block:minecraft:diamond_block"), ("绵羊", "entity:minecraft:sheep")):
        got = idx.get(name)
        ok = got is not None and want in got
        print("  [%s] %s -> %s" % ("OK " if ok else "FAIL", name, got))
        if not ok:
            FAILED.append("索引里 %s 解析错误：%r" % (name, got))

print()
if FAILED:
    print("产物自检失败 %d 项：" % len(FAILED))
    for f in FAILED:
        print("  -", f)
    sys.exit(1)
print("产物自检全部通过")
