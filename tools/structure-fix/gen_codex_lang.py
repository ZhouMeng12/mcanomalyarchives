# -*- coding: utf-8 -*-
"""
生成见闻录（图鉴）的全部文案：
1. 写进 tools/mcreator-guard/canonical/{zh_cn,en_us}.extra.txt（插入式基线，MCreator 重写 lang 后可自动补回）
2. 同步 zhcn.csv（MCreator 的翻译源）
文案来源：《MC诡异见闻录》wiki 的 UO 系列文档（UO-001~011）+ 本模组自定的 UO-012。
"""
import io, json, os, re

BS = chr(92)

# key 后缀 -> (中文, English)
UI = {
    "title": ("见闻录", "The Archive"),
    "progress": ("已解明 %1$s / %2$s", "Deciphered %1$s / %2$s"),
    "locked": ("???", "???"),
    "locked_hint": ("尚未解明——在游戏中遇到它之后会自动记录。",
                    "Not yet deciphered. It is recorded automatically once you encounter it."),
    "planned": ("该异常尚未在本模组中实装，档案暂缺。",
                "This anomaly is not implemented in the mod yet; the file is incomplete."),
    "level": ("项目等级", "Class"),
    "protocol": ("保密协议", "Protocol"),
    "traits": ("已知特征", "Known traits"),
    "unlocked": ("[见闻录] 已记录 %1$s %2$s", "[Archive] Recorded %1$s %2$s"),
}

ENTRIES = {
    "uo001": (
        ("伪云", "Fake Cloud"),
        ("形似原版云朵的高空异常，分幽灵、多态、异位三型，能穿透方块逃离；其灰蓝液体可使人虚化。",
         "A high-altitude anomaly shaped like a vanilla cloud. Three types - Ghost, Polymorph and Displaced - and it escapes through blocks. Its grey-blue fluid makes things phase out."),
        ("箭矢可穿透，其后会加速逃离 · 玻璃困笼困不住它 · 困笼内动物窒息死亡 · 水体与岩浆被染成灰蓝 · 黑色个体接触方块会令其离奇消失",
         "Arrows pass through, then it flees faster · Glass cages cannot hold it · Animals inside suffocate · Water and lava stain grey-blue · Black individuals erase nearby blocks on contact"),
        ("R4", "R4"), ("B", "B"),
    ),
    "uo002": (
        ("不存在的村庄", "The Village That Isn't"),
        ("只有踏入指定矩形坐标区才会浮现的反色村庄，村民会一直盯着你。",
         "An inverted-colour village that only exists inside a specific rectangle of coordinates. Its villagers keep staring at you."),
        ("区外建筑形同不存在 · 建筑呈反向色彩与错误材质 · 村民面部无法识别 · 越界者全身反色 · 与异常村民交易换来的「灾难」会令见者凭空消失",
         "Invisible from outside the zone · Inverted colours, wrong materials · Villagers have no recognisable face · Trespassers turn inverted · The 「disaster」 traded from them makes the buyer vanish"),
        ("R1", "R1"), ("B", "B"),
    ),
    "uo003": (
        ("虞美人", "Corn Poppy"),
        ("一朵让人观赏成瘾的虞美人。直视它会莫名悲伤流泪，围观者可能因此丧命。",
         "A poppy that makes you addicted to watching it. Look straight at it and you weep for no reason; onlookers have died."),
        ("影响范围直径达 128 格 · 通过监控等视觉载体观看可阻挡其影响 · 收容 41 小时时分泌泪滴，曾致 2 名研究员自尽 · 观赏者尸体脸部会长出新的个体",
         "Affects a 128-block diameter · Viewing through cameras blocks the effect · Secreting tears at hour 41 drove two researchers to suicide · New instances grow from the faces of dead watchers"),
        ("无效化（原 R2 不安级）", "Neutralised (formerly Restless R2)"), ("B", "B"),
    ),
    "uo004": (
        ("跨维钓竿", "Dimensional Fishing Rod"),
        ("外观与普通钓竿无异，却能把主世界之外的东西钓上岸。",
         "Looks like an ordinary rod, yet it can pull things from outside the Overworld ashore."),
        ("耐久上限约 8.98×10^17，每次垂钓只消耗 1 点 · 鱼线抛得越远，钓出的东西越脱离常规 · 钓上物不可食用或具危险性 · 附近反复出现黑色渔夫阴影",
         "Durability cap around 8.98x10^17, one point per cast · The further you cast, the stranger the catch · Catches are inedible or dangerous · A black fisherman's shadow keeps appearing nearby"),
        ("R0", "R0"), ("B", "B"),
    ),
    "uo005": (
        ("紫怪", "Purple Monster"),
        ("紫色独眼人形实体，出没于密林，以精神对话诱导你选择负面选项，并逐渐把你同化。",
         "A purple one-eyed humanoid in deep woods. It talks inside your head, pushes you toward negative options, and slowly assimilates you."),
        ("面部被单只眼睛取代 · 在脑内交流并弹出选项窗口 · 选择负面选项者皮肤会紫化 · 被人类观察时表现厌恶并消失 · 能长出触肢贯穿杀人",
         "Its face is replaced by a single eye · Speaks mentally through an option window · Those who choose negative options turn purple · It vanishes in disgust when watched · Grows tendrils that impale"),
        ("斥锁级 Rejective(R4)", "Rejective (R4)"), ("未标注", "Not stated"),
    ),
    "uo006": (
        ("鑛洞", "The Ore Cave"),
        ("森林中深不见底的分层矿洞，越深矿物越多，以贪婪诱人不停下挖。",
         "A bottomless, layered cave in the forest. The deeper you go the richer the ore - greed keeps you digging."),
        ("分 Ⅰ闪星～Ⅵ黑洞 六阶段层 · 矿脉体量异常，最高单脉 822 个铁矿 · 附属异常：富集矿石、白色矿工虚影、所求之物、愿景金门 · 旧挖掘痕迹会消失、结构重置 · 带出的宝藏会化为灰烬",
         "Six stages, from I Glimmer to VI Black Hole · Absurd veins, up to 822 iron in one · Attached anomalies: enriched ore, white miner ghosts, the thing you seek, the visionary golden gate · Old diggings vanish and the layout resets · Treasure taken out turns to ash"),
        ("R1", "R1"), ("S", "S"),
    ),
    "uo007": (
        ("负值物品", "Negative Items"),
        ("某处出现的一系列反向色彩、负数量掉落物，具反属性与回溯效应。",
         "A set of inverted-colour drops with negative stack counts, anti-properties and a retroactive effect."),
        ("物品数量为负、色彩与原版相反 · 负苹果吃下后饱食度下降、数量反增 · 负镐挖掘时耐久增加，方块被随机替换 · 负护腿无法脱下，会招来负值实体 · 被替换的方块会回溯到附近矿洞",
         "Counts are negative, colours inverted · A negative apple lowers hunger and multiplies · A negative pickaxe gains durability and randomises blocks · Negative leggings cannot be removed and summon negative entities · Replaced blocks reappear in a nearby cave"),
        ("R0", "R0"), ("B", "B"),
    ),
    "uo008": (
        ("百变命名牌", "Shapeshifting Name Tag"),
        ("档案残缺：目前只知它与受到辐射污染的命名牌有关，其余记录缺失。",
         "A fragmentary file: all that is known is its link to radiation-contaminated name tags."),
        ("项目等级 R1、保密协议 A · 对外解释：矿井与命名牌受到辐射污染 · 原始档案正文仍在施工",
         "Class R1, protocol A · Cover story: mine and name tag radiation contamination · The original document is still under construction"),
        ("R1", "R1"), ("A", "A"),
    ),
    "uo009": (
        ("假太阳", "The False Sun"),
        ("高维投影天体，只有精神疾病患者能看见；它靠注视锚定现实，并放大观测者内心的渴望。",
         "A higher-dimensional projected body, visible only to the mentally ill. It anchors to reality through gaze and magnifies the watcher's desire."),
        ("仅精神病患者可见 · 靠注视锚定于现实，专注越深成像越清晰 · 先放大内心渴望，再使人陷入宁静 · 形态随观测者的疾病而变 · 可改写集体认知",
         "Visible only to the mentally ill · Anchors to reality by being watched; focus sharpens it · Magnifies inner desire, then brings calm · Its form follows the watcher's illness · Can rewrite collective perception"),
        ("R4", "R4"), ("A", "A"),
    ),
    "uo010": (
        ("跨维窥镜", "Dimensional Spyglass"),
        ("一台灰色望远镜，五个旋钮可以窥视其他维度的事物；部分成像带有模因污染。",
         "A grey telescope whose five dials let you see into other dimensions. Some images carry memetic contamination."),
        ("外观是灰色望远镜，材质酷似 UO-004 · 侧下有 α~ε 五个旋钮，δ、ε 已损坏 · α 调偏离现实程度、β 调时间、γ 调成像密度 · 部分成像带危险模因污染，会引发极端恐惧 · 同一人观测两次以上会致盲",
         "A grey telescope whose material much resembles UO-004 · Five dials α to ε below; δ and ε are broken · α shifts deviation from reality, β time, γ image density · Some images carry dangerous memetics causing extreme fear · A second viewing by the same person causes blindness"),
        ("R1", "R1"), ("B", "B"),
    ),
    "uo011": (
        ("怪树", "Strange Tree"),
        ("黑色的怪树，会钻地潜伏，并斩杀附近连续砍树的人；基岩可以困住它。",
         "A black tree that burrows and lies in wait, then kills whoever fells trees nearby. Bedrock can trap it."),
        ("树干硬度接近基岩，无法破坏 · 树叶接近黑曜石，破坏后瞬间再生 · 累计砍 7~9 棵树即连根拔起钻入地下 · 以约 3.1 m/s 从地下接近，用镰刀状枝条斩杀 · 每砍一棵补种一棵树苗则不会触发",
         "Trunk is near-bedrock hard · Leaves near-obsidian, regrowing instantly · After 7 to 9 trees felled it uproots and burrows · Approaches underground at about 3.1 m/s and cuts with scythe-like branches · Replanting one sapling per tree prevents it"),
        ("R2", "R2"), ("C", "C"),
    ),
    "uo012": (
        ("幸运粉羊", "Lucky Pink Sheep"),
        ("平原上静静站着的粉色羊。注视它会得到好运；盯得太久，它会在你眨眼的一瞬间消失。",
         "A pink sheep standing quietly in the plains. Looking at it brings luck; stare too long and it vanishes between two blinks."),
        ("只在平原等温和开阔群系出现 · 目击满 2 秒获得幸运 · 凝视满 45 秒它会眨眼消失并转移 · 靠得太近或攻击它会招来三档灾厄 · 最高档灾厄没有任何预警",
         "Appears only in plains and similar open temperate biomes · Two seconds of sighting grants luck · Forty-five seconds of staring makes it blink away and relocate · Getting close or attacking triggers three tiers of calamity · The top tier gives no warning at all"),
        ("R3（本模组自定）", "R3 (set by this mod)"), ("B（本模组自定）", "B (set by this mod)"),
    ),
}

PREFIX = "codex.mcanomalyarchives."


def build_lang(lang_index):
    """lang_index: 0=中文 1=英文；返回 [(key, value)]"""
    rows = []
    for k, pair in UI.items():
        rows.append((PREFIX + k, pair[lang_index]))
    rows.append(("item.mcanomalyarchives.anomaly_codex", ("见闻录", "The Archive")[lang_index]))
    for eid, (name, info, trait, level, protocol) in ENTRIES.items():
        rows.append((PREFIX + eid + ".name", name[lang_index]))
        rows.append((PREFIX + eid + ".info", info[lang_index]))
        rows.append((PREFIX + eid + ".trait", trait[lang_index]))
        rows.append((PREFIX + eid + ".level", level[lang_index]))
        rows.append((PREFIX + eid + ".protocol", protocol[lang_index]))
    return rows


def write_canonical(path, rows):
    lines = ["  " + json.dumps(k, ensure_ascii=False) + ": " + json.dumps(v, ensure_ascii=False) + ","
             for k, v in rows]
    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
    return len(lines)


zh_rows = build_lang(0)
en_rows = build_lang(1)
n1 = write_canonical("tools/mcreator-guard/canonical/zh_cn.extra.txt", zh_rows)
n2 = write_canonical("tools/mcreator-guard/canonical/en_us.extra.txt", en_rows)
print("中文词条 %d 条 / 英文词条 %d 条" % (n1, n2))

# CSV（追加缺失的键）
csv_path = "zhcn.csv"
csv = io.open(csv_path, encoding="utf-8", newline="").read()
added = 0
for (key, zh), (_, en) in zip(zh_rows, en_rows):
    if ('\n' + key + ',') in csv or csv.startswith(key + ','):
        continue
    csv = csv.rstrip("\r\n") + "\r\n" + key + "," + zh + "," + en + "\r\n"
    added += 1
io.open(csv_path, "w", encoding="utf-8", newline="").write(csv)
print("zhcn.csv 追加 %d 行" % added)

# 清单：让守卫检查这些新键
mp = "tools/mcreator-guard/manifest.json"
m = json.load(io.open(mp, encoding="utf-8"))
for it in m["items"]:
    if it["id"] == "lang-zh-extra":
        it["expectAll"] = sorted(set(it["expectAll"] + [
            '"codex.mcanomalyarchives.title"', '"codex.mcanomalyarchives.uo001.name"',
            '"codex.mcanomalyarchives.uo012.trait"', '"item.mcanomalyarchives.anomaly_codex"']))
    if it["id"] == "lang-en-extra":
        it["expectAll"] = sorted(set(it["expectAll"] + [
            '"codex.mcanomalyarchives.title"', '"codex.mcanomalyarchives.uo012.trait"']))
json.dump(m, io.open(mp, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
io.open(mp, "a", encoding="utf-8").write("\n")
print("清单已更新（lang 检查项加上见闻录标记键）")
