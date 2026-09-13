# -*- coding: utf-8 -*-
"""见闻录收尾：加守卫条目 + 整理 wiki 研究资料 + gitignore"""
import io, json, os, shutil, glob

# 1) 守卫清单：新增见闻录相关检查
mp = "tools/mcreator-guard/manifest.json"
m = json.load(io.open(mp, encoding="utf-8"))
have = {it["id"] for it in m["items"]}

new_items = []
if "codex-item-registration" not in have:
    new_items.append({
        "id": "codex-item-registration",
        "kind": "contains",
        "why": "见闻录物品在生成区注册表里的注册行（元素已挂进 workspace，MCreator 重生成会自己产出；这条只是防它被改坏）",
        "path": "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java",
        "fixHint": "确认 elements/AnomalyCodex.mod.json 在 workspace 里，然后让 MCreator 重新生成代码",
        "mustContain": ['ANOMALY_CODEX = REGISTRY.register("anomaly_codex"'],
    })
if "codex-init-hooks" not in have:
    new_items.append({
        "id": "codex-init-hooks",
        "kind": "contains",
        "why": "见闻录的初始化调用（user code block）：丢了则图鉴不扫描、右键打不开",
        "path": "src/main/java/net/mcreator/mcanomalyarchives/McanomalyarchivesMod.java",
        "fixHint": "补回 CodexScanner.init(); 与 CodexItemHandler.init();",
        "mustContain": ["CodexScanner.init()", "CodexItemHandler.init()"],
    })
if "codex-files" not in have:
    new_items.append({
        "id": "codex-files",
        "kind": "exists",
        "why": "见闻录系统本体（非生成区代码 + MCreator 元素 + 物品资源）",
        "fixHint": "从 git 恢复 src/main/java/net/mcreator/mcanomalyarchives/codex/ 与 client/codex/",
        "exists": [
            "src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java",
            "src/main/java/net/mcreator/mcanomalyarchives/codex/CodexScanner.java",
            "src/main/java/net/mcreator/mcanomalyarchives/codex/CodexItemHandler.java",
            "src/main/java/net/mcreator/mcanomalyarchives/client/codex/CodexScreen.java",
            "src/main/java/net/mcreator/mcanomalyarchives/network/CodexSyncPacket.java",
            "elements/AnomalyCodex.mod.json",
            "src/main/resources/assets/mcanomalyarchives/textures/item/anomalycodex.png",
            "src/main/resources/assets/mcanomalyarchives/models/item/anomaly_codex.json",
        ],
    })
if new_items:
    m["items"].extend(new_items)
    json.dump(m, io.open(mp, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    io.open(mp, "a", encoding="utf-8").write("\n")
print("清单新增 %d 项，共 %d 项" % (len(new_items), len(m["items"])))

# 2) wiki 研究资料归到 docs/ 下（原始网页正文不随仓库分发，加进 .gitignore）
target = os.path.join("docs", "codex-research")
os.makedirs(target, exist_ok=True)
moved = 0
for pattern in ("uo_scrape", "uo_dump"):
    for f in glob.glob(os.path.join(pattern, "*")):
        if os.path.isfile(f):
            shutil.move(f, os.path.join(target, os.path.basename(f)))
            moved += 1
    if os.path.isdir(pattern) and not os.listdir(pattern):
        os.rmdir(pattern)
print("已归档 %d 个研究文件到 %s" % (moved, target))

gi = ".gitignore"
s = io.open(gi, encoding="utf-8", newline="").read()
if "codex-research" not in s:
    s = s.rstrip("\r\n") + "\r\n\r\n# ===== wiki 原文抓取（CC BY-SA，仅本地研究用，不随仓库分发）=====\r\ndocs/codex-research/\r\n"
    io.open(gi, "w", encoding="utf-8", newline="").write(s)
    print(".gitignore 已补 docs/codex-research/")
