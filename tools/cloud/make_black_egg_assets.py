# -*- coding: utf-8 -*-
"""黑吸伪云刷怪蛋：贴图 + 两份模型定义。

贴图同样从大伪云的蛋派生（16×16 灰阶蛋），但这次往乌黑压：
轮廓与明暗完全照抄，RGB 统一乘 0.16 再抬一点点冷调，得到"黑蛋"。
"""
import io
import json
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

SRC = "src/main/resources/assets/mcanomalyarchives/textures/item/stange_cloud_spawn_egg_generated.png"
DST = "src/main/resources/assets/mcanomalyarchives/textures/item/black_stange_cloud_spawn_egg_generated.png"
MODEL = "src/main/resources/assets/mcanomalyarchives/models/item/black_stange_cloud_spawn_egg.json"
ITEM_DEF = "src/main/resources/assets/mcanomalyarchives/items/black_stange_cloud_spawn_egg.json"
TINT = (1.0, 1.0, 1.25)   # 冷调
BASE = 0.16               # 压到乌黑


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

    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    io.open(path, "wb").write(data)


width, height, rows = read_png(SRC)
out = []
for row in rows:
    new_row = bytearray(len(row))
    for x in range(width):
        r, g, b, a = row[x * 4:x * 4 + 4]
        if a == 0:
            new_row[x * 4:x * 4 + 4] = bytes((0, 0, 0, 0))
            continue
        nr = max(18, min(255, int(round(r * BASE * TINT[0]))))
        ng = max(18, min(255, int(round(g * BASE * TINT[1]))))
        nb = max(20, min(255, int(round(b * BASE * TINT[2]))))
        new_row[x * 4:x * 4 + 4] = bytes((nr, ng, nb, a))
    out.append(new_row)

write_png(DST, width, height, out)
print("写出贴图", DST)

io.open(MODEL, "w", encoding="utf-8", newline="\n").write(json.dumps({
    "parent": "item/generated",
    "textures": {"layer0": "mcanomalyarchives:item/black_stange_cloud_spawn_egg_generated"}
}, ensure_ascii=False, indent=2) + "\n")
io.open(ITEM_DEF, "w", encoding="utf-8", newline="\n").write(json.dumps({
    "model": {"type": "minecraft:model", "model": "mcanomalyarchives:item/black_stange_cloud_spawn_egg"}
}, ensure_ascii=False, indent=2) + "\n")
print("写出模型与物品定义")
