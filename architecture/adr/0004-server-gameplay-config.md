# 0004：客户端视觉偏好与服务端世界规则分开

日期：2026-10-06。状态：已实施；验证见 `../ROUND-4.md`。

## 决定

- `ComfortConfig` 只定义 CLIENT 的视觉倍率，专用服务器不会注册或加载这份客户端配置。
- `ServerGameplayConfig` 定义 SERVER 的陨石与黑吸伪云方块破坏开关，由服务器或单人世界决定，
  客户端只接收同步。
- 两个注册入口都在构造期无参调用。先处理旧设置迁移，再注册客户端 spec，
  免得客户端 schema 的自动修正先把旧的地形字段清掉。
- 加载之后的默认值仍是 true；加载之前读取返回 false，这样生命周期的空窗期不会误破坏地形。
- 黑吸伪云继续和 `mobGriefing` 取与。关掉地形开关，不会取消它的消散、伤害和演出。
- 命名牌自己的爆炸开关没动。原注释说它“与陨石共用舒适度开关”，和真实代码不符，已经改正。

## NeoForge 21.1.190 的实际路径

检查了安装的源码里的 `ServerLifecycleHooks` 和 `ConfigTracker`，结论是：

1. `config/mcanomalyarchives-server.toml` 是全局的服务端默认。
2. 当前世界的 `<世界>/serverconfig/mcanomalyarchives-server.toml` 如果存在，会覆盖全局默认。
3. 服务器规则按 SERVER 类型同步；客户端改自己的 CLIENT 文件，影响不了远程服务器。

别照旧的说明，以为 SERVER 配置会自动给每个世界生成独立文件。需要按世界设置时，
得在那个世界的 serverconfig 里放覆盖文件。

## 旧设置迁移

实际的文件策略在 `LegacyGameplayConfigMigration` 里，并在临时目录里测试过：

- 只在本地客户端升级时执行；专服直接跳过，不会读取客户端文件。
- 新的服务端默认已经存在时，完全不覆盖，也不会再套用旧偏好；世界目录既不扫描也不写入。
- 旧客户端文件里有地形字段时，先存为 `mcanomalyarchives-client.toml.pre-server-split.bak`
  （已有备份就不覆盖）。
- 用 CREATE_NEW 创建新的服务端默认文件，迁移代码不会改写源客户端文件。
  之后 NeoForge 可能按新的 CLIENT schema 把旧字段去掉，所以备份要赶在这之前生成。
- 缺的旧字段按原来的默认 true 处理；不合法的值或 TOML，备份之后报告失败，不会悄悄转换字符串。
- 失败时不创建迁移目标。随后框架可能生成一份正常的 true 默认，日志会明确提示管理员去检查，
  但不会断言无效的旧值一定等价于关闭破坏。

这个默认值可能影响所有没有世界覆盖文件的单人世界，范围和以前客户端的全局偏好一样；
它不只影响“新建的世界”，也不是逐个世界自动修改。

## 时长测试

`NameTagTransformTest` 直接运行生产用的 Java 方法，固定检查 300/2400/100/1 这几个期望值，
覆盖 Unicode、缺省名字、上下限、进度、阶段和极端值。时长常量和正常结果都没改，
只是质料减法和乘法改用 long 做中间值避免溢出，极端的游戏时间差改用 double 计算，避免 long 溢出。

`tools/nametag/test_transform.py` 不再复制公式，改成调用 JUnit，并保留蛋和寿命接线的静态检查。
旧的掉落和夺取的 Python 模拟已经删掉，也没有因此补上 Java 掉落的覆盖。
`calc_transform_time.py` 仍然只是读取源码常量的估算工具，不能当回归测试。

## 部署注意

在隔离专服里第一次使用相对的世界覆盖路径时，NightConfig 3.8.2 自动修正注释，
会触发 `REPLACE_ATOMIC` 重复拼接目录（日志里出现 `world/serverconfig/world/serverconfig/...tmp...`）。
这是当前框架的文件写入路径问题，依赖本身没有改。

复验时用的是独立的绝对世界根 `--universe <绝对路径>/worlds`。
不要把 universe 设成当前游戏目录本身，Minecraft 会拒绝。这样配置能正常修正和加载，
世界覆盖和真实黑云的效果也都通过了。
生产环境要用世界覆盖文件的话，得核对启动目录和这个限制，不能只看 Gradle 返回了成功。
