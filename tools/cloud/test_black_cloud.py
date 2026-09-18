# -*- coding: utf-8 -*-
"""黑吸伪云的离线自检：数值契约 + 行为契约，不需要启动游戏。

对应实现：
  entity/BlackStangeCloudEntity.java（继承小伪云，覆盖 tickCloudEffects）
  client/renderer/BlackStangeCloudRenderer.java
  anomaly/cloud/SmallCloudEmitter.java（1/6 概率吐黑的）
  config/ComfortConfig.java（blackCloudTerrainDamage 开关）
"""
import io
import json
import os
import re
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

FAILED = []


def read(p):
    return io.open(p, encoding="utf-8").read()


def check_true(label, ok, detail=""):
    print("  [%s] %-48s %s" % ("OK " if ok else "FAIL", label, detail))
    if not ok:
        FAILED.append(label)


def check(label, got, want, tol=0):
    ok = abs(got - want) <= tol if isinstance(got, (int, float)) and isinstance(want, (int, float)) else got == want
    print("  [%s] %-48s -> %s" % ("OK " if ok else "FAIL", label, got))
    if not ok:
        FAILED.append("%s: 得到 %r 期望 %r" % (label, got, want))


ENTITY = "src/main/java/net/mcreator/mcanomalyarchives/entity/BlackStangeCloudEntity.java"
RENDERER = "src/main/java/net/mcreator/mcanomalyarchives/client/renderer/BlackStangeCloudRenderer.java"
EMITTER = "src/main/java/net/mcreator/mcanomalyarchives/anomaly/cloud/SmallCloudEmitter.java"
CONFIG = "src/main/java/net/mcreator/mcanomalyarchives/config/ComfortConfig.java"
TEX = "src/main/resources/assets/mcanomalyarchives/textures/entities/cloud_black.png"

black = read(ENTITY)
renderer = read(RENDERER)
emitter = read(EMITTER)

print("=" * 72)
print("1. 与小伪云同模型同尺寸（作者：模型用小伪云模型）")
print("=" * 72)
check_true("黑吸伪云继承小伪云（出场直线/寿命/过门冷却全部继承）", "extends SmallStangeCloudEntity" in black)
check_true("渲染器复用 ModelStrangeCloud 且缩放取小伪云那个常量",
           "ModelStrangeCloud" in renderer and "BlackStangeCloudEntity.RENDER_SCALE" in renderer)

element = json.load(io.open("elements/BlackStangeCloud.mod.json", encoding="utf-8"))["definition"]
small_element = json.load(io.open("elements/SmallStangeCloud.mod.json", encoding="utf-8"))["definition"]
for field in ("mobModelName", "modelWidth", "modelHeight", "visualScale", "spawnThisMob", "mobSpawningType"):
    check_true("元素 %s 与小伪云一致" % field, element[field] == small_element[field], str(element[field]))
check_true("只有贴图不同（乌黑那张）",
           element["mobModelTexture"] == "cloud_black.png" and small_element["mobModelTexture"] == "texture.png")


def png_colors(path):
    d = open(path, "rb").read()
    pos, idat, w, h = 8, b"", None, None
    while pos < len(d):
        ln = struct.unpack(">I", d[pos:pos + 4])[0]
        typ = d[pos + 4:pos + 8]
        if typ == b"IHDR":
            w, h = struct.unpack(">II", d[pos + 8:pos + 16])
        elif typ == b"IDAT":
            idat += d[pos + 8:pos + 8 + ln]
        pos += 12 + ln
    raw = zlib.decompress(idat)
    stride = w * 4 + 1
    out = set()
    for y in range(h):
        line = raw[y * stride + 1:(y + 1) * stride]
        for x in range(w):
            if line[x * 4 + 3] > 0:
                out.add(tuple(line[x * 4:x * 4 + 3]))
    return w, h, out


print()
print("=" * 72)
print("2. 贴图是乌黑，不是纯黑")
print("=" * 72)
w, h, colors = png_colors(TEX)
check("贴图尺寸", (w, h), (16, 16))
check_true("纯色填充（源贴图就是纯白，层次靠模型面光）", len(colors) == 1, str(colors))
color = list(colors)[0]
check_true("是乌黑 #141419，不是纯黑 #000000", color == (20, 20, 25), str(color))
check_true("整体确实是暗色（任一通道 <= 40）", max(color) <= 40, str(max(color)))

print()
print("=" * 72)
print("3. 只吞方块：不伤害生物、不染水")
print("=" * 72)
for banned in ("applyCloudDamage", "convertLavaAndWaterToCloudBlocks", "DAMAGE_PER_HIT"):
    check_true("黑云类里没有 %s" % banned, banned not in black)
check_true("覆盖的是父类的效果钩子", "protected void tickCloudEffects()" in black)

print()
print("=" * 72)
print("4. 碰到方块 → 吞一口（无掉落）→ 自己消散")
print("=" * 72)
check("吞噬半径", float(re.search(r"ERASE_RADIUS = ([0-9.]+)D", black).group(1)), 6.0, 0.001)
check("最少吞", int(re.search(r"ERASE_MIN = ([0-9]+)", black).group(1)), 12)
check("最多吞", int(re.search(r"ERASE_MAX = ([0-9]+)", black).group(1)), 24)
check_true("先接触判定", "touchingSolidBlock(" in black)
check_true("用 destroyBlock(pos, false) 吞（无掉落）", "destroyBlock(candidates.get(i), false)" in black)
check_true("吞完自己 discard", "this.discard();" in black)
check_true("吞在消散之前（顺序不能反）",
           black.index("eraseAround(serverLevel)") < black.index("vanish(serverLevel)"))
check_true("免疫名单齐全（基岩/屏障/命令方块/末地门/云室门/怪树）",
           all(k in black for k in ("Blocks.BEDROCK", "Blocks.BARRIER", "COMMAND_BLOCK", "Blocks.END_PORTAL",
                                    "CLOUD_DEM_PORTAL", "STRANGETREELOG", "STRANGETREELEAVES")))
check_true("两道闸门：mobGriefing + 配置项",
           "RULE_MOBGRIEFING" in black and "ComfortConfig.blackCloudTerrainDamage()" in black)
check_true("配置项确实定义了", "BLACK_CLOUD_TERRAIN_DAMAGE" in read(CONFIG)
           and "public static boolean blackCloudTerrainDamage()" in read(CONFIG))

print()
print("=" * 72)
print("5. 从传送门出来（1/6 是黑的）")
print("=" * 72)
chance = int(re.search(r"BLACK_ONE_IN = ([0-9]+)", emitter).group(1))
check("黑云概率分母", chance, 6)
check_true("发射器会挑黑云类型", "McanomalyarchivesModEntities.BLACK_STANGE_CLOUD.get()" in emitter)
check_true("并发上限把两种云一起算（查询的是父类类型）",
           "getEntitiesOfClass(SmallStangeCloudEntity.class, neighbourhood)" in emitter)

print()
print("=" * 72)
print("6. 集成：见闻录 / 词条 / 资源")
print("=" * 72)
codex = read("src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java")
check_true("UO-001 的解锁 id 含黑吸伪云", "mcanomalyarchives:black_stange_cloud" in codex)
ws = json.load(io.open("mcanomalyarchives.mcreator", encoding="utf-8"))
for lg in ("zh_cn", "en_us"):
    keys = ws["language_map"][lg]
    check_true("workspace language_map.%s 有两词条" % lg,
               "entity.mcanomalyarchives.black_stange_cloud" in keys
               and "item.mcanomalyarchives.black_stange_cloud_spawn_egg" in keys)
for lg in ("zh_cn", "en_us"):
    text = read("src/main/resources/assets/mcanomalyarchives/lang/%s.json" % lg)
    check_true("lang/%s.json 有两词条" % lg,
               '"entity.mcanomalyarchives.black_stange_cloud"' in text
               and '"item.mcanomalyarchives.black_stange_cloud_spawn_egg"' in text)
check_true("元素挂在工作区里且 locked_code",
           any(e.get("name") == "BlackStangeCloud" and e.get("locked_code") for e in ws["mod_elements"]))
for p in (TEX,
          "src/main/resources/assets/mcanomalyarchives/textures/item/black_stange_cloud_spawn_egg_generated.png",
          "src/main/resources/assets/mcanomalyarchives/models/item/black_stange_cloud_spawn_egg.json",
          "src/main/resources/assets/mcanomalyarchives/items/black_stange_cloud_spawn_egg.json"):
    check_true("资源 " + os.path.basename(p), os.path.exists(p))
check_true("通行 mixin 会放行黑云（子类自动覆盖）",
           "instanceof SmallStangeCloudEntity" in
           read("src/main/java/net/mcreator/mcanomalyarchives/mixin/CloudPortalPassMixin.java"))

print()
if FAILED:
    print("自检失败 %d 项：" % len(FAILED))
    for f in FAILED:
        print("  -", f)
    raise SystemExit(1)
print("自检全部通过")
