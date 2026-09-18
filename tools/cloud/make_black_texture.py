# -*- coding: utf-8 -*-
"""生成黑吸伪云的贴图：16×16 纯色「乌黑」，alpha 与小伪云同一档。

为什么是纯色填充：伪云那张 texture.png 我逐像素读过 —— **256 个像素全是纯白 (255,255,255)**，
现在看到的灰白层次完全来自模型各个面的方向光，跟贴图无关。所以黑色版也只能是纯色，
要定的只是"哪种黑"。

定的是 **乌黑 #141419（20,20,25）**，不是纯黑 #000000：
纯黑在游戏里会变成一片没有层次的剪影，加一丝几乎看不出的冷调 + 模型面光，
实际渲染会在 #0F0F13 ~ #2A2A31 之间浮动，是一团有厚度的黑雾。想更深/更浅只改 BLACK_RGB。
"""
import io
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

SRC = "src/main/resources/assets/mcanomalyarchives/textures/entities/texture.png"
DST = "src/main/resources/assets/mcanomalyarchives/textures/entities/cloud_black.png"
BLACK_RGB = (20, 20, 25)   # 乌黑
ALPHA = 130                # 与小伪云同档：渲染器走 entityTranslucent，这是"整只半透明"的一半


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
colors = set()
for row in rows:
    for x in range(width):
        if row[x * 4 + 3] > 0:
            colors.add(tuple(row[x * 4:x * 4 + 3]))
# 断言前提：源贴图就是纯色（否则"纯色填充"这个做法本身要重新想）
assert len(colors) == 1, "源贴图的颜色不止一种了（%d 种），这个脚本的前提失效，请重新设计黑色版" % len(colors)
src_color = colors.pop()
print("源贴图：%dx%d，不透明像素只有一种颜色 %s" % (width, height, src_color))

out = []
for row in rows:
    new_row = bytearray(len(row))
    for x in range(width):
        a = row[x * 4 + 3]
        if a == 0:
            new_row[x * 4:x * 4 + 4] = bytes((0, 0, 0, 0))
        else:
            new_row[x * 4:x * 4 + 4] = bytes((BLACK_RGB[0], BLACK_RGB[1], BLACK_RGB[2], ALPHA))
    out.append(new_row)

write_png(DST, width, height, out)
print("写出 %s：纯色 #%02X%02X%02X，alpha %d" % (DST, BLACK_RGB[0], BLACK_RGB[1], BLACK_RGB[2], ALPHA))
