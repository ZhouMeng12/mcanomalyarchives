# -*- coding: utf-8 -*-
"""红伪云自检：贴图/尺寸/来源/上瘾/词条，不需要启动游戏。"""
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


SMALL = read("src/main/java/net/mcreator/mcanomalyarchives/entity/SmallStangeCloudEntity.java")
RED = read("src/main/java/net/mcreator/mcanomalyarchives/entity/RedStangeCloudEntity.java")
RENDERER = read("src/main/java/net/mcreator/mcanomalyarchives/client/renderer/RedStangeCloudRenderer.java")
LISTENER = read("src/main/java/net/mcreator/mcanomalyarchives/events/PlayerLookAtCornPoppyListener.java")

print("=" * 72)
print("1. 与小伪云同模型同尺寸（作者：还是和小伪云差不多）")
print("=" * 72)
check_true("红伪云继承小伪云", "extends SmallStangeCloudEntity" in RED)
check_true("渲染器复用 ModelStrangeCloud 且缩放取小伪云那个常量",
           "ModelStrangeCloud" in RENDERER and "RedStangeCloudEntity.RENDER_SCALE" in RENDERER)
red_elem = json.load(io.open("elements/RedStangeCloud.mod.json", encoding="utf-8"))["definition"]
small_elem = json.load(io.open("elements/SmallStangeCloud.mod.json", encoding="utf-8"))["definition"]
for field in ("mobModelName", "modelWidth", "modelHeight", "visualScale", "spawnThisMob", "mobSpawningType"):
    check_true("元素 %s 与小伪云一致" % field, red_elem[field] == small_elem[field], str(red_elem[field]))
check_true("只有贴图不同", red_elem["mobModelTexture"] == "cloud_red.png")
check_true("红伪云不吸虞美人（否则会一朵花换一只云、无限复制）",
           "protected boolean canAbsorbPoppy()" in RED and "return false;" in RED)

print()
print("=" * 72)
print("2. 贴图是虞美人主色的红")
print("=" * 72)


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


w, h, colors = png_colors("src/main/resources/assets/mcanomalyarchives/textures/entities/cloud_red.png")
check("贴图尺寸", (w, h), (16, 16))
check_true("纯色填充", len(colors) == 1, str(colors))
color = list(colors)[0]
check_true("是红色（红通道明显压过绿蓝）", color[0] > 150 and color[0] > color[1] + 100, str(color))

print()
print("=" * 72)
print("3. 来源：小伪云碰到虞美人 → 花变普通虞美人、云变红")
print("=" * 72)
check_true("普通小伪云会吸（默认 true）", "protected boolean canAbsorbPoppy() {\n\t\treturn true;\n\t}" in SMALL)
check_true("按方块类型找虞美人（花没有碰撞箱）",
           "is(McanomalyarchivesModBlocks.CORN_POPPY.get())" in SMALL)
check_true("虞美人变成原版罂粟", "Blocks.POPPY.defaultBlockState()" in SMALL)
check_true("云变成 RED_STANGE_CLOUD 实体", "McanomalyarchivesModEntities.RED_STANGE_CLOUD.get()" in SMALL)
check_true("运动状态原样交接（位置/高度/方向/已活时长）",
           all(k in SMALL for k in ("handOffMotionTo(", "next.fixedYLevel = this.fixedYLevel",
                                    "next.heading = this.heading", "next.tickCount = this.tickCount")))
check_true("黑吸伪云不走这条路（它整个覆盖了 tickCloudEffects）",
           "protected void tickCloudEffects()" in read("src/main/java/net/mcreator/mcanomalyarchives/entity/BlackStangeCloudEntity.java"))
check_true("只有服务端改方块", "instanceof ServerLevel serverLevel" in SMALL)

print()
print("=" * 72)
print("4. 看红伪云会上瘾（接在既有虞美人上瘾系统里）")
print("=" * 72)
check_true("玩家侧认红伪云", "isPlayerLookingAtRedCloud(player)" in LISTENER)
check_true("NPC 侧也认红伪云", "isEntityLookingAtRedCloud(livingEntity)" in LISTENER)
check_true("按视线夹角找云（不是打方块射线）", "findRedCloudInView(" in LISTENER and "CLOUD_COS_LIMIT" in LISTENER)
check_true("命中的云坐标写进 currentWatchingPoppy（定身朝向才对得上）",
           "currentWatchingPoppy.put(player.getUUID(), cloud.blockPosition())" in LISTENER)
check_true("上瘾阈值沿用虞美人的 10 秒", "ADDICTION_TRIGGER_TIME = 10 * TICKS_PER_SECOND" in LISTENER)
check_true("区分'看的是云'（安布拉的枯花特例不要误伤云）",
           "boolean watchingCloud;" in LISTENER and "!state.watchingCloud" in LISTENER)

print()
print("=" * 72)
print("5. 集成：见闻录 / 词条 / 资源")
print("=" * 72)
check_true("UO-001 解锁 id 含红伪云", "mcanomalyarchives:red_stange_cloud" in read(
    "src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java"))
ws = json.load(io.open("mcanomalyarchives.mcreator", encoding="utf-8"))
for lg in ("zh_cn", "en_us"):
    keys = ws["language_map"][lg]
    check_true("workspace language_map.%s 有两词条" % lg,
               "entity.mcanomalyarchives.red_stange_cloud" in keys
               and "item.mcanomalyarchives.red_stange_cloud_spawn_egg" in keys)
for lg in ("zh_cn", "en_us"):
    text = read("src/main/resources/assets/mcanomalyarchives/lang/%s.json" % lg)
    check_true("lang/%s.json 有两词条" % lg,
               '"entity.mcanomalyarchives.red_stange_cloud"' in text
               and '"item.mcanomalyarchives.red_stange_cloud_spawn_egg"' in text)
check_true("元素挂在工作区里且 locked_code",
           any(e.get("name") == "RedStangeCloud" and e.get("locked_code") for e in ws["mod_elements"]))
for p in ("src/main/resources/assets/mcanomalyarchives/textures/entities/cloud_red.png",
          "src/main/resources/assets/mcanomalyarchives/textures/item/red_stange_cloud_spawn_egg_generated.png",
          "src/main/resources/assets/mcanomalyarchives/models/item/red_stange_cloud_spawn_egg.json",
          "src/main/resources/assets/mcanomalyarchives/items/red_stange_cloud_spawn_egg.json"):
    check_true("资源 " + os.path.basename(p), os.path.exists(p))

print()
if FAILED:
    print("自检失败 %d 项：" % len(FAILED))
    for f in FAILED:
        print("  -", f)
    raise SystemExit(1)
print("自检全部通过")
