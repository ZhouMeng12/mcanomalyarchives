# 第三轮：怪树状态与恢复

日期：2026-10-06。基线仍是 `0cd5406`，第一、二轮的改动保留，未提交、未发布。

## 做了什么

- 原来三个各管一摊的处理器，换成一个 `anomaly/strangetree` 模块。
- 阶段、目标、原基座、树身快照、迁居和攻击时序，统一由它管。
- journal 先于清树写入 NeoForgeData，重载时取消袭击，把树恢复到原位。
- 迁居失败会回滚；平移用实体脚底做锚点，自定义树冠不再干扰地面查询。
- 补齐了生命周期清理，玩家计数按维度隔离；补种会取消整条追杀链；已经计过的树干不再重复遍历连通簇。
- 不认识的记录隔离并保留；旧版本里已经丢失的快照，不会凭空造一棵树。

## 自动验证

- `gradlew.bat test build --console=plain --offline` 通过。
- 79 个 Java 测试，0 失败。本轮新增 37 个：怪树状态 22、真实 NBT 8、玩家进度 7。
- 101 项守卫通过。
- 日常的 Gradle 配置缓存仍可复用，`git diff --check` 通过。
- jar 里已经没有旧的三个 Handler。

## 隔离世界验证

用 `architecture/dedicated-smoke.init.gradle` 加 `-PsmokeGameDir=run/tree-smoke`，
启动了一个独立的开发专服，只绑定 127.0.0.1 和临时端口。EULA 文件保持 false，
开发模式自己跳过这项检查，没有碰正式存档。

验证用一个临时的 JDK 诊断 helper，只在这个目录、没有在线玩家时，
从服务器线程在已加载区块里造一棵最小的树，依次检查：

1. 地面查询会跳过本模组的树冠。
2. 清树前抓到了两块完整快照。
3. 真实的 `Entity.saveWithoutId` 写出的 NeoForgeData 里带有快照。
4. 用真实的 `Entity.load` 读回后，树身和脚底都能还原。
5. 恢复成功后，快照被消费掉。
6. 之后放上去的石头没有被覆盖。
7. 来自未来版本的 journal 被保留，地形也没被改。

7 项全部通过。临时演员都已删掉，测试位置也还原了；开发专服正常停服并完成保存。
诊断 helper 只在聊天 `work/` 里，不进模组 jar；`run/` 也不进 Git。

## 复验

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/mcreator-guard/guard.ps1 -Action check
.\gradlew.bat test build --console=plain --offline
```

## 没做完的

- 真人完整走一遍伐木、补种、钻地、追杀；客户端动画和发光；真实的区块卸载；多人交互。
- 旧版本里已经丢失的恢复方块数据，没法从姿态字段推回来，只能靠旧备份或人工修复。
- 配置拆分、命名牌旧的 Python 时长检查、窥视者的生命周期，留到后面。
