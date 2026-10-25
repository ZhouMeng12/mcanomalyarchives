# 本地异常标本展台

启动（项目根目录）：

```powershell
python previews/anomaly-lab/generate_assets.py
python previews/anomaly-lab/build_design.py
python -m http.server 8765 --bind 127.0.0.1 --directory previews/anomaly-lab
```

访问 http://127.0.0.1:8765 。仅监听本机，不依赖 CDN、联网库或外部字体。

六组钓获模型、羊与木棍两组命名试体；可旋转、缩放、显示线框；两个可播放/暂停/拖动的命名时序。每个模型提供嵌入图集的 Blockbench free 源文件，八张专用 UV PNG，其中蒲公英牛使用原版花朵贴图，鱿鱼仅保留躯干和八条腿 与 JSON 共享给网页使用；原版参考从本机 1.21.1 客户端资源提取，派生的牛、羊、鱿鱼、末影人材质已重新配色、分面并添加异常细节。

这是概念资产与过程演示。未注册 Minecraft 实体、未导出 Java 骨骼、未接入服务端吸收或爆炸。详细方案见 architecture/ROUND-7-IMPLEMENTATION-PLAN.md。

验证：8 套模型、77 个方块部件，已检查 UV 边界、PNG 完整性、Blockbench UUID/层级和 16 个 HTTP 模型/材质端点；浏览器六档切换、命名分支与进度拖动已检查。Blockbench 实际导入和 Minecraft 游戏内渲染尚未验证。
