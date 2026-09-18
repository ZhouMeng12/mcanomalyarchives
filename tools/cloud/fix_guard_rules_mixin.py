# -*- coding: utf-8 -*-
"""把"门吐云"的守卫规则从"门户子类"改成"mixin"。

原来的做法（注册 SmallCloudEmittingPortalBlock 子类 + 改写生成区注册行）被 MCreator
重新生成时静默打回，所以换了 mixin，规则也要跟着换。
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)
P = "tools/mcreator-guard/manifest.json"

m = json.load(io.open(P, encoding="utf-8"))
drop = {"small-cloud-portal-registration", "small-cloud-portal-random-tick"}
before = len(m["items"])
m["items"] = [i for i in m["items"] if i["id"] not in drop]

m["items"].append({
    "id": "small-cloud-portal-mixin",
    "kind": "contains",
    "why": "让门吐云的钩子在 mixin 里（手写区，MCreator 不碰）。这条是踩过坑才有的：原本写了个门户子类 "
           "SmallCloudEmittingPortalBlock 并改写生成区的注册行，2026-09-18 作者在 MCreator 里跑了一次客户端，"
           "注册行被重写回 CloudDemPortalBlock::new —— 不报错、不崩、只是门从此不再吐云。",
    "path": "src/main/java/net/mcreator/mcanomalyarchives/mixin/CloudPortalEmitMixin.java",
    "fixHint": "从 git 恢复 mixin/CloudPortalEmitMixin.java（并确认 mixins.json 里登记了它）",
    "mustContain": [
        "@Mixin(CloudDemPortalBlock.class)",
        "SmallCloudEmitter.tryEmit(level, pos, state, random);",
        "randomTick(Lnet/minecraft/world/level/block/state/BlockState;",
    ],
})
m["items"].append({
    "id": "small-cloud-portal-mixin-registered",
    "kind": "contains",
    "why": "mixin 配置里必须登记 CloudPortalEmitMixin：少了它 mixin 根本不加载，门永远不吐云（同样不报错）",
    "path": "src/main/resources/mcanomalyarchives.mixins.json",
    "fixHint": '把 "CloudPortalEmitMixin" 加回 mixins 数组',
    "mustContain": ['"CloudPortalEmitMixin"'],
})

with io.open(P, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(m, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("规则 %d → %d" % (before, len(m["items"])))
