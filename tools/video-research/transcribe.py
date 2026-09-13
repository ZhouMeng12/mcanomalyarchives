"""音频转写：faster-whisper 中文转写，输出带时间戳的台词文本。

用法: python transcribe.py <wav> <out_txt> [model]
默认 small + int8（CPU），language=zh，vad_filter=True。
首次运行会下载模型（~500MB），之后走缓存。
"""
import io
import sys
import time

from faster_whisper import WhisperModel


def fmt(sec: float) -> str:
    m, s = divmod(sec, 60)
    return f"{int(m):02d}:{s:05.1f}"


def main() -> int:
    wav, out = sys.argv[1], sys.argv[2]
    name = sys.argv[3] if len(sys.argv) > 3 else "small"
    t0 = time.time()
    model = WhisperModel(name, device="cpu", compute_type="int8")
    print(f"model {name} loaded in {time.time() - t0:.1f}s", flush=True)
    segments, info = model.transcribe(wav, language="zh", vad_filter=True,
                                      beam_size=5, condition_on_previous_text=False)
    print(f"language={info.language} p={info.language_probability:.2f}", flush=True)
    n = 0
    with io.open(out, "w", encoding="utf-8", newline="\n") as fh:
        for seg in segments:
            text = seg.text.strip()
            if not text:
                continue
            n += 1
            fh.write(f"[{fmt(seg.start)}-{fmt(seg.end)}] {text}\n")
            if n % 25 == 0:
                print(f"  ...{n} segments ({fmt(seg.end)})", flush=True)
    print(f"DONE segments={n} -> {out} in {time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
