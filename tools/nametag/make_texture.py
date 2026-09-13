#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate the "baibian_name_tag" (百变命名牌) 16x16 item texture for the
MCAnomalyArchives NeoForge mod.

Source : vanilla name tag sprite, read straight out of the NeoForge
         client-extra resources jar (no network access required).
Method : the vanilla sprite is treated as a LUMINANCE MAP.  Every opaque
         pixel keeps its relative brightness (a strictly monotone mapping,
         so light/dark layering and the pixel outline survive), while
         hue/saturation are replaced with a single dark-brown tone.
         The alpha channel is copied through untouched.

Only the standard library plus Pillow are used.  Nothing is installed and
nothing is downloaded.

Run:  python tools/nametag/make_texture.py
"""

import colorsys
import hashlib
import io
import os
import struct
import sys
import zipfile

# --------------------------------------------------------------------------
# Paths / constants
# --------------------------------------------------------------------------
WORKSPACE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

SRC_JAR = os.path.join(
    WORKSPACE,
    "build", "moddev", "artifacts",
    "neoforge-21.1.190-client-extra-aka-minecraft-resources.jar",
)
SRC_ENTRY = "assets/minecraft/textures/item/name_tag.png"

OUT_PNG = os.path.join(
    WORKSPACE,
    "src", "main", "resources", "assets", "mcanomalyarchives",
    "textures", "item", "baibiannametag.png",
)

# Dark-brown target hue/saturation: H=25deg, S=0.50 reproduces the requested
# reference palette (#4A3423 .. #5C4230) once the Value is driven by luminance.
TARGET_HUE = 25.0 / 360.0
TARGET_SAT = 0.50

# Normalised luminance window the source brightness range is mapped into.
# Both ends stay far below the vanilla sprite's brightest pixel, so the whole
# texture is unambiguously darker while keeping its internal contrast.
LUM_MIN = 0.06
LUM_MAX = 0.26

# Rec.709 relative luminance weights.
WR, WG, WB = 0.2126, 0.7152, 0.0722

MAGIC = b"\x89PNG\r\n\x1a\n"


# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------
def read_source_png():
    """Pull the vanilla name tag out of the resources jar."""
    if not os.path.isfile(SRC_JAR):
        raise SystemExit("source jar not found: %s" % SRC_JAR)
    with zipfile.ZipFile(SRC_JAR) as zf:
        names = [n for n in zf.namelist() if n.endswith("name_tag.png")]
        if SRC_ENTRY not in names:
            raise SystemExit("entry %s missing (found: %s)" % (SRC_ENTRY, names))
        raw = zf.read(SRC_ENTRY)
    print("source jar   : %s" % SRC_JAR)
    print("source entry : %s" % SRC_ENTRY)
    print("source bytes : %d" % len(raw))
    print("source sha256: %s" % hashlib.sha256(raw).hexdigest())
    return raw


def to_pixels(rgba_img):
    """RGBA image -> flat list of (r, g, b, a) int tuples (no deprecation warnings)."""
    buf = rgba_img.tobytes()
    if len(buf) != rgba_img.width * rgba_img.height * 4:
        raise SystemExit("unexpected raw size from Pillow")
    return [tuple(buf[i:i + 4]) for i in range(0, len(buf), 4)]


def lum709(px):
    """Rec.709 luminance on the 0..255 scale."""
    return WR * px[0] + WG * px[1] + WB * px[2]


def dark_brown_for_luminance(l_norm):
    """
    Map a normalised source luminance (0..255 scale normalised form) to a
    dark-brown RGB triple with exactly the requested relative brightness.

    HSV is used with a fixed hue/saturation; V is solved so that the resulting
    Rec.709 luminance equals the target luminance exactly.  Because V is a
    strictly increasing linear function of the source luminance, the ordering
    and the spacing of the original light/dark levels are preserved, and
    R > G > B holds for every output pixel.
    """
    v = l_norm / LUM_UNIT_LUM
    if v > 1.0:
        v = 1.0
    r, g, b = colorsys.hsv_to_rgb(TARGET_HUE, TARGET_SAT, v)
    return (int(round(r * 255.0)), int(round(g * 255.0)), int(round(b * 255.0)))


# Luminance of the fully-saturated target hue/sat unit colour, computed once.
_unit = colorsys.hsv_to_rgb(TARGET_HUE, TARGET_SAT, 1.0)
LUM_UNIT_LUM = WR * _unit[0] + WG * _unit[1] + WB * _unit[2]


def build_target(source_pixels):
    """Tone-map the opaque pixels; pass the alpha channel straight through."""
    opaque_lums = [lum709(p) for p in source_pixels if p[3] != 0]
    lo, hi = min(opaque_lums), max(opaque_lums)
    span = hi - lo

    out = []
    for p in source_pixels:
        a = p[3]
        if a == 0:
            out.append((0, 0, 0, 0))          # fully transparent stays fully transparent
            continue
        t = (lum709(p) - lo) / span if span > 0 else 1.0
        target_lum = (LUM_MIN + t * (LUM_MAX - LUM_MIN)) * 255.0
        r, g, b = dark_brown_for_luminance(target_lum / 255.0)
        out.append((r, g, b, a))
    return out, lo, hi


def png_ihdr(path):
    """Parse magic + IHDR straight out of the written file (independent check)."""
    with open(path, "rb") as fh:
        blob = fh.read(33)
    ok_magic = blob[:8] == MAGIC
    ok_chunk = blob[12:16] == b"IHDR"
    w, h, depth, ctype, comp, filt, inter = struct.unpack(">IIBBBBB", blob[16:29])
    return {
        "magic": ok_magic,
        "first_chunk": blob[12:16].decode("latin-1"),
        "width": w, "height": h, "bitdepth": depth, "colortype": ctype,
        "compression": comp, "filter": filt, "interlace": inter,
        "header_ok": ok_magic and ok_chunk and depth == 8 and ctype == 6 and (w, h) == (16, 16),
    }


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------
def main():
    try:
        from PIL import Image
    except ImportError:
        raise SystemExit("Pillow is required but not importable")

    raw = read_source_png()
    src_img = Image.open(io.BytesIO(raw))
    print("source mode  : %s  size=%s  format=%s" % (src_img.mode, src_img.size, src_img.format))
    src_rgba = src_img.convert("RGBA")
    if src_rgba.size != (16, 16):
        raise SystemExit("source is not 16x16: %s" % (src_rgba.size,))
    src_px = to_pixels(src_rgba)
    src_opaque = sum(1 for p in src_px if p[3] != 0)

    out_px, lo, hi = build_target(src_px)

    out_img = Image.new("RGBA", (16, 16))
    out_img.putdata(out_px)
    os.makedirs(os.path.dirname(OUT_PNG), exist_ok=True)
    out_img.save(OUT_PNG, format="PNG", optimize=False)
    print("\noutput png   : %s" % OUT_PNG)

    # ---------------- self-check 1: file / header -------------------------
    size_bytes = os.path.getsize(OUT_PNG)
    hdr = png_ihdr(OUT_PNG)
    print("\n=== CHECK 1: file & IHDR ===")
    print("exists=%s  bytes=%d" % (os.path.isfile(OUT_PNG), size_bytes))
    print("magic_ok=%s  first_chunk=%s" % (hdr["magic"], hdr["first_chunk"]))
    print("width=%d height=%d bitdepth=%d colortype=%d (6=RGBA) compression=%d filter=%d interlace=%d"
          % (hdr["width"], hdr["height"], hdr["bitdepth"], hdr["colortype"],
             hdr["compression"], hdr["filter"], hdr["interlace"]))
    print("sha256=%s" % hashlib.sha256(open(OUT_PNG, "rb").read()).hexdigest())
    print("CHECK1 PASS=%s" % (os.path.isfile(OUT_PNG) and size_bytes > 0 and hdr["header_ok"]))

    # ---------------- self-check 2: per-pixel alpha equality --------------
    chk_img = Image.open(OUT_PNG).convert("RGBA")
    chk_px = to_pixels(chk_img)
    if len(chk_px) != 256:
        raise SystemExit("output is not 256 pixels")
    alpha_mismatch = [i for i in range(256) if chk_px[i][3] != src_px[i][3]]
    src_trans = sum(1 for p in src_px if p[3] == 0)
    out_trans = sum(1 for p in chk_px if p[3] == 0)
    out_opaque = 256 - out_trans
    # transparent-pixel RGB: confirms nothing was painted into the empty area
    trans_rgb_src = {p[:3] for p in src_px if p[3] == 0}
    trans_rgb_out = {p[:3] for p in chk_px if p[3] == 0}
    print("\n=== CHECK 2: per-pixel alpha identity ===")
    print("alpha mismatches     : %d" % len(alpha_mismatch))
    print("source opaque pixels : %d" % src_opaque)
    print("output opaque pixels : %d" % out_opaque)
    print("opaque count equal   : %s" % (src_opaque == out_opaque))
    print("source transparent   : %d   output transparent: %d" % (src_trans, out_trans))
    print("transparent RGB src  : %s" % sorted(trans_rgb_src))
    print("transparent RGB out  : %s" % sorted(trans_rgb_out))
    print("CHECK2 PASS=%s" % (not alpha_mismatch and src_opaque == out_opaque
                              and src_trans == out_trans))

    # ---------------- self-check 3: colour statistics ---------------------
    op = [p for p in chk_px if p[3] != 0]
    chans = {}
    for idx, name in enumerate("RGB"):
        vals = [p[idx] for p in op]
        chans[name] = (min(vals), max(vals), sum(vals) / len(vals))
    dark = min(op, key=lum709)
    bright = max(op, key=lum709)
    mono = all(p[0] > p[1] > p[2] for p in op)
    distinct = sorted({p[:3] for p in op}, key=lambda c: lum709(c))

    print("\n=== CHECK 3: output colour statistics (opaque pixels, n=%d) ===" % len(op))
    for name in "RGB":
        mn, mx, av = chans[name]
        print("  %s  min=%3d  max=%3d  mean=%7.3f" % (name, mn, mx, av))
    print("  darkest  pixel RGB = %s  (L=%.2f)" % (dark[:3], lum709(dark)))
    print("  brightest pixel RGB = %s  (L=%.2f)" % (bright[:3], lum709(bright)))
    print("  every pixel has R>G>B : %s" % mono)
    print("  distinct output colours: %d -> %s" % (len(distinct), ["#%02X%02X%02X" % c for c in distinct]))
    print("  source L range : %.2f .. %.2f" % (lo, hi))
    print("  output L range : %.2f .. %.2f" % (lum709(dark), lum709(bright)))
    print("  brightest output is darker than source brightest: %s"
          % (lum709(bright) < lum709(max((p for p in src_px if p[3] != 0), key=lum709))))
    print("CHECK3 PASS=%s" % (mono and all(chans[c][0] < chans[c][1] for c in "RGB")))

    # ---------------- ascii preview --------------------------------------
    print("\n=== preview ('.'=transparent) ===")
    for y in range(16):
        row = []
        for x in range(16):
            p = chk_px[y * 16 + x]
            row.append("......" if p[3] == 0 else "%02X%02X%02X" % p[:3])
        print("%2d %s" % (y, " ".join(row)))

    print("\nDONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
