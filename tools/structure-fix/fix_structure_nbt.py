#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
结构 NBT 命名空间修复 / 校验工具（MC诡异见闻录）

背景：模组从 strangerecord 改名为 mcanomalyarchives 后，src/main/resources 里的 JSON
（worldgen / template_pool / loot_table 等）都跟着改了，但结构模板 .nbt 是 gzip 压缩的
二进制，批量文本替换扫不到 —— 结果结构生成时调色板里的方块 ID 与实体 ID 全是旧命名空间，
方块变成空气、实体不生成。

设计原则：**只打字节级补丁，不重写文件**。
NBT 的字符串是「2 字节长度前缀 + UTF-8 内容」，改名后长度会变，所以必须同步改长度前缀。
但如果先把 NBT 解析成 Python 对象再序列化回去，标量类型会被"反推"（TAG_Byte/TAG_Short/
TAG_Float 都可能被写成 TAG_Int/TAG_Double），而 MC 读 NBT 是按类型查的
（例：CompoundTag.getByte 要求类型确为 TAG_Byte，否则返回 0）—— 这种静默类型漂移
会造成比原名更隐蔽的故障。
所以这里只做：解析定位（读）→ 在原始字节上就地把旧命名空间替换掉（写），
其余字节一个都不动，并在写回前自检「反向补丁能还原出原始字节」。

用法：
    python fix_structure_nbt.py dump              # 列出每个结构里的方块/实体
    python fix_structure_nbt.py fix  [--apply]    # 试运行 / 真正写入（写入前自动备份 + 自检）
    python fix_structure_nbt.py verify            # 校验结构引用的 ID 是否真实存在
"""

import gzip
import os
import re
import struct
import sys
import time

OLD_NS = "strangerecord"
NEW_NS = "mcanomalyarchives"

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STRUCTURE_DIR = os.path.join(ROOT, "src", "main", "resources", "data", NEW_NS, "structure")
RESOURCE_DIR = os.path.join(ROOT, "src", "main", "resources")
BACKUP_DIR = os.path.join(ROOT, "backup", "structure-nbt-bak")

TAG_NAMES = {1: "byte", 2: "short", 3: "int", 4: "long", 5: "float", 6: "double",
             7: "byte_array", 8: "string", 9: "list", 10: "compound", 11: "int_array",
             12: "long_array"}


class TList:
    __slots__ = ("etype", "items")

    def __init__(self, etype, items):
        self.etype = etype
        self.items = items


class Span:
    """一个 TAG_String 值在原始缓冲区里的位置（内容不含长度前缀）"""

    __slots__ = ("prefix", "start", "length", "text")

    def __init__(self, prefix, start, length, text):
        self.prefix = prefix
        self.start = start
        self.length = length
        self.text = text


# --------------------------------------------------------------------------- 读

def _read_string(buf, i, spans=None):
    (n,) = struct.unpack_from(">H", buf, i)
    body = i + 2
    text = buf[body:body + n].decode("utf-8")
    if spans is not None:
        spans.append(Span(i, body, n, text))
    return text, body + n


def _read_payload(buf, i, tid, spans=None):
    if tid == 1:
        return struct.unpack_from(">b", buf, i)[0], i + 1
    if tid == 2:
        return struct.unpack_from(">h", buf, i)[0], i + 2
    if tid == 3:
        return struct.unpack_from(">i", buf, i)[0], i + 4
    if tid == 4:
        return struct.unpack_from(">q", buf, i)[0], i + 8
    if tid == 5:
        return struct.unpack_from(">f", buf, i)[0], i + 4
    if tid == 6:
        return struct.unpack_from(">d", buf, i)[0], i + 8
    if tid == 7:
        (n,) = struct.unpack_from(">i", buf, i)
        return bytes(buf[i + 4:i + 4 + n]), i + 4 + n
    if tid == 8:
        return _read_string(buf, i, spans)
    if tid == 9:
        (etype,) = struct.unpack_from(">B", buf, i)
        (n,) = struct.unpack_from(">i", buf, i + 1)
        i += 5
        items = []
        for _ in range(n):
            v, i = _read_payload(buf, i, etype, spans)
            items.append(v)
        return TList(etype, items), i
    if tid == 10:
        d = {}
        while True:
            (t,) = struct.unpack_from(">B", buf, i)
            i += 1
            if t == 0:
                return d, i
            key, i = _read_string(buf, i)          # 键名不登记 span（不会被改名）
            v, i = _read_payload(buf, i, t, spans)
            d[key] = v
    if tid == 11:
        (n,) = struct.unpack_from(">i", buf, i)
        return list(struct.unpack_from(">%di" % n, buf, i + 4)), i + 4 + 4 * n
    if tid == 12:
        (n,) = struct.unpack_from(">i", buf, i)
        return list(struct.unpack_from(">%dq" % n, buf, i + 4)), i + 4 + 8 * n
    raise ValueError("未知 TAG 类型 %r @%d" % (tid, i))


def parse_nbt(buf):
    """返回 (root_tid, root_name, payload, 结束偏移, 所有字符串值的 span)"""
    spans = []
    (tid,) = struct.unpack_from(">B", buf, 0)
    name, i = _read_string(buf, 1)
    payload, i = _read_payload(buf, i, tid, spans)
    return tid, name, payload, i, spans


def load(path):
    with open(path, "rb") as f:
        raw = f.read()
    data = gzip.decompress(raw)
    tid, name, payload, end, spans = parse_nbt(data)
    if end != len(data):
        raise ValueError("%s 解析后仍余 %d 字节，NBT 结构异常，拒绝处理"
                         % (path, len(data) - end))
    return data, tid, name, payload, spans


def save(path, data):
    with open(path, "wb") as f:
        f.write(gzip.compress(data, compresslevel=9, mtime=0))


# --------------------------------------------------------------- 字节级补丁

def patch_namespace(data, spans):
    """在原始字节上就地替换命名空间，返回 (新字节, 替换条数)"""
    todo = []
    for sp in spans:
        if OLD_NS + ":" not in sp.text:
            continue
        new_text = sp.text.replace(OLD_NS + ":", NEW_NS + ":")
        new_bytes = new_text.encode("utf-8")
        todo.append((sp, new_bytes))
    if not todo:
        return data, 0
    # 从后往前改，前面的偏移才不会失效
    out = bytearray(data)
    for sp, new_bytes in sorted(todo, key=lambda t: -t[0].start):
        end = sp.start + sp.length
        out[sp.start:end] = new_bytes
        out[sp.prefix:sp.prefix + 2] = struct.pack(">H", len(new_bytes))
    # 自检：把新字节反向还原，必须与原始字节逐字节相同。
    # 注意方向：正向补丁要【降序】（否则前面替换后偏移全变），
    # 反向还原要【升序】（先把低位恢复回原长度，后面的偏移才回到原位）。
    back = bytearray(out)
    for sp, new_bytes in sorted(todo, key=lambda t: t[0].start):
        end = sp.start + len(new_bytes)
        back[sp.start:end] = sp.text.encode("utf-8")[:sp.length]
        back[sp.prefix:sp.prefix + 2] = struct.pack(">H", sp.length)
    if bytes(back) != bytes(data):
        raise AssertionError("自检失败：除目标字符串外的字节被改动了，已放弃写入")
    return bytes(out), len(todo)


# ------------------------------------------------------------------ 分析

def walk_strings(node, path=""):
    if isinstance(node, str):
        yield path, node
    elif isinstance(node, dict):
        for k, v in node.items():
            yield from walk_strings(v, path + "/" + str(k))
    elif isinstance(node, TList):
        for idx, v in enumerate(node.items):
            yield from walk_strings(v, "%s[%d]" % (path, idx))


NS_RE = re.compile(r"[a-z0-9_.-]+:[a-z0-9_/.\-]+")


def classify(path):
    p = path.lower()
    if "/palette" in p:
        return "block"
    if "entit" in p:
        return "entity"
    return "other"


def collect_ids(node):
    buckets = {"block": set(), "entity": set(), "other": set()}
    for path, s in walk_strings(node):
        for m in NS_RE.finditer(s):
            buckets[classify(path)].add(m.group(0))
    return buckets


def structure_files():
    if not os.path.isdir(STRUCTURE_DIR):
        return []
    return [os.path.join(STRUCTURE_DIR, n) for n in sorted(os.listdir(STRUCTURE_DIR))
            if n.endswith(".nbt")]


def known_mod_ids():
    ids = set()
    assets = os.path.join(RESOURCE_DIR, "assets", NEW_NS)
    for sub in ("blockstates", "models/block", "models/item", "items", "textures/block"):
        d = os.path.join(assets, *sub.split("/"))
        if not os.path.isdir(d):
            continue
        for n in os.listdir(d):
            base = n.split(".")[0].replace("_generated", "")
            ids.add("%s:%s" % (NEW_NS, base))
    ent_file = os.path.join(ROOT, "src", "main", "java", "net", "mcreator", NEW_NS,
                            "init", "McanomalyarchivesModEntities.java")
    if os.path.isfile(ent_file):
        with open(ent_file, encoding="utf-8") as f:
            for m in re.finditer(r'register\("([a-z0-9_]+)"', f.read()):
                ids.add("%s:%s" % (NEW_NS, m.group(1)))
    return ids


# ------------------------------------------------------------------ 命令

def cmd_dump():
    for path in structure_files():
        _, _, name, payload, _ = load(path)
        buckets = collect_ids(payload)
        print("=" * 74)
        print("%s   (root=%r, %d 字节, size=%s)"
              % (os.path.basename(path), name, os.path.getsize(path), payload.get("size")))
        for label, key in (("方块(调色板)", "block"), ("实体", "entity"), ("其它引用", "other")):
            items = buckets[key]
            if not items:
                continue
            print("   %s (%d):" % (label, len(items)))
            for x in sorted(items):
                flag = "   <== 旧命名空间！" if x.startswith(OLD_NS + ":") else ""
                print("      %-46s%s" % (x, flag))
    return 0


def cmd_fix(apply):
    for path in structure_files():
        base = os.path.basename(path)
        data, tid, name, payload, spans = load(path)
        _, hits = patch_namespace(data, spans)   # 试算
        if not hits:
            print("  [跳过]   %-14s 没有旧命名空间" % base)
            continue
        affected = sorted({sp.text for sp in spans if OLD_NS + ":" in sp.text})
        if not apply:
            print("  [试运行] %-14s 将替换 %d 处：" % (base, hits))
            for a in affected:
                print("             %s  ->  %s" % (a, a.replace(OLD_NS + ":", NEW_NS + ":")))
            continue
        new_data, hits = patch_namespace(data, spans)
        # 写回前先备份
        if not os.path.isdir(BACKUP_DIR):
            os.makedirs(BACKUP_DIR)
        bak = os.path.join(BACKUP_DIR, base + ".before-" + time.strftime("%Y%m%d-%H%M%S"))
        if not os.path.exists(bak):
            with open(bak, "wb") as f:
                f.write(open(path, "rb").read())
        save(path, new_data)
        # 写回后立刻重新解析确认仍是合法 NBT
        re_data, _, _, re_payload, _ = load(path)
        assert re_data == new_data
        after = collect_ids(re_payload)
        left = [x for grp in after.values() for x in grp if x.startswith(OLD_NS + ":")]
        print("  [已修复] %-14s 替换 %d 处，写回后重新解析 OK，残留旧命名空间 %d 个"
              % (base, hits, len(left)))
    if not apply:
        print("\n加 --apply 才会真正写入。")
    return 0


def cmd_verify():
    known = known_mod_ids()
    bad = 0
    for path in structure_files():
        _, _, _, payload, _ = load(path)
        buckets = collect_ids(payload)
        print("=" * 74)
        print(os.path.basename(path))
        for x in sorted(buckets["block"] | buckets["entity"] | buckets["other"]):
            if x.startswith("minecraft:") or x.startswith("neoforge:"):
                continue
            ok = x in known
            if not ok:
                bad += 1
            print("   %-46s %s" % (x, "OK" if ok else "<== 模组里找不到这个 ID！"))
    print()
    if bad:
        print("有 %d 个引用在模组里不存在，结构生成时会被丢弃（方块变空气 / 实体不生成）。" % bad)
        return 1
    print("所有引用都能在模组里找到 ✓")
    return 0


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2
    if args[0] == "dump":
        return cmd_dump()
    if args[0] == "fix":
        return cmd_fix("--apply" in args)
    if args[0] == "verify":
        return cmd_verify()
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main())
