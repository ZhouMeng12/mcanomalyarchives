# -*- coding: utf-8 -*-
"""兼容旧自检入口：调用真实 Java 时长测试，不在 Python 复刻公式。

用法：python tools/nametag/test_transform.py [--offline]
仍保留蛋物种键与寿命调用的静态接线检查；它们不是游戏内行为验证。
原掉落/夺取的 Python 模拟已移除，不能再声称其通过验证了生产 Java。
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def wiring_checks():
    base = ROOT / "src/main/java/net/mcreator/mcanomalyarchives/anomaly/nametag"
    ticker = (base / "NameTagTicker.java").read_text(encoding="utf-8")
    handler = (base / "NameTagHandler.java").read_text(encoding="utf-8")
    notifier = (base / "NameTagNotifier.java").read_text(encoding="utf-8")
    costs = (base / "NameTagCosts.java").read_text(encoding="utf-8")
    lifespan = re.search(r"NAMED_ENTITY_LIFESPAN_TICKS\s*=\s*(\d+)", costs)
    checks = (
        ("蛋物种键已定义", "EGG_SPECIES_KEY = " in notifier),
        ("下蛋使用共享物种键", "putString(NameTagNotifier.EGG_SPECIES_KEY" in ticker),
        ("砸蛋读取共享物种键", "contains(NameTagNotifier.EGG_SPECIES_KEY)" in handler
         and "getString(NameTagNotifier.EGG_SPECIES_KEY)" in handler),
        ("60秒寿命与死亡逻辑接线", lifespan is not None and int(lifespan.group(1)) == 1200
         and "NAMED_ENTITY_LIFESPAN_TICKS" in ticker and "dieOfExhaustion" in ticker),
    )
    for label, passed in checks:
        print("  [%s] %s" % ("OK" if passed else "FAIL", label), flush=True)
    return all(passed for _, passed in checks)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="仅使用已有 Gradle 依赖缓存")
    args = parser.parse_args()
    print("执行真实 Java NameTagTransformTest（固定期望：15 秒～2 分钟）", flush=True)
    prefix = ["cmd", "/d", "/c", "gradlew.bat"] if os.name == "nt" else ["./gradlew"]
    command = prefix + ["test", "--rerun", "--tests",
                        "net.mcreator.mcanomalyarchives.anomaly.nametag.NameTagTransformTest",
                        "--console=plain"]
    if args.offline:
        command.append("--offline")
    result = subprocess.run(command, cwd=ROOT, check=False)
    if result.returncode:
        return result.returncode
    if not wiring_checks():
        return 1
    print("真实 Java 时长测试与静态接线检查通过；未执行游戏内行为测试。", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
