# -*- coding: utf-8 -*-
"""把红伪云补进四张生成区注册表 + 生成刷怪蛋资源（MCreator 重生成时会自己产出这些行）。"""
import io
import json
import os
import re
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

E = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntities.java"
R = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModEntityRenderers.java"
I = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModItems.java"
T = "src/main/java/net/mcreator/mcanomalyarchives/init/McanomalyarchivesModTabs.java"
EGG_SRC = "src/main/resources/assets/mcanomalyarchives/textures/item/stange_cloud_spawn_egg_generated.png"
EGG_DST = "src/main/resources/assets/mcanomalyarchives/textures/item/red_stange_cloud_spawn_egg_generated.png"
EGG_MODEL = "src/main/resources/assets/mcanomalyarchives/models/item/red_stange_cloud_spawn_egg.json"
EGG_ITEM = "src/main/resources/assets/mcanomalyarchives/items/red_stange_cloud_spawn_egg.json"


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, s):
    io.open(p, "w", encoding="utf-8", newline="").write(s)


def insert_after(text, anchor, addition, label):
    assert anchor in text, "找不到锚点（%s）" % label
    if addition.strip() in text:
        print("-- %s 已补过" % label)
        return text
    print("OK %s" % label)
    return text.replace(anchor, anchor + addition, 1)


s = read(E)
s = insert_after(s, "\t\tSmallStangeCloudEntity.init(event);\n", "\t\tRedStangeCloudEntity.init(event);\n", "实体 init")
s = insert_after(s, "\t\tevent.put(SMALL_STANGE_CLOUD.get(), SmallStangeCloudEntity.createAttributes().build());\n",
                 "\t\tevent.put(RED_STANGE_CLOUD.get(), RedStangeCloudEntity.createAttributes().build());\n", "实体属性")
if "RED_STANGE_CLOUD = register" not in s:
    anchor = "\tpublic static final DeferredHolder<EntityType<?>, EntityType<BlackStangeCloudEntity>> BLACK_STANGE_CLOUD"
    assert anchor in s, "找不到黑吸伪云的注册行作锚点"
    end = s.index(";", s.index(".sized(", s.index(anchor))) + 1
    s = (s[:end] + """

\tpublic static final DeferredHolder<EntityType<?>, EntityType<RedStangeCloudEntity>> RED_STANGE_CLOUD = register("red_stange_cloud",
\t\t\tEntityType.Builder.<RedStangeCloudEntity>of(RedStangeCloudEntity::new, MobCategory.AMBIENT).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).fireImmune()

\t\t\t\t\t.sized(4f, 0.67f));""" + s[end:])
    print("OK 实体注册")
else:
    print("-- 实体注册已补过")
write(E, s)

s = read(R)
s = insert_after(s, "event.registerEntityRenderer(McanomalyarchivesModEntities.SMALL_STANGE_CLOUD.get(), SmallStangeCloudRenderer::new);\n",
                 "\t\tevent.registerEntityRenderer(McanomalyarchivesModEntities.RED_STANGE_CLOUD.get(), RedStangeCloudRenderer::new);\n",
                 "渲染器注册")
write(R, s)

s = read(I)
s = insert_after(s, "\tpublic static final DeferredItem<Item> SMALL_STANGE_CLOUD_SPAWN_EGG;\n",
                 "\tpublic static final DeferredItem<Item> RED_STANGE_CLOUD_SPAWN_EGG;\n", "刷怪蛋声明")
s = insert_after(s, '\t\tSMALL_STANGE_CLOUD_SPAWN_EGG = REGISTRY.register("small_stange_cloud_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.SMALL_STANGE_CLOUD, -1, -1, new Item.Properties()));\n',
                 '\t\tRED_STANGE_CLOUD_SPAWN_EGG = REGISTRY.register("red_stange_cloud_spawn_egg", () -> new DeferredSpawnEggItem(McanomalyarchivesModEntities.RED_STANGE_CLOUD, -1, -1, new Item.Properties()));\n',
                 "刷怪蛋注册")
write(I, s)

s = read(T)
s = insert_after(s, "\t\ttabData.accept(McanomalyarchivesModItems.SMALL_STANGE_CLOUD_SPAWN_EGG.get());\n",
                 "\t\ttabData.accept(McanomalyarchivesModItems.RED_STANGE_CLOUD_SPAWN_EGG.get());\n", "创造栏")
write(T, s)


# ---- 刷怪蛋贴图：从大伪云那张灰阶蛋派生，往红偏 ----
def read_png(path):
    data = open(path, "rb").read()
    pos, idat, width, height = 8, b"", None, None
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            width, height = struct.unpack(">II", chunk[:8])
        elif typ == b"IDAT":
            idat += chunk
        pos += 12 + ln
    raw = zlib.decompress(idat)
    stride = width * 4 + 1
    rows, prev = [], bytearray(width * 4)
    for y in range(height):
        f = raw[y * stride]
        line = bytearray(raw[y * stride + 1:(y + 1) * stride])
        if f == 0:
            cur = line
        elif f == 1:
            cur = bytearray(len(line))
            for i in range(len(line)):
                cur[i] = (line[i] + (cur[i - 4] if i >= 4 else 0)) & 255
        elif f == 2:
            cur = bytearray((line[i] + prev[i]) & 255 for i in range(len(line)))
        elif f == 3:
            cur = bytearray(len(line))
            for i in range(len(line)):
                a = cur[i - 4] if i >= 4 else 0
                cur[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            cur = bytearray(len(line))
            for i in range(len(line)):
                a = cur[i - 4] if i >= 4 else 0
                b = prev[i]
                c = prev[i - 4] if i >= 4 else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                cur[i] = (line[i] + pr) & 255
        else:
            raise SystemExit("不支持的 filter %d" % f)
        prev = cur
        rows.append(cur)
    return width, height, rows


def write_png(path, width, height, rows):
    raw = b"".join(b"\x00" + bytes(r) for r in rows)

    def chunk(typ, payload):
        return (struct.pack(">I", len(payload)) + typ + payload
                + struct.pack(">I", zlib.crc32(typ + payload) & 0xFFFFFFFF))

    io.open(path, "wb").write(b"\x89PNG\r\n\x1a\n"
                              + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                              + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


w, h, rows = read_png(EGG_SRC)
out = []
for row in rows:
    new_row = bytearray(len(row))
    for x in range(w):
        r, g, b, a = row[x * 4:x * 4 + 4]
        if a == 0:
            new_row[x * 4:x * 4 + 4] = bytes((0, 0, 0, 0))
            continue
        # 灰阶 → 红：红通道保留亮度，绿蓝压掉
        nr = min(255, int(round(r * 1.0)))
        ng = min(255, int(round(g * 0.22)))
        nb = min(255, int(round(b * 0.24)))
        new_row[x * 4:x * 4 + 4] = bytes((nr, ng, nb, a))
    out.append(new_row)
write_png(EGG_DST, w, h, out)
print("OK 刷怪蛋贴图", EGG_DST)

io.open(EGG_MODEL, "w", encoding="utf-8", newline="\n").write(json.dumps({
    "parent": "item/generated",
    "textures": {"layer0": "mcanomalyarchives:item/red_stange_cloud_spawn_egg_generated"}
}, ensure_ascii=False, indent=2) + "\n")
io.open(EGG_ITEM, "w", encoding="utf-8", newline="\n").write(json.dumps({
    "model": {"type": "minecraft:model", "model": "mcanomalyarchives:item/red_stange_cloud_spawn_egg"}
}, ensure_ascii=False, indent=2) + "\n")
print("OK 刷怪蛋模型与物品定义")
