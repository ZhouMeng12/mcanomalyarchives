# -*- coding: utf-8 -*-
"""见闻录获取途径收尾：加守卫条目"""
import io, json

mp = "tools/mcreator-guard/manifest.json"
m = json.load(io.open(mp, encoding="utf-8"))
have = {it["id"] for it in m["items"]}

new = []
if "codex-origin-story" not in have:
    new.append({
        "id": "codex-origin-story",
        "kind": "exists",
        "why": "见闻录的获取途径（拿书找斯万过剧情 + 对话选择）：剧情调度、两个网络包、选择界面",
        "fixHint": "从 git 恢复 src/main/java/net/mcreator/mcanomalyarchives/codex/CodexOriginStory.java 等文件",
        "exists": [
            "src/main/java/net/mcreator/mcanomalyarchives/codex/CodexOriginStory.java",
            "src/main/java/net/mcreator/mcanomalyarchives/network/DialogueChoicePacket.java",
            "src/main/java/net/mcreator/mcanomalyarchives/network/DialogueChoiceAnswerPacket.java",
            "src/main/java/net/mcreator/mcanomalyarchives/client/dialogue/DialogueChoiceScreen.java",
        ],
    })
if "svan-codex-hook" not in have:
    new.append({
        "id": "svan-codex-hook",
        "kind": "contains",
        "why": "斯万交互里接入见闻录剧情的那段（ControllableMonster 是非生成区的手写基类，但仍要防误删）",
        "path": "src/main/java/net/mcreator/mcanomalyarchives/entity/ControllableMonster.java",
        "fixHint": "补回 CodexOriginStory.isBlankBook(heldItem) / tryStart(serverPlayer, this) 这段判断",
        "mustContain": ["CodexOriginStory.isBlankBook", "CodexOriginStory.tryStart"],
    })
if "codex-story-lines" not in have:
    new.append({
        "id": "codex-story-lines",
        "kind": "contains",
        "why": "剧情台词（集中在 DialogueDatabase，改台词只改这里）",
        "path": "src/main/java/net/mcreator/mcanomalyarchives/dialogue/DialogueDatabase.java",
        "fixHint": "补回 CODEX_INTRO / CODEX_BRANCH_A / CODEX_MIDDLE / CODEX_GIVE 等台词段",
        "mustContain": ["CODEX_INTRO", "CODEX_CHOICE_A_OPTIONS", "CODEX_GIVE", "CODEX_ALREADY"],
    })

if new:
    m["items"].extend(new)
    json.dump(m, io.open(mp, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    io.open(mp, "a", encoding="utf-8").write("\n")
print("新增 %d 项，共 %d 项" % (len(new), len(m["items"])))
