# -*- coding: utf-8 -*-
"""红伪云自检（变种版）：不启动游戏，核对"同一个生物换形态"这条设计。

作者 2026-09-18：红伪云作为伪云的一个变种，不要再加一个生物。所以这份自检里
最要紧的几条反向检查是「不许再出现独立的 red 实体/刷怪蛋」，
以及"变红只是置一个同步标记、不生成新实体"。
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
    print("  [%s] %-50s %s" % ("OK " if ok else "FAIL", label, detail))
    if not ok:
        FAILED.append(label)


def check(label, got, want, tol=0):
    ok = abs(got - want) <= tol if isinstance(got, (int, float)) and isinstance(want, (int, float)) else got == want
    print("  [%s] %-50s -> %s" % ("OK " if ok else "FAIL", label, got))
    if not ok:
        FAILED.append("%s: 得到 %r 期望 %r" % (label, got, want))


SMALL = read("src/main/java/net/mcreator/mcanomalyarchives/entity/SmallStangeCloudEntity.java")
RENDERER = read("src/main/java/net/mcreator/mcanomalyarchives/client/renderer/SmallStangeCloudRenderer.java")
LISTENER = read("src/main/java/net/mcreator/mcanomalyarchives/events/PlayerLookAtCornPoppyListener.java")

print("=" * 74)
print("1. 红伪云是变种，不是新生物")
print("=" * 74)
check_true("没有 RedStangeCloudEntity 这个类", not os.path.exists(
    "src/main/java/net/mcreator/mcanomalyarchives/entity/RedStangeCloudEntity.java"))
check_true("没有 RedStangeCloud 元素", not os.path.exists("elements/RedStangeCloud.mod.json"))
check_true("没有红伪云刷怪蛋资源", not os.path.exists(
    "src/main/resources/assets/mcanomalyarchives/items/red_stange_cloud_spawn_egg.json"))
for p in ("src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java",
          "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java",
          "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModTabs.java",
          "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntityRenderers.java"):
    check_true("%s 里没有独立红实体" % os.path.basename(p), "RED_STANGE_CLOUD" not in read(p))
ws = json.load(io.open("mcanomalyarchives.mcreator", encoding="utf-8"))
check_true("工作区里没有红伪云元素", not any(e.get("name") == "RedStangeCloud" for e in ws["mod_elements"]))
check_true("词条里没有红伪云独立条目",
           not any("red_stange_cloud" in k for lg in ("zh_cn", "en_us") for k in ws["language_map"][lg]))

print()
print("=" * 74)
print("2. 变红 = 置一个同步标记（客户端才画得出红贴图）")
print("=" * 74)
check_true("用 SynchedEntityData 定义标记", "SynchedEntityData.defineId(SmallStangeCloudEntity.class" in SMALL)
check_true("defineSynchedData 里注册了它", "builder.define(DATA_RED, false);" in SMALL)
check_true("有 isRed()/setRed()", "public boolean isRed()" in SMALL and "public void setRed(boolean red)" in SMALL)
check_true("写进 NBT（存档读出来还是红的）",
           'tag.putBoolean("RedVariant", isRed());' in SMALL and 'setRed(tag.getBoolean("RedVariant"));' in SMALL)
check_true("渲染器按标记换贴图", "entity.isRed() ? redTexture : entityTexture" in RENDERER)
check_true("红贴图就是虞美人主色那张", "textures/entities/cloud_red.png" in RENDERER)

print()
print("=" * 74)
print("3. 来源：碰到虞美人 → 那朵花变普通虞美人、云变红（同一个实体）")
print("=" * 74)
check_true("按方块类型找虞美人（花没有碰撞箱）", "is(McanomalyarchivesModBlocks.CORN_POPPY.get())" in SMALL)
check_true("虞美人变成原版罂粟", "Blocks.POPPY.defaultBlockState()" in SMALL)
check_true("变红靠 setRed(true)，不生成新实体、不 discard 自己",
           "setRed(true);" in SMALL and "level.addFreshEntity(red)" not in SMALL)
check_true("已经变红的云不再吸花（否则一朵花换一只红云没完没了）", "return !isRed();" in SMALL)
check_true("黑吸伪云不走这条路（它整个覆盖了 tickCloudEffects）",
           "protected void tickCloudEffects()" in read("src/main/java/net/mcreator/mcanomalyarchives/entity/BlackStangeCloudEntity.java"))
check_true("只有服务端改方块", "instanceof ServerLevel serverLevel" in SMALL)

print()
print("=" * 74)
print("4. 看红伪云会上瘾（接在既有虞美人上瘾系统里，按标记过滤）")
print("=" * 74)
check_true("玩家侧认红伪云", "isPlayerLookingAtRedCloud(player)" in LISTENER)
check_true("NPC 侧也认红伪云", "isEntityLookingAtRedCloud(livingEntity)" in LISTENER)
check_true("按视线夹角找云", "CLOUD_COS_LIMIT" in LISTENER and "findRedCloudInView(" in LISTENER)
check_true("只把变红的算进去", "if (!cloud.isRed())" in LISTENER)
check_true("命中的云坐标写进 currentWatchingPoppy",
           "currentWatchingPoppy.put(player.getUUID(), cloud.blockPosition())" in LISTENER)
check_true("上瘾阈值沿用虞美人的 10 秒", "ADDICTION_TRIGGER_TIME = 10 * TICKS_PER_SECOND" in LISTENER)
check_true("区分'看的是云'（安布拉的枯花特例不能误伤云）",
           "boolean watchingCloud;" in LISTENER and "!state.watchingCloud" in LISTENER)

print()
print("=" * 74)
print("5. 资源与见闻录")
print("=" * 74)


def colors(path):
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


w, h, cols = colors("src/main/resources/assets/mcanomalyarchives/textures/entities/cloud_red.png")
check("红贴图尺寸", (w, h), (16, 16))
check_true("纯色", len(cols) == 1, str(cols))
c = list(cols)[0]
check_true("是红色（红通道压过绿蓝）", c[0] > 150 and c[0] > c[1] + 100, str(c))
codex = read("src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java")
check_true("见闻录 UO-001 里有小伪云的 id（变种与它同类，同一个 id 解锁）",
           "mcanomalyarchives:small_stange_cloud" in codex)
check_true("见闻录里没有多余的 red id", "red_stange_cloud" not in codex)

print()
if FAILED:
    print("自检失败 %d 项：" % len(FAILED))
    for f in FAILED:
        print("  -", f)
    raise SystemExit(1)
print("自检全部通过")
