"""抓取 B 站弹幕 XML 并转成 TSV（秒 \t 文本）。

用法: python fetch_danmaku.py <cid> <out_tsv>
说明: 必须显式声明 Accept-Encoding: identity，否则 B 站返回 deflate/gzip 二进制。
"""
import gzip
import io
import re
import sys
import urllib.request
import zlib

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36"


def fetch(cid: int) -> str:
    url = f"https://comment.bilibili.com/{cid}.xml"
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Encoding": "identity"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read()
        enc = (resp.headers.get("Content-Encoding") or "").lower()
    if raw[:2] == b"\x1f\x8b" or "gzip" in enc:
        raw = gzip.decompress(raw)
    elif enc == "deflate" or raw[:1] == b"\x78":
        try:
            raw = zlib.decompress(raw)
        except zlib.error:
            raw = zlib.decompress(raw, -zlib.MAX_WBITS)
    return raw.decode("utf-8", errors="replace")


def main() -> int:
    cid = int(sys.argv[1])
    out = sys.argv[2]
    xml = fetch(cid)
    rows = []
    for m in re.finditer(r'<d p="([^"]*)">(.*?)</d>', xml, re.S):
        p, text = m.group(1), m.group(2)
        secs = float(p.split(",")[0])
        text = (text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", '"').replace("&#39;", "'").replace("\n", " "))
        rows.append((secs, text))
    rows.sort(key=lambda r: r[0])
    with io.open(out, "w", encoding="utf-8", newline="\n") as fh:
        for secs, text in rows:
            fh.write(f"{secs:.1f}\t{text}\n")
    print(f"cid={cid} danmaku={len(rows)} -> {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
