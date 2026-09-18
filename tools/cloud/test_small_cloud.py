# -*- coding: utf-8 -*-
"""小伪云（UO-001 衍生个体）的离线自检。

不启动游戏，只做三件事：
  1. **算一遍尺寸**：确认"短横边 2 格"这个作者要求，与元素里的碰撞箱、
     渲染器里的缩放、实体里的伤害盒三者是同一套数字（改一个忘一个是最容易出的错）；
  2. **核对触发链**：门方块 → 随机刻 → 吐云逻辑 → 实体出场，每一环的钩子都还在；
  3. **核对集成**：见闻录多 id 解锁、大伪云伤害排除、维度 id 拼写、词条三处齐全。

Java 侧对应实现：
  entity/SmallStangeCloudEntity.java
  anomaly/cloud/SmallCloudEmittingPortalBlock.java
  anomaly/cloud/SmallCloudEmitter.java
  client/renderer/SmallStangeCloudRenderer.java
"""
import io
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

FAILED = []


def read(p):
    return io.open(p, encoding="utf-8").read()


def check(label, got, want, tol=0):
    ok = abs(got - want) <= tol if isinstance(got, (int, float)) and isinstance(want, (int, float)) else got == want
    print("  [%s] %-44s -> %s" % ("OK " if ok else "FAIL", label, got))
    if not ok:
        FAILED.append("%s: 得到 %r，期望 %r" % (label, got, want))
    return ok


def check_true(label, ok, detail=""):
    print("  [%s] %-44s %s" % ("OK " if ok else "FAIL", label, detail))
    if not ok:
        FAILED.append(label)
    return ok


# 大伪云：同一个模型，缩放 20 → 短横边 15 格（模型短边 0.75 格 × 20）
BIG_SCALE = 20.0
BIG_SHORT = 15.0
BIG_LENGTH = 30.0
BIG_HEIGHT = 5.0
BIG_EFFECT = (15.0, 7.0, 3.0)   # 长 / 短 / 垂直半径
BIG_DAMAGE = 1.0
TARGET_SHORT = 2.0              # 作者要求：短的横边是 2 格

print("=" * 68)
print("1. 尺寸链：短横边 2 格 ↔ 缩放 ↔ 碰撞箱 ↔ 伤害盒")
print("=" * 68)

scale = BIG_SCALE * (TARGET_SHORT / BIG_SHORT)
length = BIG_LENGTH * (TARGET_SHORT / BIG_SHORT)
height = BIG_HEIGHT * (TARGET_SHORT / BIG_SHORT)
check("按'短横边=2格'推出的缩放", round(scale, 4), 2.6667, 0.0001)
check("推出的视觉长边", round(length, 3), 4.0, 0.001)
check("推出的视觉高度", round(height, 3), 0.667, 0.001)

entity = read("src/main/java/net/mcreator/mcanomalyarchives/entity/SmallStangeCloudEntity.java")
m = re.search(r"RENDER_SCALE = ([0-9.]+)f", entity)
check("实体里的 RENDER_SCALE", float(m.group(1)) if m else None, 2.6667, 0.0001)
check("实体里的 VISUAL_LENGTH", float(re.search(r"VISUAL_LENGTH = ([0-9.]+)f", entity).group(1)), 4.0, 0.001)
check("实体里的 VISUAL_HEIGHT", float(re.search(r"VISUAL_HEIGHT = ([0-9.]+)f", entity).group(1)), 0.667, 0.001)
check("实体里的 VISUAL_SHORT_EDGE", float(re.search(r"VISUAL_SHORT_EDGE = ([0-9.]+)f", entity).group(1)), 2.0, 0.001)

renderer = read("src/main/java/net/mcreator/mcanomalyarchives/client/renderer/SmallStangeCloudRenderer.java")
check_true("渲染器用的是实体的 RENDER_SCALE（不另写一份数字）",
           "SmallStangeCloudEntity.RENDER_SCALE" in renderer and "scale(20f" not in renderer)
check_true("渲染器复用 ModelStrangeCloud", "ModelStrangeCloud" in renderer)
check_true("渲染器复用大伪云的贴图", "textures/entities/texture.png" in renderer)

element = json.load(io.open("elements/SmallStangeCloud.mod.json", encoding="utf-8"))
d = element["definition"]
check("元素的 _type", element["_type"], "livingentity")
check_true("元素复用同一个模型", d["mobModelName"] == "ModelStrangeCloud" and d["mobModelTexture"] == "texture.png")
check("元素碰撞箱 modelWidth（按大伪云先例取长边）", d["modelWidth"], 4.0)
check("元素碰撞箱 modelHeight", d["modelHeight"], 0.67)
check_true("元素禁止自然生成（不进生成表、不产 biome_modifier）", d["spawnThisMob"] is False)
check_true("元素仍禁碰撞（它是现象不是生物）", d["disableCollisions"] is True and d["solidBoundingBox"]["fixedValue"] is False)

ent_reg = read("src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java")
m = re.search(r'SMALL_STANGE_CLOUD = register\("small_stange_cloud".*?\.sized\(([0-9.]+)f, ([0-9.]+)f\)', ent_reg, re.S)
check_true("实体注册行存在且 .sized() 与元素一致",
           m is not None and float(m.group(1)) == 4.0 and float(m.group(2)) == 0.67,
           m.groups() if m else "没匹配到")

print()
print("=" * 68)
print("2. 伤害盒：等比缩小（大伪云的 2/15），伤害值照抄")
print("=" * 68)
ratio = TARGET_SHORT / BIG_SHORT
check("伤害盒长半径", float(re.search(r"EFFECT_LENGTH = ([0-9.]+)", entity).group(1)), round(BIG_EFFECT[0] * ratio, 3), 0.001)
check("伤害盒短半径", float(re.search(r"EFFECT_WIDTH = ([0-9.]+)", entity).group(1)), round(BIG_EFFECT[1] * ratio, 3), 0.001)
check("伤害盒垂直半径", float(re.search(r"EFFECT_RADIUS_Y = ([0-9.]+)", entity).group(1)), round(BIG_EFFECT[2] * ratio, 3), 0.001)
check("单次伤害（与大伪云一致）", float(re.search(r"DAMAGE_PER_HIT = ([0-9.]+)f", entity).group(1)), BIG_DAMAGE, 0.001)
check("伤害间隔（与大伪云一致）", int(re.search(r"DAMAGE_INTERVAL = ([0-9]+)", entity).group(1)), 10)
check_true("染水/染岩浆用的是云水与云石（与大伪云同一套）",
           "CLOUD_STONE.get().defaultBlockState()" in entity and "CLOUD_WATER.get().defaultBlockState()" in entity)
check_true("寿命 60 秒", "LIFETIME_TICKS = 1200" in entity)

print()
print("=" * 68)
print("3. 触发链：门存在 → 随机刻 → 吐出来")
print("=" * 68)
portal = read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/cloud/SmallCloudEmittingPortalBlock.java")
check_true("门户子类继承了云室门方块", "extends CloudDemPortalBlock" in portal)
check_true("门户子类覆盖了 randomTick", "public void randomTick(" in portal)
check_true("randomTick 里不调 super（保持门里不刷僵尸猪灵）",
           "SmallCloudEmitter.tryEmit(level, pos, state, random);" in portal and "super.randomTick" not in portal)

blocks = read("src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModBlocks.java")
m = re.search(r'CLOUD_DEM_PORTAL = REGISTRY\.register\("cloud_dem_portal", ([A-Za-z0-9_.]+)::new\)', blocks)
check_true("注册行确实指向吐云子类（回退会静默失效）",
           m is not None and m.group(1).endswith("SmallCloudEmittingPortalBlock"), m.group(1) if m else "没匹配到")

emitter = read("src/main/java/net/mcreator/mcanomalyarchives/anomaly/cloud/SmallCloudEmitter.java")
check("每门并发上限", int(re.search(r"MAX_PER_PORTAL = ([0-9]+)", emitter).group(1)), 2)
emit_one_in = int(re.search(r"EMIT_ONE_IN = ([0-9]+)", emitter).group(1))
# 原版随机刻：每个 16³ 区块段每 tick 抽 randomTickSpeed(=3) 个位置（ServerLevel.tickChunk）
# → 单个门方块 3/4096 每 tick；一扇 2×3 的门 6 格 → 1/228 每 tick；再除以概率门槛。
per_tick = 6.0 * 3.0 / 4096.0 / emit_one_in
seconds = 1.0 / per_tick / 20.0
check_true("平均出货间隔在'偶尔'的合理区间（30~120 秒）", 30.0 <= seconds <= 120.0,
           "约 %.0f 秒一只（EMIT_ONE_IN=%d）" % (seconds, emit_one_in))
check_true("出口侧要检查空气（否则云会卡在墙里）", "isPassable(level, pos.relative(normal))" in emitter)
check_true("出场方向靠朝向传递（两端各算一遍，不需要网络包）",
           "yawFor(exit.getStepX(), exit.getStepZ())" in emitter and "headingFromYaw" in entity)
check_true("实体运动不用 this.getRandom（两端必须算得一样）",
           "this.getRandom()" not in entity)
check_true("实体每 tick 只 setPos 一次（避免位置更新两遍抽搐）", entity.count("this.setPos(") == 1)

print()
print("=" * 68)
print("4. 集成：见闻录 / 大伪云 / 维度 id / 词条")
print("=" * 68)
codex = read("src/main/java/net/mcreator/mcanomalyarchives/codex/AnomalyCodex.java")
check_true("UO-001 支持多 id（含 small_stange_cloud）",
           "mcanomalyarchives:stange_cloud|mcanomalyarchives:small_stange_cloud" in codex)
check_true("scan() 会按 | 拆分 id", 'entry.match().split("\\\\|")' in codex)
check_true("大伪云的伤害盒排除小伪云",
           "instanceof net.mcreator.mcanomalyarchives.entity.SmallStangeCloudEntity" in
           read("src/main/java/net/mcreator/mcanomalyarchives/entity/StangeCloudEntity.java"))

bad = []
for base, _, files in os.walk("src/main/java"):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        p = os.path.join(base, fn)
        if 'clouddem"' in read(p):
            bad.append(p)
check_true("源码里再没有 clouddem 这种写错的维度 id", not bad, bad or "")

ws = json.load(io.open("mcanomalyarchives.mcreator", encoding="utf-8"))
for lg in ("zh_cn", "en_us"):
    keys = ws["language_map"][lg]
    check_true("workspace language_map.%s 有两词条" % lg,
               "entity.mcanomalyarchives.small_stange_cloud" in keys
               and "item.mcanomalyarchives.small_stange_cloud_spawn_egg" in keys)
for lg in ("zh_cn", "en_us"):
    text = read("src/main/resources/assets/mcanomalyarchives/lang/%s.json" % lg)
    check_true("lang/%s.json 有两词条" % lg,
               '"entity.mcanomalyarchives.small_stange_cloud"' in text
               and '"item.mcanomalyarchives.small_stange_cloud_spawn_egg"' in text)
check_true("元素挂在工作区里且 locked_code",
           any(e.get("name") == "SmallStangeCloud" and e.get("locked_code") for e in ws["mod_elements"]))

for p in ("textures/item/small_stange_cloud_spawn_egg_generated.png",
          "models/item/small_stange_cloud_spawn_egg.json",
          "items/small_stange_cloud_spawn_egg.json"):
    full = "src/main/resources/assets/mcanomalyarchives/" + p
    check_true("刷怪蛋资源 " + p, os.path.exists(full))

print()
if FAILED:
    print("自检失败 %d 项：" % len(FAILED))
    for f in FAILED:
        print("  -", f)
    raise SystemExit(1)
print("自检全部通过")
