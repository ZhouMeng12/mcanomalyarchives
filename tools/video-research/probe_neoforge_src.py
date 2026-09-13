"""从 NeoForge sources jar 里抽取指定类/JSON 片段，供方案撰写时核对 API。"""
import io
import os
import re
import sys
import zipfile

JAR = (r"C:\Users\Administrator\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge"
       r"\21.1.190\4abd7b9f3852f57bae69982938023b62ef281752\neoforge-21.1.190-sources.jar")

if os.environ.get("PROBE_JAR"):
    JAR = os.environ["PROBE_JAR"]


def find(patterns, context=0, max_hits=3):
    with zipfile.ZipFile(JAR) as zf:
        names = [n for n in zf.namelist() if n.endswith(".java")]
        for pat in patterns:
            hits = 0
            print(f"\n########## {pat}")
            for name in names:
                text = zf.read(name).decode("utf-8", "replace")
                if re.search(pat, text, re.I):
                    hits += 1
                    print(f"--- {name}")
                    if context:
                        lines = text.splitlines()
                        for i, line in enumerate(lines):
                            if re.search(pat, line, re.I):
                                lo, hi = max(0, i - context), min(len(lines), i + context + 1)
                                print("\n".join(lines[lo:hi]))
                                print("  ...")
                    if hits >= max_hits:
                        break
            if hits == 0:
                print("(no match)")


def dump(entry: str) -> None:
    with zipfile.ZipFile(JAR) as zf:
        print(zf.read(entry).decode("utf-8", "replace"))


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--dump":
        dump(sys.argv[2])
    else:
        pats = sys.argv[1:]
        find(pats or ["class AddTableLootModifier"], context=0)
