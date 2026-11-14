"""Keep the negative bow wording in MCreator, its guard baseline and generated language files."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KEYS = {
    "zh_cn": {
        "item.mcanomalyarchives.negative_bow": "负值弓",
        "item.mcanomalyarchives.negative_arrow": "负值箭",
        "entity.mcanomalyarchives.negative_return_arrow": "负值箭",
        "codex.mcanomalyarchives.note.negative_bow_release": "用负值弓松弦后，一支普通箭被消耗，却没有看到箭从弓上飞出。",
        "codex.mcanomalyarchives.note.negative_bow_return": "延迟后，一支箭从瞄准的方向高速飞回射手。一次放箭并没有在松弦时结束。",
    },
    "en_us": {
        "item.mcanomalyarchives.negative_bow": "Negative Bow",
        "item.mcanomalyarchives.negative_arrow": "Negative Arrow",
        "entity.mcanomalyarchives.negative_return_arrow": "Negative Arrow",
        "codex.mcanomalyarchives.note.negative_bow_release": "Releasing a negative bow consumed an ordinary arrow, but no arrow flew out from the bow.",
        "codex.mcanomalyarchives.note.negative_bow_return": "After a delay, an arrow flew back at high speed from the aimed direction toward its shooter. Releasing the bow was not the end of the shot.",
    },
}


KEYS["zh_cn"].update({
    "item.mcanomalyarchives.negative_music_disc": "负值唱片",
    "jukebox_song.mcanomalyarchives.negative_cat": "C418 — cat（倒流）",
    "codex.mcanomalyarchives.note.negative_disc_sound": "唱片响起时，周围的声音被压低，颜色翻转，波纹穿过静止的空间。",
    "codex.mcanomalyarchives.note.negative_disc_rewind": "波纹收回后，周围约五十格内的方块、生物与物品回到了过去，最远约十秒前。我的见闻仍然保留。",
    "codex.mcanomalyarchives.note.negative_disc_wear": "回溯无法修复唱片自身的损耗。每次成功倒流消耗一次，十次后消失。",
    "message.mcanomalyarchives.rewind.warming": "尚无可回溯的记录：请在唱片机附近稍等后重新插入。有多少历史就回溯多少，最多十秒。",
    "message.mcanomalyarchives.rewind.chunks": "周围尚未完全加载，倒流中止；唱片未损耗。",
    "message.mcanomalyarchives.rewind.machine": "附近存在无法完整记录的方块实体，倒流中止；唱片未损耗。",
    "message.mcanomalyarchives.rewind.menu": "临时合成栏打断了记录。清空合成格并关闭工作界面，稍等后再试。",
    "message.mcanomalyarchives.rewind.boundary": "过去十秒内有物体或物品跨越回溯边界，倒流中止；唱片未损耗。",
    "message.mcanomalyarchives.rewind.busy": "变化超过记录容量，或已有倒流正在进行；唱片未损耗。",
    "message.mcanomalyarchives.rewind.worn": "这张唱片已经耗尽。"
})
KEYS["en_us"].update({
    "item.mcanomalyarchives.negative_music_disc": "Negative Music Disc",
    "jukebox_song.mcanomalyarchives.negative_cat": "C418 — cat (Reversed)",
    "codex.mcanomalyarchives.note.negative_disc_sound": "The disc muffled the surrounding sounds. Colours inverted and a ripple crossed the frozen space.",
    "codex.mcanomalyarchives.note.negative_disc_rewind": "Blocks, creatures and items within roughly fifty blocks returned to the recorded past, up to ten seconds ago. My discoveries remained.",
    "codex.mcanomalyarchives.note.negative_disc_wear": "Rewinding cannot repair the disc itself. Each successful rewind uses one charge; it vanishes after ten uses.",
    "message.mcanomalyarchives.rewind.warming": "No past recorded yet. Wait briefly near the jukebox, then reinsert. Available history is rewound, up to ten seconds.",
    "message.mcanomalyarchives.rewind.chunks": "Surrounding chunks are not loaded. Cancelled without wear.",
    "message.mcanomalyarchives.rewind.machine": "A nearby block entity cannot be fully recorded. Cancelled without wear.",
    "message.mcanomalyarchives.rewind.menu": "Transient crafting inputs interrupted recording. Clear the crafting grid, close the menu and wait briefly.",
    "message.mcanomalyarchives.rewind.boundary": "Objects or items crossed the rewind boundary during the last ten seconds. Cancelled without wear.",
    "message.mcanomalyarchives.rewind.busy": "Too many changes to record, or another rewind is active. No wear applied.",
    "message.mcanomalyarchives.rewind.worn": "This disc is exhausted."
})

KEYS["zh_cn"].update({
    "item.mcanomalyarchives.negative_dirt": "负值泥土",
    "item.mcanomalyarchives.negative_oak_log": "负值橡木原木",
    "item.mcanomalyarchives.negative_oak_planks": "负值橡木木板",
    "block.mcanomalyarchives.negative_space": "负空间",
    "codex.mcanomalyarchives.note.negative_block_place": "负值方块没有留下对应的物质，而是形成黑色空间。黑色空间逐渐溶解，约三十秒后消失。",
    "codex.mcanomalyarchives.note.negative_space_contact": "接触黑色空间会遭遇致命伤害。",
    "codex.mcanomalyarchives.note.negative_bow_release": "负值弓松弦后，没有箭从弓上飞出，反而补回了一支普通箭。",
    "entity.mcanomalyarchives.negative_return_arrow": "箭"
})
KEYS["en_us"].update({
    "item.mcanomalyarchives.negative_dirt": "Negative Dirt",
    "item.mcanomalyarchives.negative_oak_log": "Negative Oak Log",
    "item.mcanomalyarchives.negative_oak_planks": "Negative Oak Planks",
    "block.mcanomalyarchives.negative_space": "Negative Space",
    "codex.mcanomalyarchives.note.negative_block_place": "Placing a negative block leaves black space instead of its material. It dissolves gradually and vanishes after about thirty seconds.",
    "codex.mcanomalyarchives.note.negative_space_contact": "Touching the black space causes lethal damage.",
    "codex.mcanomalyarchives.note.negative_bow_release": "Releasing the negative bow produced no outgoing arrow. Instead, an ordinary arrow returned to my inventory.",
    "entity.mcanomalyarchives.negative_return_arrow": "Arrow"
})

KEYS["zh_cn"].update({
    "item.mcanomalyarchives.negative_wooden_pickaxe": "负值木镐",
    "block.mcanomalyarchives.liquid_cobblestone": "液态原石",
    "codex.mcanomalyarchives.note.negative_block_return": "负空间消散后，附近洞穴的空位出现了对应的普通物质。",
    "codex.mcanomalyarchives.note.name_liquid_stone": "命名为水的原石蔓延成灰色液层，黏附接触者并不断化为粉尘。约十一分钟后消散。",
    "codex.mcanomalyarchives.note.name_potato": "被命名为土豆的生物先停止活动，身体逐渐块茎化并发芽，最后留下土豆。"
})
KEYS["en_us"].update({
    "item.mcanomalyarchives.negative_wooden_pickaxe": "Negative Wooden Pickaxe",
    "block.mcanomalyarchives.liquid_cobblestone": "Liquid Cobblestone",
    "codex.mcanomalyarchives.note.negative_block_return": "After the negative space dissolved, its ordinary material reappeared in an empty spot in a nearby cave.",
    "codex.mcanomalyarchives.note.name_liquid_stone": "Cobblestone named water spread into a sticky grey layer and evaporated into dust. It dissipated after about eleven minutes.",
    "codex.mcanomalyarchives.note.name_potato": "The creature named potato stopped moving, gradually became tuber-like, sprouted and finally left potatoes behind."
})

def update_object(text, pairs, indent):
    """Preserve unrelated keys and formatting instead of rewriting the whole workspace."""
    parsed = json.loads(text)
    missing = {}
    for key, value in pairs.items():
        if key not in parsed:
            missing[key] = value
        else:
            pattern = r'(' + re.escape(json.dumps(key)) + r'\s*:\s*)"(?:[^"\\]|\\.)*"'
            text = re.sub(pattern, lambda m: m.group(1) + json.dumps(value, ensure_ascii=False), text)
    if missing:
        close = text.rfind("}")
        prefix = text[:close].rstrip()
        if parsed:
            prefix += ","
        lines = [indent + json.dumps(k) + ": " + json.dumps(v, ensure_ascii=False) for k, v in missing.items()]
        text = prefix + "\n" + ",\n".join(lines) + "\n" + text[text.rfind("\n", 0, close) + 1:]
    json.loads(text)
    return text


workspace = ROOT / "mcanomalyarchives.mcreator"
raw = workspace.read_text(encoding="utf-8")
language_start = raw.index('"language_map"')
for language, pairs in KEYS.items():
    match = re.search(r'"' + language + r'"\s*:\s*\{', raw[language_start:])
    start = language_start + match.end() - 1
    _, size = json.JSONDecoder().raw_decode(raw[start:])
    raw = raw[:start] + update_object(raw[start:start + size], pairs, "      ") + raw[start + size:]
json.loads(raw)
workspace.write_text(raw, encoding="utf-8")

for language, pairs in KEYS.items():
    lang = ROOT / f"src/main/resources/assets/mcanomalyarchives/lang/{language}.json"
    lang.write_text(update_object(lang.read_text(encoding="utf-8"), pairs, "  "), encoding="utf-8")
    canonical = ROOT / f"tools/mcreator-guard/canonical/{language}.extra.txt"
    text = canonical.read_text(encoding="utf-8")
    for key, value in pairs.items():
        line = "  " + json.dumps(key) + ": " + json.dumps(value, ensure_ascii=False) + ","
        pattern = r'^\s*' + re.escape(json.dumps(key)) + r'\s*:.*$'
        if re.search(pattern, text, re.MULTILINE):
            text = re.sub(pattern, lambda _: line, text, flags=re.MULTILINE)
        else:
            text = text.rstrip() + "\n" + line + "\n"
    canonical.write_text(text, encoding="utf-8")

manifest_path = ROOT / "tools/mcreator-guard/manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
for item in manifest["items"]:
    language = {"lang-zh-extra": "zh_cn", "lang-en-extra": "en_us"}.get(item["id"])
    if language:
        for key in KEYS[language]:
            quoted = json.dumps(key)
            if quoted not in item["expectAll"]:
                item["expectAll"].append(quoted)
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Synced bow and disc keys per language: workspace, canonical, resources and guard.")
