# -*- coding: utf-8 -*-
"""生成小伪云刷怪蛋的贴图与物品模型。

贴图从大伪云的蛋（stange_cloud_spawn_egg_generated.png，一张 16×16 的灰阶蛋）派生：
**轮廓与明暗完全照抄**，只把颜色往灰蓝偏一点（云水那一路的色调），
这样两个蛋在创造栏里一眼能分清，又明显是同一族。

顺带写出 1.21.1 的两份模型定义：
  models/item/small_stange_cloud_spawn_egg.json  → parent item/generated，指向本贴图
  items/small_stange_cloud_spawn_egg.json        → 1.21 的物品定义（指向上面的模型）
"""
import io
import json
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

SRC = "src/main/resources/assets/mcanomalyarchives/textures/item/stange_cloud_spawn_egg_generated.png"
DST = "src/main/resources/assets/mcanomalyarchives/textures/item/small_stange_cloud_spawn_egg_generated.png"
MODEL = "src/main/resources/assets/mcanomalyarchives/models/item/small_stange_cloud_spawn_egg.json"
ITEM_DEF = "src/main/resources/assets/mcanomalyarchives/items/small_stange_cloud_spawn_egg.json"

# 灰阶 → 灰蓝：红降一点、蓝保满。写死系数，肉眼可辨但不刺眼。
TINT = (0.78, 0.86, 0.98)


def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "不是 PNG"
    pos, idat = 8, b""
    width = height = None
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
    rows = []
    prev = bytearray(width * 4)
    for y in range(height):
        f = raw[y * stride]
        line = bytearray(raw[y * stride + 1:(y + 1) * stride])
        if f == 0:
            cur = line
        elif f == 2:
            cur = bytearray((line[i] + prev[i]) & 255 for i in range(len(line)))
        elif f == 1:
            cur = bytearray(len(line))
            for i in range(len(line)):
                cur[i] = (line[i] + (cur[i - 4] if i >= 4 else 0)) & 255
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

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    io.open(path, "wb").write(data)


width, height, rows = read_png(SRC)
out = []
changed = 0
for row in rows:
    new_row = bytearray(len(row))
    for x in range(width):
        r, g, b, a = row[x * 4:x * 4 + 4]
        if a == 0:
            new_row[x * 4:x * 4 + 4] = bytes((0, 0, 0, 0))
            continue
        nr = min(255, int(round(r * TINT[0])))
        ng = min(255, int(round(g * TINT[1])))
        nb = min(255, int(round(b * TINT[2])))
        if (nr, ng, nb) != (r, g, b):
            changed += 1
        new_row[x * 4:x * 4 + 4] = bytes((nr, ng, nb, a))
    out.append(new_row)

write_png(DST, width, height, out)
print("写出贴图 %s（%dx%d，改了 %d 个不透明像素）" % (DST, width, height, changed))

io.open(MODEL, "w", encoding="utf-8", newline="\n").write(json.dumps({
    "parent": "item/generated",
    "textures": {"layer0": "mcanomalyarchives:item/small_stange_cloud_spawn_egg_generated"}
}, ensure_ascii=False, indent=2) + "\n")
print("写出模型", MODEL)

io.open(ITEM_DEF, "w", encoding="utf-8", newline="\n").write(json.dumps({
    "model": {"type": "minecraft:model", "model": "mcanomalyarchives:item/small_stange_cloud_spawn_egg"}
}, ensure_ascii=False, indent=2) + "\n")
print("写出物品定义", ITEM_DEF)
