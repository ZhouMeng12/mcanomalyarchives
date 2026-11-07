# UO-002 乱码物品贴图来源

这些参考 PNG 直接从本机 Minecraft 1.21.1 客户端 JAR 的 `assets/minecraft/textures/item` 提取。乱码图标由程序用 nearest-neighbor 拼合，没有使用生成图。

- 水瓶：将 `potion_overlay.png` 按水瓶蓝色着色，再叠加 `potion.png` 瓶体；它作为每个组合中随机反色的候选物。
- 其他候选物：原版苹果、骨头、末影珍珠、红石、羽毛、纸等纹理。
- `stick.png`、`firework_rocket.png`：与上述候选一样参与随机抽样。
- 游戏内每个新乱码物品栈随机选择 24 个预制组合之一；每组从候选中抽三种不同物品，并反转其中一件的 RGB 色彩。
