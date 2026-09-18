# -*- coding: utf-8 -*-
"""把小伪云（UO-001 衍生个体）挂进 MCreator 工作区。

照 tools/nametag/attach_nametag.py 与 fix_nametag_lang.py 的既定做法（那两次都验证过）：

  1. mcanomalyarchives.mcreator 的 mod_elements 增一条 locked_code=true 的记录；
  2. 语言词条写四处，少一处就会被 MCreator 或守卫写回去：
       - mcanomalyarchives.mcreator 的 language_map（MCreator 重写 lang 的来源）
       - tools/mcreator-guard/canonical/{zh_cn,en_us}.extra.txt（守卫的插入基线）
       - src/main/resources/assets/mcanomalyarchives/lang/{zh_cn,en_us}.json（当前产物）

Java 侧的注册行（实体/渲染器/刷怪蛋）由人手工改，不在这里做——那几行在生成区，
改法需要看上下文，脚本盲插容易插错位置。
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
CANON = {
    "zh_cn": "tools/mcreator-guard/canonical/zh_cn.extra.txt",
    "en_us": "tools/mcreator-guard/canonical/en_us.extra.txt",
}
LANG = {
    "zh_cn": "src/main/resources/assets/mcanomalyarchives/lang/zh_cn.json",
    "en_us": "src/main/resources/assets/mcanomalyarchives/lang/en_us.json",
}

ELEMENT_NAME = "SmallStangeCloud"
REGISTRY_NAME = "small_stange_cloud"
ELEMENT_PATH = "~/StrangeCloud"          # 与大伪云同一个元素树分组
ELEMENT_FILES = [
    "src/main/java/net/mcreator/mcanomalyarchives/entity/SmallStangeCloudEntity.java",
    "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/SmallStangeCloudRenderer.java",
    "src/main/resources/assets/mcanomalyarchives/models/item/small_stange_cloud_spawn_egg.json",
    "src/main/resources/assets/mcanomalyarchives/items/small_stange_cloud_spawn_egg.json",
]

NEW_LANG = {
    "zh_cn": [
        ("entity.mcanomalyarchives.small_stange_cloud", "小伪云"),
        ("item.mcanomalyarchives.small_stange_cloud_spawn_egg", "小伪云刷怪蛋"),
    ],
    "en_us": [
        ("entity.mcanomalyarchives.small_stange_cloud", "Small Stange Cloud"),
        ("item.mcanomalyarchives.small_stange_cloud_spawn_egg", "Small Stange Cloud Spawn Egg"),
    ],
}


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t, backup=False):
    if backup:
        shutil.copy(p, p + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    io.open(p, "w", encoding="utf-8", newline="").write(t)


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


def add_keys(body, pairs, indent):
    """把若干条 "k": "v", 追加到一个 JSON 对象体末尾（pairs 里已存在的跳过）。

    坑：语言块里**最后一条是可能没有后置逗号**的（MCreator 自己写出来的就是这样），
    直接往后追加会变成 `"a": "1"\n"b": "2"` 这种非法 JSON —— 上一版就踩了这个，
    把工作区文件写坏了（靠 .bak 回滚）。所以：先去掉可能的尾逗号，再**补一个逗号**，
    然后才追加新条目。
    """
    have = set(re.findall(r'"((?:[^"\\]|\\.)*)"\s*:', body))
    add = [(k, v) for k, v in pairs if k not in have]
    if not add:
        return body, 0
    stripped = re.sub(r",\s*$", "", body.rstrip())
    if stripped.rstrip().endswith("{"):
        head = stripped.rstrip()  # 空对象，直接追加不用逗号
    else:
        head = stripped + ","
    lines = []
    for i, (k, v) in enumerate(add):
        tail = "" if i == len(add) - 1 else ","   # 最后一条不带逗号（对象里不允许尾逗号）
        lines.append(indent + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) + tail)
    return head + NL + NL.join(lines) + NL, len(add)


# ---------- 1) 工作区元素 ----------
raw = read(WS)
if '"%s"' % ELEMENT_NAME in raw:
    print("-- 工作区已有 %s" % ELEMENT_NAME)
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
        ["        ]",
         "      },",
         '      "path": "%s"' % ELEMENT_PATH,
         "    }"])
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
    raw = read(WS)
    ws = json.loads(raw)
    print("OK 工作区元素数:", len(ws["mod_elements"]),
          " 已挂载:", any(e["name"] == ELEMENT_NAME for e in ws["mod_elements"]))

# ---------- 2) 工作区 language_map ----------
raw = read(WS)
lm_open, lm_close = block_range(raw, "language_map")
assert lm_open is not None, "工作区里找不到 language_map"
segment = raw[lm_open:lm_close]
changed = 0
for lg in ("zh_cn", "en_us"):
    r = block_range(segment, lg)
    assert r, "工作区里找不到 language_map.%s" % lg
    bopen, bclose = r
    body, n = add_keys(segment[bopen:bclose], NEW_LANG[lg], "      ")
    changed += n
    segment = segment[:bopen] + body + segment[bclose:]
if changed:
    shutil.copy(WS, WS + ".bak-" + time.strftime("%Y%m%d-%H%M%S"))
    write(WS, raw[:lm_open] + segment + raw[lm_close:])
    print("OK language_map 新增 %d 条词条" % changed)
else:
    print("-- language_map 已是最新")
ws = json.loads(read(WS))
for lg in ("en_us", "zh_cn"):
    d = ws["language_map"][lg]
    miss = [k for k, _ in NEW_LANG[lg] if k not in d]
    print("   language_map.%-5s 共 %d 条, 缺 %s" % (lg, len(d), miss or "无"))

# ---------- 3) canonical 基线（lang 产物交给 guard apply 插） ----------
# 注意：**不要**自己往 assets/.../lang/*.json 里追加。那是完整 JSON 对象，
# 直接往文件末尾追加会写到最后那个 `}` 之后（上一版就踩了这个）。
# 正确路径是：canonical 基线 → `guard.ps1 apply` 用 insertBefore 规则插到 `}` 之前，
# 逗号处理与去重都由 guard 负责（guard.ps1:182-212）。
for lg, path in CANON.items():
    text = read(path)
    pairs = NEW_LANG[lg]
    have = set(re.findall(r'^\s*"((?:[^"\\]|\\.)*)"\s*:', text, re.M))
    add = [(k, v) for k, v in pairs if k not in have]
    if not add:
        print("-- %s 已是最新" % path)
        continue
    if not text.endswith(NL):
        text += NL
    lines = ["  " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) + "," for k, v in add]
    write(path, text + NL.join(lines) + NL)
    print("OK %s 新增 %d 条（接着跑 guard.ps1 apply 同步进 lang 产物）" % (path, len(add)))

print("DONE")
