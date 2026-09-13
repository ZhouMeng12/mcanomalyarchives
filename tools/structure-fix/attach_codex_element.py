# -*- coding: utf-8 -*-
"""把见闻录物品挂进 MCreator：物品注册表 + 模组主类 init + 工作区元素（locked_code=true）"""
import re, json, time, shutil

def nl_of(path):
    """按文件实际行尾工作，避免把 CRLF 文件改成 LF"""
    with open(path, 'rb') as f:
        return "\r\n" if b"\r\n" in f.read(4096) else "\n"

# 注意：Python 文本模式读入时会把 CRLF 规范化成 \n，
# 所以【内存里一律用 \n 拼接】，写回时再由文本模式还原成系统的 CRLF。
NL = "\n"

# 1) 物品注册表
p = 'src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java'
s = open(p, encoding='utf-8').read()
if 'ANOMALY_CODEX' not in s:
    decls = list(re.finditer(r'\tpublic static final DeferredItem<Item> [A-Z0-9_]+;' + NL, s))
    last = decls[-1]
    s = s[:last.end()] + '\tpublic static final DeferredItem<Item> ANOMALY_CODEX;' + NL + s[last.end():]
    m = re.search(r'\t\tCLOUD_INGOT = REGISTRY\.register\("cloud_ingot"[^\r\n]*' + NL, s)
    ins = '\t\tANOMALY_CODEX = REGISTRY.register("anomaly_codex", AnomalyCodexItem::new);' + NL
    s = s[:m.start()] + ins + s[m.start():]
    if 'item.AnomalyCodexItem' not in s:
        s = s.replace('import net.mcreator.mcanomalyarchives.item.CloudIngotItem;',
                      'import net.mcreator.mcanomalyarchives.item.AnomalyCodexItem;' + NL
                      + 'import net.mcreator.mcanomalyarchives.item.CloudIngotItem;')
    open(p, 'w', encoding='utf-8').write(s)
    print('OK 物品注册表已更新')
else:
    print('-- 物品注册表已有 ANOMALY_CODEX')

# 2) 模组主类 init（写在 user code block 里，MCreator 会保留）
m = 'src/main/java/net/mcreator/mcanomalyarchives/McanomalyarchivesMod.java'
s = open(m, encoding='utf-8').read()
if 'CodexScanner.init()' not in s:
    anchor = '\t\tnet.mcreator.mcanomalyarchives.client.BlinkClientHandler.init();' + NL
    add = (anchor
           + '\t\tnet.mcreator.mcanomalyarchives.codex.CodexScanner.init(); // 见闻录：周期扫描解锁' + NL
           + '\t\tnet.mcreator.mcanomalyarchives.codex.CodexItemHandler.init(); // 见闻录：右键打开' + NL)
    assert anchor in s, '找不到 BlinkClientHandler.init() 锚点'
    s = s.replace(anchor, add)
    open(m, 'w', encoding='utf-8').write(s)
    print('OK 模组主类已注册 codex init')
else:
    print('-- 模组主类已有 codex init')

# 3) 工作区挂元素
w = 'mcanomalyarchives.mcreator'
raw = open(w, encoding='utf-8').read()
if '"AnomalyCodex"' not in raw:
    entry = (NL.join([
        '    {',
        '      "name": "AnomalyCodex",',
        '      "type": "item",',
        '      "compiles": true,',
        '      "locked_code": true,',
        '      "registry_name": "anomaly_codex",',
        '      "metadata": {',
        '        "files": [',
        '          "src/main/java/net/mcreator/mcanomalyarchives/item/AnomalyCodexItem.java",',
        '          "src/main/resources/assets/mcanomalyarchives/models/item/anomaly_codex.json"',
        '        ]',
        '      },',
        '      "path": "~/StrangeRecord"',
        '    }']))
    m2 = re.search(r'"mod_elements"\s*:\s*\[', raw)
    start = m2.end() - 1
    i, depth, in_str, esc = start, 0, False, False
    while i < len(raw):
        c = raw[i]
        if in_str:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == '[':
                depth += 1
            elif c == ']':
                depth -= 1
                if depth == 0:
                    break
        i += 1
    shutil.copy(w, w + '.bak-' + time.strftime('%Y%m%d-%H%M%S'))
    open(w, 'w', encoding='utf-8').write(raw[:i].rstrip() + ',' + NL + entry + NL + '  ' + raw[i:])
    ws = json.loads(open(w, encoding='utf-8').read())
    print('OK 工作区元素数:', len(ws['mod_elements']),
          ' 已挂载:', any(e['name'] == 'AnomalyCodex' for e in ws['mod_elements']))
else:
    print('-- 工作区已有 AnomalyCodex')
