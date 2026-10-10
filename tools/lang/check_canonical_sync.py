# -*- coding: utf-8 -*-
"""检查守卫的插入式基线（canonical/*.extra.txt）与当前 lang/*.json 是否同步。

守卫的 `lang-*-extra` 是 `insertBefore` 规则：**只按键名比对**，缺哪条补哪条，
补进去的文案取自 canonical。所以：

- 键缺失 → 守卫报 DRIFT，`apply` 用 canonical 的文案补回来（哪怕 json 里本来是别的写法）；
- 只是**文案不一样** → 守卫不报，但一旦该键哪天被 MCreator 冲掉，
  `apply` 会把 canonical 里的旧文案塞回去，和 MCreator 工作区 language_map 打架。

跑一次这个脚本就能看出两边差在哪；确认当前 json/工作区才是想要的那版之后，
用 `guard.ps1 -Action snapshot` 把基线整体刷新（注意它只刷新检查通过的项）。
"""
import io, json, os, re

os.chdir(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

LINE = re.compile(r'^("(?:[^"\\]|\\.)*")\s*:\s*("(?:[^"\\]|\\.)*")$')


def parse(path):
    out = {}
    for line in io.open(path, encoding="utf-8"):
        line = line.strip()
        if not line.startswith('"'):
            continue
        line = line.rstrip(",")
        m = LINE.match(line)
        if m:
            out[json.loads(m.group(1))] = json.loads(m.group(2))
    return out


def main():
    drift_total = 0
    for lg in ("zh_cn", "en_us"):
        can = parse("tools/mcreator-guard/canonical/%s.extra.txt" % lg)
        cur = json.loads(io.open("src/main/resources/assets/mcanomalyarchives/lang/%s.json" % lg,
                                 encoding="utf-8").read())
        miss = [k for k in can if k not in cur]
        diff = [k for k in can if k in cur and can[k] != cur[k]]
        print("[%s] canonical %d 条；json 缺键 %d；文案不一致 %d" % (lg, len(can), len(miss), len(diff)))
        for k in miss:
            print("   缺键:", k)
        for k in diff:
            print("   文案不一致:", k)
            print("      canonical:", can[k])
            print("      json     :", cur[k])
        drift_total += len(miss) + len(diff)
    print("合计需要人工判断的条目:", drift_total)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
