# -*- coding: utf-8 -*-
"""把百变命名牌（UO-008）挂进 MCreator：物品注册表 + 模组主类 init + 工作区元素 + 语言基线。

对应 tools/structure-fix/attach_codex_element.py 的做法（见闻录那次已经验证过）。
用户要求"命名牌在 MCreator 里做"，所以：
  * elements/BaibianNameTag.mod.json 是 MCreator 的元素定义（编辑器里能改）
  * workspace 的 mod_elements 里挂一条 locked_code=true 的记录，防止 MCreator 重写代码
  * 物品类 item/BaibianNameTagItem.java 保持最小实现，玩法全在非生成区 anomaly/nametag/
"""
import io
import json
import os
import re
import shutil
import time

NL = "\n"  # 内存里一律用 \n 拼接（文本模式读入已把 CRLF 规范化）
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

REGISTRY = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java"
MODMAIN = "src/main/java/net/mcreator/mcanomalyarchives/McanomalyarchivesMod.java"
WS = "mcanomalyarchives.mcreator"
CANON = {
    "zh_cn": "tools/mcreator-guard/canonical/zh_cn.extra.txt",
    "en_us": "tools/mcreator-guard/canonical/en_us.extra.txt",
}

NEW_LANG = {
    "zh_cn": [
        ("item.mcanomalyarchives.baibian_name_tag", "百变命名牌"),
        ("nametag.mcanomalyarchives.need_name", "这张命名牌还没写字——先放进铁砧改名"),
        ("nametag.mcanomalyarchives.unknown", "协会的档案里没有「%s」这个名字（它只认主流叫法）"),
        ("nametag.mcanomalyarchives.applied", "已命名：%s"),
        ("nametag.mcanomalyarchives.locked", "这个名字已经写死了——改不回来"),
        ("nametag.mcanomalyarchives.erased", "……什么都没留下"),
        ("nametag.mcanomalyarchives.planned", "该档案尚未被协会解明"),
        ("nametag.mcanomalyarchives.unstable", "它撑不住这个名字——别放到地上"),
        ("nametag.mcanomalyarchives.exploded", "差额用爆炸还了回来"),
        ("nametag.mcanomalyarchives.not_for_player", "命名牌对玩家不起作用"),
        ("nametag.mcanomalyarchives.no_output", "被改写的存在已经产不出原来的东西了"),
        ("nametag.mcanomalyarchives.draining", "它正从四周抽取%s"),
        ("nametag.mcanomalyarchives.dry", "附近找不到它能抽取的东西"),
        ("nametag.mcanomalyarchives.tooltip", "命名：%s · 剩余 %s 次"),
        ("nametag.mcanomalyarchives.egg", "%s蛋"),
    ],
    "en_us": [
        ("item.mcanomalyarchives.baibian_name_tag", "Baibian Name Tag"),
        ("nametag.mcanomalyarchives.need_name", "This tag has nothing written on it - rename it in an anvil first"),
        ("nametag.mcanomalyarchives.unknown", "The Association has no record of \"%s\" (it only accepts common names)"),
        ("nametag.mcanomalyarchives.applied", "Named: %s"),
        ("nametag.mcanomalyarchives.locked", "This name is already fixed - it cannot be changed back"),
        ("nametag.mcanomalyarchives.erased", "...nothing was left behind"),
        ("nametag.mcanomalyarchives.planned", "This record has not been deciphered by the Association yet"),
        ("nametag.mcanomalyarchives.unstable", "It cannot hold this name - do not place it down"),
        ("nametag.mcanomalyarchives.exploded", "The deficit was repaid as an explosion"),
        ("nametag.mcanomalyarchives.not_for_player", "The tag has no effect on players"),
        ("nametag.mcanomalyarchives.no_output", "A rewritten existence no longer yields what it used to"),
        ("nametag.mcanomalyarchives.draining", "It is draining %s from its surroundings"),
        ("nametag.mcanomalyarchives.dry", "Nothing nearby can be drained"),
        ("nametag.mcanomalyarchives.tooltip", "Named: %s - %s uses left"),
        ("nametag.mcanomalyarchives.egg", "%s Egg"),
    ],
}


def read(path):
    return io.open(path, encoding="utf-8").read()


def write(path, text, backup=False):
    if backup:
        shutil.copy(path, path + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    io.open(path, "w", encoding="utf-8", newline="").write(text)


# ---------- 1) 物品注册表 ----------
s = read(REGISTRY)
if "BAIBIAN_NAME_TAG" not in s:
    decls = list(re.finditer(r"\tpublic static final DeferredItem<Item> [A-Z0-9_]+;" + NL, s))
    last = decls[-1]
    s = s[:last.end()] + "\tpublic static final DeferredItem<Item> BAIBIAN_NAME_TAG;" + NL + s[last.end():]
    m = re.search(r'\t\tANOMALY_CODEX = REGISTRY\.register\("anomaly_codex"[^\r\n]*' + NL, s)
    assert m, "找不到 ANOMALY_CODEX 注册行作为锚点"
    ins = '\t\tBAIBIAN_NAME_TAG = REGISTRY.register("baibian_name_tag", BaibianNameTagItem::new);' + NL
    s = s[:m.end()] + ins + s[m.end():]
    if "item.BaibianNameTagItem" not in s and "import net.mcreator.mcanomalyarchives.item.*;" not in s:
        anchor = "import net.mcreator.mcanomalyarchives.item.AnomalyCodexItem;" + NL
        assert anchor in s, "找不到 item 包的 import 锚点"
        s = s.replace(anchor, anchor + "import net.mcreator.mcanomalyarchives.item.BaibianNameTagItem;" + NL)
    write(REGISTRY, s, backup=True)
    print("OK 物品注册表：已加入 BAIBIAN_NAME_TAG")
else:
    print("-- 物品注册表已有 BAIBIAN_NAME_TAG")

# ---------- 2) 模组主类 init ----------
s = read(MODMAIN)
if "NameTagHandler.init()" not in s:
    anchor = "\t\tnet.mcreator.mcanomalyarchives.codex.CodexItemHandler.init(); // 见闻录：右键打开" + NL
    assert anchor in s, "找不到 CodexItemHandler.init() 锚点"
    add = (anchor
           + "\t\tnet.mcreator.mcanomalyarchives.anomaly.nametag.NameTagHandler.init(); // 百变命名牌：右键实体 / 铁砧命名" + NL
           + "\t\tnet.mcreator.mcanomalyarchives.anomaly.nametag.NameTagTicker.init(); // 百变命名牌：行为覆盖层" + NL)
    s = s.replace(anchor, add)
    write(MODMAIN, s, backup=True)
    print("OK 模组主类：已注册 nametag init")
else:
    print("-- 模组主类已有 nametag init")

# ---------- 3) 工作区元素 ----------
raw = read(WS)
if '"BaibianNameTag"' not in raw:
    entry = (NL.join([
        "    {",
        '      "name": "BaibianNameTag",',
        '      "type": "item",',
        '      "compiles": true,',
        '      "locked_code": true,',
        '      "registry_name": "baibian_name_tag",',
        '      "metadata": {',
        '        "files": [',
        '          "src/main/java/net/mcreator/mcanomalyarchives/item/BaibianNameTagItem.java",',
        '          "src/main/resources/assets/mcanomalyarchives/models/item/baibian_name_tag.json"',
        "        ]",
        "      },",
        '      "path": "~/StrangeRecord"',
        "    }"]))
    m2 = re.search(r'"mod_elements"\s*:\s*\[', raw)
    assert m2, "工作区里找不到 mod_elements"
    start = m2.end() - 1
    i, depth, in_str, esc = start, 0, False, False
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
          " 已挂载:", any(e["name"] == "BaibianNameTag" for e in ws["mod_elements"]))
else:
    print("-- 工作区已有 BaibianNameTag")

# ---------- 4) 语言基线（canonical extra） ----------
for lg, pairs in NEW_LANG.items():
    path = CANON[lg]
    text = read(path)
    have = set(re.findall(r'^\s*"((?:[^"\\]|\\.)*)"\s*:', text, re.M))
    add = [(k, v) for k, v in pairs if k not in have]
    if not add:
        print("-- %s 基线已是最新" % lg)
        continue
    lines = ["  " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) + "," for k, v in add]
    if not text.endswith("\n"):
        text += NL
    write(path, text + NL.join(lines) + NL)
    print("OK %s 基线新增 %d 条" % (lg, len(add)))

print("DONE")
