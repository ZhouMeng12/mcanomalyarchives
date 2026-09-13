"""深度模式：下载 B 站视频 → 合并 → 抽 16k 单声道音轨 → 每 5s 抽帧。

用法: python deep_capture.py <BV号> <输出前缀目录>
产物（全部落在 out_dir 下）:
  <slug>_v.f30064.mp4 / _a.m4a / full.mp4 / audio16k.wav / frames/f_%04d.jpg

说明: 720P 匿名可下；遇 412 风控重试即可。合并/抽帧用 imageio-ffmpeg 的便携 ffmpeg。
"""
import os
import subprocess
import sys
import time

import imageio_ffmpeg
from yt_dlp import YoutubeDL

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36"


def ydl_opts(out_dir: str, slug: str, fmt: str):
    return {
        "format": fmt,
        "outtmpl": os.path.join(out_dir, slug + ".%(ext)s"),
        # 必须告诉 yt-dlp 便携 ffmpeg 的位置，否则分流合并会直接 abort
        "ffmpeg_location": FFMPEG,
        "merge_output_format": "mp4",
        "http_headers": {"Referer": "https://www.bilibili.com", "User-Agent": UA},
        "quiet": False,
        "no_warnings": True,
        "no_playlist": True,
        "retries": 8,
        "fragment_retries": 8,
        "concurrent_fragment_downloads": 4,
    }


def download(url: str, out_dir: str, slug: str) -> str:
    """下载并让 yt-dlp 直接合并出 <slug>.mp4（合并需要 ffmpeg_location）。"""
    last = None
    for attempt in range(1, 6):
        try:
            with YoutubeDL(ydl_opts(out_dir, slug, "bv*[height<=720]+ba/b[height<=720]")) as ydl:
                info = ydl.extract_info(url, download=True)
            path = ydl.prepare_filename(info)
            if not os.path.exists(path):
                # 合并后扩展名可能变化
                stem = os.path.splitext(path)[0]
                for ext in (".mp4", ".mkv", ".webm"):
                    if os.path.exists(stem + ext):
                        path = stem + ext
                        break
            print("downloaded:", path)
            return path
        except Exception as exc:  # noqa: BLE001
            last = exc
            print(f"[attempt {attempt}] failed: {exc}", file=sys.stderr)
            time.sleep(5)
    raise SystemExit(f"download failed: {last}")


def run(args):
    print("+", " ".join(args))
    subprocess.run(args, check=True)


def main() -> int:
    url, out_dir = sys.argv[1], sys.argv[2]
    slug = sys.argv[3] if len(sys.argv) > 3 else "vid"
    os.makedirs(out_dir, exist_ok=True)
    download(url, out_dir, slug)

    # yt-dlp 已合并时可能留下 <slug>.fVIDEO.mp4 / <slug>.fAUDIO.m4a，这里兜底再合一次
    full = os.path.join(out_dir, f"{slug}_full.mp4")
    if not os.path.exists(full):
        parts = sorted(f for f in os.listdir(out_dir)
                       if f.startswith(slug + ".") and f.endswith((".mp4", ".m4a", ".webm")))
        print("parts:", parts)
        vids = [p for p in parts if p.endswith((".mp4", ".webm")) and ".f" in p]
        auds = [p for p in parts if p.endswith(".m4a")]
        if vids and auds:
            run([FFMPEG, "-y", "-loglevel", "error",
                 "-i", os.path.join(out_dir, vids[0]), "-i", os.path.join(out_dir, auds[0]),
                 "-c", "copy", full])
        else:
            single = os.path.join(out_dir, f"{slug}.mp4")
            if not os.path.exists(single):
                raise SystemExit(f"no downloadable parts found in {out_dir}: {parts}")
            run([FFMPEG, "-y", "-loglevel", "error", "-i", single, "-c", "copy", full])

    wav = os.path.join(out_dir, f"{slug}_audio16k.wav")
    if not os.path.exists(wav):
        run([FFMPEG, "-y", "-loglevel", "error", "-i", full, "-vn", "-ac", "1", "-ar", "16000", wav])

    frames = os.path.join(out_dir, "frames")
    os.makedirs(frames, exist_ok=True)
    if not any(f.endswith(".jpg") for f in os.listdir(frames)):
        run([FFMPEG, "-y", "-loglevel", "error", "-i", full, "-vf", "fps=1/5,scale=640:-1", "-q:v", "4",
             os.path.join(frames, "f_%04d.jpg")])
    n = len([f for f in os.listdir(frames) if f.endswith(".jpg")])
    print(f"DONE full={full} wav={wav} frames={n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
