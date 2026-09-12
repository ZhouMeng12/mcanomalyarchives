---
name: video-content-study
description: "多模态研读B站视频(或外站)内容并提取设定：先抓文本(标题/简介/章节/评论)，再用视觉子代理读封面/截图，最后输出SCP风格设定卡并映射到MCreator模组元素建议。适用于研究MC诡异见闻录等灵感来源视频。"
whenToUse: "用户要求分析某个视频/网页/创作者系列的内容与设定，或提取可做进MCreator模组(尤其MC诡异见闻录同人)的元素时。典型触发词：看/分析这个视频、视频讲了什么、这期内容是什么、斯万/SvenTheOrangeBoy的新视频。"
metadata:
  version: "1.0"
  author: "workspace user"
---

# video-content-study

把一段视频（或一个 B站系列）研读成**结构化设定资料**，并映射为本工作区 MCreator 模组可用的元素建议。

## 关键前提（本环境实测，务必遵守）

1. **当前主模型（deepseek-v4-flash）不支持图像输入**。凡需"看图"（封面/截图/帧），必须启动**视觉子代理**：
   - provider: `deepseek-official`，model: `deepseek-v4-flash-vision-exp`
   - 给子代理**本地图片文件的绝对路径**（如 `D:\Desktop\MCreaterWorkspace\docs\video-research\covers\xxx.jpg`），让它在自己环境里读取；不要把图片内容贴进 prompt。
2. B站主站 API 与部分页面会 **412 风控**。可用通道（按优先级）：
   - `https://r.jina.ai/https://www.bilibili.com/video/<BV>/` — 页面全文（标题/简介/章节/选集/评论），**实测最可靠**
   - `https://www.snm0516.aisee.tv/video/<BV>/` — 镜像（可能要求验证码）
   - `https://www.bilibili.tv/en/video/<数字id>/` — 国际镜像（信息滞后，可佐证发布时间线）
   - B站 `api.bilibili.com/*` 直连大多 412，需 cookie + 签名，默认不做
3. 视频下载 / 抽帧 / 音轨转写属于**深度模式**（需 `yt-dlp`、`imageio-ffmpeg`、`faster-whisper` 且首次会消耗大量时间空间），**默认不执行**；除非用户明确要求，或文本+封面不足以判断内容。

## 流程

### L0 解析输入

- 识别链接形态：`BV1xxxx` 视频页、`space.bilibili.com/<uid>/video` 空间页、合集页 `channel/collectiondetail?sid=...`、外站（YouTube 等）。
- 若是 BV 视频：记录 BV 号、标题、投稿时间、播放/点赞/投币（来自 L1 页面）。
- 判断归属：是否《MC诡异见闻录》正传（合集 19 期）、SP/RE/RUINS 番外、外传（如 Fallacy™/谬误）、其他创作者。
- 输出：一个研究目标卡片（后续层都往这张卡片填充）。

### L1 文本层（主路径，先做）

1. `web_fetch` → `https://r.jina.ai/https://www.bilibili.com/video/<BV>/` 取页面全文：标题、播放/点赞、简介、章节、合集、评论。
2. **B站 view API（经 r.jina.ai 代理，实测可用）**：
   `https://r.jina.ai/https://api.bilibili.com/x/web-interface/view?bvid=<BV>`
   → 拿到 `desc` 全文、`cid`、投稿时间、**ugc_season 全合集列表**（含每期 pubdate/cid/BV）。若目标是"最新到哪期/系列脉络"，这是最快路径。
3. **弹幕全文（重大突破，实测可用，最高信息密度）**：
   - URL：`https://comment.bilibili.com/<cid>.xml`（或 `https://api.bilibili.com/x/v1/dm/list.so?oid=<cid>`）
   - ⚠️ 抓取必须保证 UTF-8：用 `Invoke-WebRequest` 后以 **Latin-1(28591) 字节回环**修复 mojibake（`$latin1.GetBytes($s)` → UTF8 decode），或直接用 Python `urllib` + `Accept-Encoding: identity` 并处理压缩。
   - 每条 `<d p="秒,模式,...">文本</d>` 含**精确时间戳**——把弹幕按播放器章节秒数切分阅读，即可重建剧情/机制（观众会实时复述台词与设定，比简介更详细）。
   - 存档为 `docs/video-research/raw/<slug>_danmaku.tsv`（列：秒\t文本）。
4. **章节精确秒表**：`view API` 的 `pages[].cid` + player 接口 `view_points`（经 `x/player/wbi/v2?bvid&cid=`，每章 `from/to` 秒 + 章节帧图 URL）。注意：r.jina.ai 对二进制（protobuf 弹幕 seg.so）会拒绝。
5. 若目标是"系列追踪"：view API 的 ugc_season 已含全部期数+日期，无需再抓空间页。

### L2 图像层（封面/截图）

1. 从 L1 页面提取封面 URL（`i0.hdslb.com/bfs/archive/...`，取 `.jpg` 不带尺寸后缀或 `@1280w` 均可）。
2. 下载到本地：`docs/video-research/covers/<slug>.jpg`（用 Invoke-WebRequest / curl，User-Agent 设为浏览器）。
3. 启动**视觉子代理**读该文件，prompt 固定要求描述：场景与构图 / 主体生物或物体特征 / 画面文字符号编号 / 色调氛围 / 恐怖元素 / 封面预告的核心信息。
4. 截图与弹幕画面等如用户提供，同样走视觉子代理。

### L3 字幕/语音（深度模式，**已实测可行**——要"真正看视频"走这条）

> ⚠️ 用户可能要求"重点看视频而不是弹幕"。此时开启深度模式，流程（UO-012 已验证全通）：
>
> **① 下载视频**：`python -m yt_dlp --no-playlist -f '<fmtID>+<audioID>' --add-header "Referer:https://www.bilibili.com" -o out.%(ext)s <URL>`。B站 720P 匿名可下（fmt 30064+30280 一类）；若 412 风控则等待数秒重试（list-formats 常能过，下载偶发 412，重试即可）；1080P 需大会员。
> **② 合并**：imageio-ffmpeg 便携版（`pip install imageio-ffmpeg`，`get_ffmpeg_exe()`）→ `-c copy` 合并 mp4。
> **③ 抽帧**：`ffmpeg -i full.mp4 -vf "fps=1/5,scale=640:-1" -q:v 4 f_%04d.jpg`（10 分钟 → 122 帧）。帧号 n 对应秒 ≈ (n-1)×5。
> **④ 并行视觉通读**：把帧按 40 帧/段切块，**每块一个视觉子代理**（deepseek-v4-flash-vision-exp，给本地帧绝对路径）要求"逐帧只描述实际所见：场景/角色/字幕文字/UI/动作"，可还原完整剧情与字幕。禁止脑补、看不清要明说。
> **⑤ 音频转写**：ffmpeg 提 16k 单声道 wav → `faster-whisper`（`pip install faster-whisper`，small 模型 int8 CPU，中文 `language='zh'`, vad_filter=True）→ 输出带时间戳台词，存档 `<slug>_transcript.txt`。
> **⑥ 成卡**：证据优先级 = **旁白台词 > 帧内字幕/UI > 画面内容 > 弹幕（仅辅助）**。

- 若用户只要"大致内容"：L1 文本+弹幕足够（快）；若要求"视频本体/准确剧情"：执行上述 ①-⑥。
- 保留旧路径：给 cookie 后可尝试 CC 字幕接口（wbi 签名）；但 whisper 转写已验证更通用，字幕接口多数情况可跳过。
- 执行前说明耗时/磁盘（视频约 60-100MB/10min、抽帧、whisper 模型 ~500MB 首次下载），征得同意后开始。

### L4 设定卡输出（本技能的主产物）

把研读结果按统一模板写成 markdown，存档于 `docs/video-research/<slug>.md`。**必须区分证据等级**：

- 【文本证据】= 官方简介原文 / 章节名 / 弹幕高密度一致复述（附秒数）
- 【观众推测】= 单条弹幕观点（写明互相矛盾的解读）
- 严禁把推测当设定写死；拿不准的进"备注/未解明"。

```markdown
# <异常名>（编号如 UO-012）

- 来源视频：<标题> | BV号 | 投稿日期 | 播放量/弹幕数
- 所属系列：MC诡异见闻录 正传/SP/外传
- 威胁等级：（低/中/高/无法分级，可注明观众提到的 R 级/等级）

## 核心机制
（基于文本证据，逐条分列；如"扭曲概率""观察者效应""意图判定"）

## 目击/剧情记录
（按章节时间轴简述发生了什么——来源：简介+章节+按秒切分的弹幕）

## 可做进模组的点
（1-3 条最有游戏性的机制构想，标注与现有系统的复用点/差异点）

## 备注/未解明
（弹幕存疑处、需看原视频确认处、L3 深度模式未开启说明）
```

### 参考速查（数据获取通道实测状态）

| 通道 | URL 形态 | 状态 |
|---|---|---|
| 视频页文本 | `r.jina.ai/https://www.bilibili.com/video/<BV>/` | ✅ 可用 |
| view API（desc/cid/合集/日期） | `r.jina.ai/https://api.bilibili.com/x/web-interface/view?bvid=<BV>` | ✅ 可用 |
| **弹幕全文 XML** | `https://comment.bilibili.com/<cid>.xml` | ✅ 可用（**最详细文本源**） |
| 章节秒表+帧 | `x/player/wbi/v2?bvid&cid` 的 view_points | ✅ 可用（经 jina） |
| CC/AI 字幕 | — | ❌ 需登录 cookie |
| 视频下载/抽帧/转写 | yt-dlp / ffmpeg / whisper | ⚠️ 深度模式，需安装+同意 |

### L5 映射 MCreator 元素

对照本工作区已有元素（用 glob 看 `elements/*.mod.json` 与 `src/main/java/net/mcreator/mcanomalyarchives/{entity,effects,events,block,item}`），给出建议：

- 新建元素类型（实体/效果/方块/物品/进度/规则）与 registry 名（`mcanomalyarchives` 前缀）
- 核心机制落到现有系统的哪一层：entity AI / effect handler / events listener / network packet / mixin
- 参考已有的近似实现（如虞美人注视→`PlayerLookAtCornPoppyListener`，怪树→`StrangeTree*Handler`）
- 工作量粗评（S/M/L）

## 研究对象

- B站 [SvenTheOrangeBoy](https://space.bilibili.com/1504970622)（斯万，《MC诡异见闻录》合集 19 期，播放 3400w+）。
- 已归档研究：见 `docs/video-research/`。
- 缓存目录：`docs/video-research/covers/`（封面）、`docs/video-research/raw/`（弹幕 TSV）——建议 gitignore 排除，避免仓库膨胀。

## 边界

- 仅供个人学习与二创灵感，不传播视频/图片本体；抓取内容只缓存本地。
- B站风控严格：控制请求频率；如需 cookie 走环境变量，勿写死在技能或脚本中。
- 深度模式（下载/转写）执行前必须征得用户同意。
- **准确性纪律**：产出设定卡必须区分"文本证据/观众推测"，宁可标注"未解明"也不脑补。
