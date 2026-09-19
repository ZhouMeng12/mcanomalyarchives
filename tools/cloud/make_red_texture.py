# -*- coding: utf-8 -*-
"""生成红伪云的贴图：从虞美人的贴图取主色，做成 16×16 纯色 + alpha 130。

伪云的 texture.png 是纯白纯色、层次全靠模型面光，所以这里同样只填一个颜色。
颜色来源：直接去虞美人的方块贴图里统计出现最多的"红色系"像素，保证红伪云
和虞美人看起来是同一族颜色（而不是随手挑一个红）。
"""
import io
import os
import struct
import zlib
import collections

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

SRC_TEX = "src/main/resources/assets/mcanomalyarchives/textures/block/invicon_poppy.png"   # 虞美人本体用的那张贴图
FALLBACK = "src/main/resources/assets/mcanomalyarchives/textures/entities/texture.png"
DST = "src/main/resources/assets/mcanomalyarchives/textures/entities/cloud_red.png"
ALPHA = 130


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
                              + chunk(b"IDAT", zlib.compress(raw, 9))
                              + chunk(b"IEND", b""))


# ---- 从虞美人贴图取红色主色 ----
def dominant_red(path):
    w, h, rows = read_png(path)
    counter = collections.Counter()
    for row in rows:
        for x in range(w):
            r, g, b, a = row[x * 4:x * 4 + 4]
            if a < 200 or r < 60 or r <= g + 20:   # 只看不透明的偏红像素
                continue
            counter[(r // 16 * 16, g // 16 * 16, b // 16 * 16)] += 1
    return counter.most_common(1)[0][0] if counter else None


red = dominant_red(SRC_TEX) if os.path.exists(SRC_TEX) else None
if red is None:
    red = (176, 32, 40)   # 虞美人贴图取不到就退回一个罂粟红
    print("（虞美人贴图取不到主色，用默认罂粟红 %s）" % (red,))
else:
    print("虞美人贴图里的主色：", red)

w, h, _ = read_png(FALLBACK)   # 尺寸沿用云贴图
rows = []
for _y in range(h):
    row = bytearray()
    for _x in range(w):
        row += bytes((red[0], red[1], red[2], ALPHA))
    rows.append(row)
write_png(DST, w, h, rows)
print("写出 %s：%dx%d 纯色 #%02X%02X%02X alpha %d" % (DST, w, h, red[0], red[1], red[2], ALPHA))
