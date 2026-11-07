# MCreator 离线同步与重生成兼容

本机 MCreator 使用 `C:/Users/Administrator/.mcreator/gradle`，命令行默认使用 `.gradle`。清空前者后，即使命令行可以构建，MCreator 的离线同步仍可能缺少插件或运行依赖。

## 本次恢复

- 使用 `tools/repair-mcreator-offline-cache.ps1` 从已可用的命令行缓存补齐 MCreator 的依赖与 NeoForm 缓存。保留旧仓库元数据备份，不复制锁文件。
- `tools/offline-maven` 保存固定版本 NeoForge 的启动配置。JSON 与已缓存的官方 userdev JAR 内 `config.json` 逐字节一致；本地模块描述仅提供 `neoforge-moddev-config` capability，并限制在 `neoForgeConfigOnly` 配置使用。
- 六个新生物补齐 MCreator 所调用的 `init` 接口，以及与生成注册表同名的独立渲染器。渲染器登记到对应锁定元素的文件清单，共享实现不再属于单个元素。
- 乱码物品使用生成注册表要求的无参构造器。
- `settings.gradle` 在 ModDev 插件应用时加载 `architecture/mcreator-workspace.gradle`。不要把工作区自定义内容放进 MCreator 每次运行都会重建的 `mcreator.gradle`。
- 编译前修复任务从 `architecture/mcreator-imports.json` 恢复 MCreator 重生成时丢失的导入，并恢复服务端任务委托、动画实体类型检查；同时移除过时的 `NeoForge.EVENT_BUS.register(this)`，避免无订阅方法的主类触发加载异常。任务通过 Gradle 属性接收工作区路径，兼容 MCreator 默认开启的 configuration cache。

## 再次清空缓存后

关闭运行中的构建，然后从工作区执行：

```powershell
.\tools\repair-mcreator-offline-cache.ps1
```

该工具要求命令行的 `.gradle` 缓存仍然可用。不要同时清空两个缓存目录。

## 验证

使用 MCreator 自带 JDK 21.0.10 和其专用 Gradle 缓存离线执行验证：`compileJava` 成功。首次客户端加载到进度/成就阶段后正常运行；之后为验证 MCreator 默认的 configuration cache 设置，完整执行 `runClient --offline` 并成功结束，缓存配置也成功保存。MCreator 桌面窗口的重新运行验证曾因用户按 Escape 中止。
