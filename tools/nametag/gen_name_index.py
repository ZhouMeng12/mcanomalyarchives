#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 Minecraft 语言文件生成「游戏内显示名 -> 注册表 id」静态索引。

用法::

    python tools/nametag/gen_name_index.py <lang资源jar或目录> [<lang资源jar或目录> ...]

每个输入参数都可以是：

* 一个 jar/zip 文件 —— 用 ``zipfile`` 读取其中的 ``assets/<ns>/lang/<locale>.json``；
* 一个已解包的目录 —— 读取 ``<dir>/assets/<ns>/lang/<locale>.json``
  （递归查找，任何层级的 ``assets/<ns>/lang/<locale>.json`` 都算）。

处理 ``zh_cn`` 与 ``en_us`` 两个 locale，各自产出一个索引文件。

数据源说明（默认值使用的路径）::

    默认 DEFAULT_SOURCES =
        D:\\Desktop\\MCreaterWorkspace\\build\\moddev\\artifacts\\
        neoforge-21.1.190-client-extra-aka-minecraft-resources.jar

    注意：这个客户端资源 jar **只带 en_us**（``assets/minecraft/lang/en_us.json``，6881 键），
    里面没有 ``assets/minecraft/lang/zh_cn.json``；build 目录下另外 4 个 jar
    （modid-1.0.jar / *-merged.jar / *-sources.jar / *.jar）与
    %USERPROFILE%\\.gradle\\caches 下全部 173 个 jar 也都没有 —— vanilla 只把 en_us 打进 jar，
    其余语言放在 assets 对象里，所以本机没有现成的“含 zh_cn 的 jar”可换。

    本机实际可用的 vanilla zh_cn（D:\\Desktop\\mc\\.minecraft\\assets\\objects，按内容排序）::

        1.17  lang (5138 键) —— 缺 377 个 1.21.1 键
        1.19  lang (5431 键) —— 缺 315 个 1.21.1 键
        index 29 -> sha1 d08b7239b6e67d6c2b61809ae2a075df3136f8fb (532430 字节)  <- 实测采用
        index 17/26 -> sha1 f7a45a45d72345df1c526575ddeb30850f82b587 (536105 字节)
        index 5   -> sha1 fb03956a914214bd185d80885ce5d6b4f2ac09cf (552334 字节)

    后三个是 1.21.9~1.21.11 时代的文件（含 copper_golem / music_disc_lava_chicken /
    pale_oak_log 等 1.21.9 之后才有的内容），其中：
      * 三个都 100% 覆盖 1.21.1 en_us 的 entity/item/block 键集（0 缺失）；
      * index 17/5 分别是它们之后的版本（键集是超集，多出的新键更多）；
      * 因此选**多出 1.21.1 不存在的键最少**（+354）、命名漂移也最小的 index 29。

    这些对象文件名是 hash、不符合 ``assets/<ns>/lang/<locale>.json`` 布局，
    实测时把它复制成 ``<临时目录>/assets/minecraft/lang/zh_cn.json`` 后作为「目录」输入传入：

        python tools/nametag/gen_name_index.py ^
            "D:\\Desktop\\MCreaterWorkspace\\build\\moddev\\artifacts\\neoforge-21.1.190-client-extra-aka-minecraft-resources.jar" ^
            "%TEMP%\\mc_vanilla_zhcn_idx29_d08b7239"

    （临时目录只是为了让文件符合 ``assets/<ns>/lang/<locale>.json`` 布局，用完即可删除；
    如果手上有一个真正含 ``assets/minecraft/lang/zh_cn.json`` 的 jar，
    直接把它作为输入传进来即可，无需任何临时目录。）
"""

import io
import json
import os
import re
import sys
import zipfile

# ---------------------------------------------------------------------------
# 常量 / 配置
# ---------------------------------------------------------------------------

# 索引支持的注册表类型
KINDS = ("entity", "item", "block")

# 同一显示名下多个 id 的排序优先级：item -> block -> entity（同类按 id 字典序）
KIND_ORDER = {"item": 0, "block": 1, "entity": 2}

# 需要处理的 locale
LOCALES = ("zh_cn", "en_us")

# 本模组命名空间（这些条目优先级高于 vanilla）
MOD_NAMESPACE = "mcanomalyarchives"

# 仓库根目录（本脚本位于 <repo>/tools/nametag/gen_name_index.py）
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 输出路径写死为 src/main/resources/data/<ns>/name_index/<locale>.json
OUTPUT_DIR = os.path.join(
    REPO_ROOT, "src", "main", "resources", "data", MOD_NAMESPACE, "name_index"
)
OUTPUT_PATH_TEMPLATE = os.path.join(OUTPUT_DIR, "{locale}.json")

# 本模组自己的语言文件（不在 vanilla jar 里，需要额外合并）
MOD_LANG_PATH_TEMPLATE = os.path.join(
    REPO_ROOT, "src", "main", "resources", "assets", MOD_NAMESPACE, "lang", "{locale}.json"
)

# 默认数据源（未显式传参时使用）。最终实测使用的 jar 就是下面这个：
#   D:\Desktop\MCreaterWorkspace\build\moddev\artifacts\neoforge-21.1.190-client-extra-aka-minecraft-resources.jar
# 它含 assets/minecraft/lang/en_us.json（434381 字节 / 6881 键，内容与
# minecraft_1.21.1_client.jar 里的 en_us 完全一致），但**不含 zh_cn.json**，
# 所以 zh_cn 需要额外把 assets 对象作为目录输入传进来 —— 见模块 docstring 里的
# 实测命令（用的是 index 29 的 zh_cn 对象，sha1 d08b7239b6e67d6c2b61809ae2a075df3136f8fb）。
DEFAULT_SOURCES = [
    r"D:\Desktop\MCreaterWorkspace\build\moddev\artifacts"
    r"\neoforge-21.1.190-client-extra-aka-minecraft-resources.jar",
]

# jar/目录内的语言文件路径：assets/<ns>/lang/<locale>.json
_LANG_RELPATH_RE = re.compile(r"^assets/([^/]+)/lang/([^/]+)\.json$")

# 语言 key：<kind>.<namespace>.<id>，三段，任何一段里出现 '.' 都不接受
# （即「id 含 . 的条目」会被跳过，因为那样 split('.') 会得到 4 段）
_KEY_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\.([^.\s]+)\.([^.\s]+)$")


# ---------------------------------------------------------------------------
# 解析
# ---------------------------------------------------------------------------


def parse_key(key):
    """把 lang key 解析成 (kind, namespace, id)；不符合规则返回 None。"""
    if not isinstance(key, str):
        return None
    match = _KEY_RE.match(key)
    if match is None:
        return None
    kind, namespace, ident = match.group(1), match.group(2), match.group(3)
    if kind not in KINDS:
        return None
    return kind, namespace, ident


def clean_value(value):
    """返回可作为显示名的字符串；值为空 / 含 % / 含换行 时返回 None。"""
    if not isinstance(value, str):
        return None
    name = value.strip()
    if not name:
        return None          # 值为空
    if "%" in name:
        return None          # 占位符（%1$s 之类）
    if "\n" in name or "\r" in name:
        return None          # 含换行的多行文本
    return name


def add_entry(entries, name, registry_id, kind, is_mod):
    """entries: {显示名: {registry_id: {"kind":..., "is_mod":...}}}"""
    bucket = entries.setdefault(name, {})
    info = bucket.get(registry_id)
    if info is None:
        bucket[registry_id] = {"kind": kind, "is_mod": is_mod}
    elif is_mod and not info["is_mod"]:
        # 同一个 id 既来自 vanilla 又来自本模组 -> 视为本模组条目
        info["is_mod"] = True


def parse_lang_payload(payload, label, entries, wanted_namespace=None):
    """把一份 lang JSON 里的可用条目写进 entries，返回 (加入条目数, 跳过条目数)。"""
    if not isinstance(payload, dict):
        warn("%s: 顶层不是 JSON 对象，已跳过" % label)
        return 0, 0

    added = 0
    skipped = 0
    for key, raw_value in payload.items():
        parsed = parse_key(key)
        if parsed is None:
            skipped += 1
            continue
        kind, namespace, ident = parsed
        if wanted_namespace is not None and namespace != wanted_namespace:
            skipped += 1
            continue
        name = clean_value(raw_value)
        if name is None:
            skipped += 1
            continue
        registry_id = "%s:%s:%s" % (kind, namespace, ident)
        add_entry(entries, name, registry_id, kind, namespace == MOD_NAMESPACE)
        added += 1
    return added, skipped


def read_json_file(path):
    with io.open(path, "r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def iter_lang_payloads(source, locale):
    """产出 (label, payload) —— 一个输入里所有匹配该 locale 的 lang 文件。"""
    if os.path.isdir(source):
        for root, _dirs, files in os.walk(source):
            for filename in sorted(files):
                if not filename.endswith(".json"):
                    continue
                full_path = os.path.join(root, filename)
                rel_path = os.path.relpath(full_path, source).replace(os.sep, "/")
                match = _LANG_RELPATH_RE.match(rel_path)
                if match is None or match.group(2) != locale:
                    continue
                try:
                    payload = read_json_file(full_path)
                except Exception as exc:  # noqa: BLE001 - 单个文件坏掉不该中断整轮
                    warn("%s: 读取失败(%s)，已跳过" % (full_path, exc))
                    continue
                yield full_path, payload
        return

    if os.path.isfile(source):
        try:
            archive = zipfile.ZipFile(source)
        except Exception as exc:  # noqa: BLE001
            warn("%s: 不是可读的 zip/jar(%s)，已跳过" % (source, exc))
            return
        with archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                match = _LANG_RELPATH_RE.match(info.filename)
                if match is None or match.group(2) != locale:
                    continue
                try:
                    raw = archive.read(info)
                    payload = json.loads(raw.decode("utf-8-sig"))
                except Exception as exc:  # noqa: BLE001
                    warn("%s!%s: 读取失败(%s)，已跳过" % (source, info.filename, exc))
                    continue
                yield "%s!%s" % (source, info.filename), payload
        return

    warn("%s: 路径不存在，已跳过" % source)


def collect_locale(sources, locale):
    """收集一个 locale 的全部条目 -> {显示名: {id: {...}}}"""
    entries = {}

    for source in sources:
        for label, payload in iter_lang_payloads(source, locale):
            added, skipped = parse_lang_payload(payload, label, entries)
            print("  - %s: 采纳 %d 条，跳过 %d 条" % (label, added, skipped))

    # 额外合并本模组自己的语言文件（不在 vanilla jar 里）
    mod_lang_path = MOD_LANG_PATH_TEMPLATE.format(locale=locale)
    if os.path.isfile(mod_lang_path):
        try:
            payload = read_json_file(mod_lang_path)
        except Exception as exc:  # noqa: BLE001
            warn("%s: 读取失败(%s)，已跳过" % (mod_lang_path, exc))
        else:
            added, skipped = parse_lang_payload(
                payload, mod_lang_path, entries, wanted_namespace=MOD_NAMESPACE
            )
            print("  - %s: 采纳 %d 条，跳过 %d 条（本模组，优先级最高）" % (mod_lang_path, added, skipped))
    else:
        print("  - %s: 不存在，跳过本模组语言文件合并" % mod_lang_path)

    return entries


def sort_ids(id_map):
    """排序规则：本模组优先 -> item -> block -> entity -> id 字典序。"""
    ordered = sorted(
        id_map.items(),
        key=lambda item: (
            0 if item[1]["is_mod"] else 1,
            KIND_ORDER[item[1]["kind"]],
            item[0],
        ),
    )
    return [registry_id for registry_id, _info in ordered]


def count_kinds(index):
    counts = {kind: 0 for kind in KINDS}
    for ids in index.values():
        for registry_id in ids:
            kind = registry_id.split(":", 1)[0]
            if kind in counts:
                counts[kind] += 1
    return counts


def describe_sources(sources):
    names = []
    for source in sources:
        cleaned = os.path.normpath(source)
        names.append(os.path.basename(cleaned) or cleaned)
    names.append("%s mod lang" % MOD_NAMESPACE)
    return " + ".join(names)


def write_locale_file(locale, index, sources):
    data = dict(index)
    data["__meta"] = {
        "source": describe_sources(sources),
        "count": len(index),
    }
    text = json.dumps(data, ensure_ascii=False, indent=1, sort_keys=True)

    if not os.path.isdir(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    out_path = OUTPUT_PATH_TEMPLATE.format(locale=locale)
    with io.open(out_path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
        handle.write(u"\n")
    return out_path


def warn(message):
    sys.stderr.write("[warn] %s\n" % message)


def print_stats(locale, index, out_path):
    counts = count_kinds(index)
    id_total = sum(counts.values())

    multi = sorted(
        ((name, ids) for name, ids in index.items() if len(ids) > 1),
        key=lambda item: (-len(item[1]), item[0]),
    )

    print("[%s] 写入 %s" % (locale, out_path))
    print("[%s] 显示名 %d 个，id 共 %d 个（entity %d / item %d / block %d）"
          % (locale, len(index), id_total, counts["entity"], counts["item"], counts["block"]))
    print("[%s] 多重映射（一个名字对应 >=2 个 id）: %d 个" % (locale, len(multi)))
    for name, ids in multi[:3]:
        print("[%s]   例: %s -> %s" % (locale, name, ", ".join(ids)))
    print("")


def main(argv):
    sources = argv[1:]
    if not sources:
        sources = list(DEFAULT_SOURCES)
        print("未提供输入，使用默认数据源:")
        for source in sources:
            print("  %s" % source)
        print("（注意：默认 jar 只含 en_us，zh_cn 需额外传入含 "
              "assets/minecraft/lang/zh_cn.json 的 jar 或目录）")
        print("")

    for source in sources:
        if not os.path.exists(source):
            warn("输入不存在: %s" % source)

    for locale in LOCALES:
        print("=== locale: %s ===" % locale)
        entries = collect_locale(sources, locale)
        index = {name: sort_ids(id_map) for name, id_map in entries.items()}
        out_path = write_locale_file(locale, index, sources)
        print_stats(locale, index, out_path)

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
