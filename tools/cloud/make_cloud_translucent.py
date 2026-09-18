# -*- coding: utf-8 -*-
"""把伪云共用的那张贴图弄成半透明，配合渲染器的 translucent 渲染类型。

原贴图的不透明像素 alpha 是 215（作者画的时候没用满），而生物默认的
entityCutoutNoCull 是"抠图"式渲染（alpha 只当阈值用），所以现在看着是全不透明的。
这里把不透明像素的 alpha 统一压到 TARGET_ALPHA，再让两个云渲染器走
RenderType.entityTranslucent —— 整只云就有了半透明效果。

幂等：已经是目标 alpha 就什么都不动。
"""
import io
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

PATH = "src/main/resources/assets/mcanomalyarchives/textures/entities/texture.png"
TARGET_ALPHA = 130  # ≈51%：明显透光，又不至于看不见


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
    rows = []
    prev = bytearray(width * 4)
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


width, height, rows = read_png(PATH)
before = set()
changed = 0
out = []
for row in rows:
    new_row = bytearray(row)
    for x in range(width):
        a = row[x * 4 + 3]
        before.add(a)
        if a == 0:
            continue
        if a != TARGET_ALPHA:
            new_row[x * 4 + 3] = TARGET_ALPHA
            changed += 1
    out.append(new_row)

if changed:
    write_png(PATH, width, height, out)
print("%s：%dx%d，alpha %s → %d，改了 %d 个像素" % (PATH, width, height, sorted(before), TARGET_ALPHA, changed))
