# 第四轮：配置分离与时长自检

日期：2026-10-06。初始 Git 基线仍是 `0cd5406`；前四轮的改动都在工作区，未提交、未发布。

## 做了什么

- CLIENT 只留 `comfort.screenShakeIntensity` 和 `comfort.blinkDarkness`。
- SERVER 放 `world.meteorTerrainDamage` 和 `world.blackCloudTerrainDamage`。
  正常情况下默认 true；配置还没加载时，不允许破坏地形。
- 默认文件是 `config/mcanomalyarchives-server.toml`，世界里的 serverconfig 可以覆盖它。
- 本地首次升级时，先备份旧客户端文件，再创建服务端默认。专服、已有的默认文件和世界覆盖都不会被迁移改写。
- 命名牌时长自检改成直接调用 Java，顺手改了旧的公式注释，并给极端数值加了溢出保护。
- 更新了黑云的静态检查和规则生成器，新增配置、迁移、时长三类接线守卫。

## 自动验证

- 构建通过；共 107 个 Java 测试，本轮新增 28 个：
  6 个实际 spec 读取与卸载测试、10 个真实文件迁移测试、12 个生产时长方法测试。
- 旧的 `test_transform.py --offline` 确认会真的执行 NameTagTransformTest，
  不再是把 Java 公式抄一份到 Python 里。
- 109 项守卫通过；最后的全量 test/build 和 `git diff --check` 也通过。
- 小伪云、红伪云、黑吸伪云、名称解析、生物性质这 5 个离线检查通过，旧的时长入口也通过。

## 隔离专服和世界检查

`run/config-smoke` 里特意放了三套互相不同的文件：

- 全局默认：两个地形开关都是 false。
- 世界覆盖：陨石 true，黑云 false。
- 旧客户端：陨石 false，黑云 true。

在绝对路径的世界根下，专服启动成功，9 项世界内检查全部通过：

1. SERVER spec 确实加载了。
2. CLIENT spec 在专服上保持未加载。
3. 世界覆盖里陨石 true，优先于全局的 false。
4. 世界覆盖里黑云 false，读取正确。
5. 专服不会导入或改写旧客户端文件，也不会生成迁移备份。
6. 世界覆盖不会改写全局默认。
7. 服务器开关关闭时，真实的黑云只消散，不会删测试用的石头。
8. 服务器开关开着、但 mobGriefing 关闭时，仍然不删方块。
9. 两道开关都开着时，真实的黑云会吞掉测试石头并消散。

临时的位置、实体、设置和 gamerule，测试完都已还原；诊断 helper 只在聊天 work/ 里。
没有接受 EULA，开发模式自己跳过检查。用的是隔离的 worlds 根和本机临时端口，没碰正式存档。
专服用正常的 halt 停下并完成保存，测试 JVM 没有残留。
黑云的检查是直接调用真实的接触结算，跳过了出场阶段，所以这项不能算动画或自然生成已经实测。

## 遇到的问题，以及证据的边界

- 相对的世界路径在自动修正配置时第一次失败：NightConfig 的原子临时文件把目录拼了两遍。
- 接着试过把 universe 设成和游戏目录相同的绝对路径，被 Minecraft 拒绝了。
- 改用独立的绝对路径 `<隔离目录>/worlds` 后，配置加载和世界检查都通过了。
- 前两次失败的启动，即使 Gradle 显示 BUILD SUCCESSFUL，也不能算服务器启动成功；
  这轮是根据 Done 日志和真实运行检查，才确认最后一次成功。
- 第三方依赖没有动，失败记录也没有掩盖，部署注意事项见 ADR-0004。
- 单人 GUI 里的设置、真实客户端的配置同步，以及真人玩法，都还没验证。

## 复验

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/mcreator-guard/guard.ps1 -Action check
python -B tools/nametag/test_transform.py --offline
.\gradlew.bat test build --console=plain --offline
```

Python 入口会筛选测试，所以最后要再跑一遍全量 test/build，别把筛选后的报告当成全量结果。
