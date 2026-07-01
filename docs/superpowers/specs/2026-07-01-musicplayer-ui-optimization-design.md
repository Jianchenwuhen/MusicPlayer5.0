# MusicPlayer5.0 UI 优化设计规格

- 日期：2026-07-01
- 分支：`feature/ui-optimization`（自 `test` 派生）
- 作者：团队 + Claude（结对）
- 状态：已通过设计评审，进入实现

## 1. 背景与目标

MusicPlayer5.0 是一款 Android 本地/在线音乐播放器（Java，团队课程项目）。底座已具备：氛围深色背景图、玻璃拟态面板、矢量播放/暂停图标、自定义波形视图、LRC 歌词逐行同步高亮。

本次目标：在**不改动依赖与架构**的前提下，尽可能提升 UI 观感与体验一致性，四个界面统一到一套深色玻璃风设计系统。

### 成功标准

1. **现代 UI 观感 / 设计规范感**：颜色、间距、字号、圆角全部走统一设计变量；四屏排版一致；触控有水波纹反馈。
2. **完整体验闭环**：在线搜索改为真实列表，补齐空状态 / 加载中 / 错误态；本地音乐、播放列表空状态接好。
3. **交互动效**：ripple 触控反馈、页面转场、播放/暂停图标切换、歌词平滑滚动居中。
4. **多屏适配 / edge-to-edge**：透明状态栏沉浸式、按系统栏高度留白、横屏与不同尺寸不错位。

## 2. 约束（硬约束）

- **不动依赖**：不改 `build.gradle`（保留 `appcompat-v7:24.2.1`）、不引入 AndroidX / Material Components / RecyclerView。所有效果用 appcompat + 平台原生 API（minSdk 21 起全部支持）实现。
- **不动架构与后端逻辑**：不改 `MusicService`、`PlaylistProvider`、数据库、广播协议。
- **团队安全**：改动集中在 `res/` 与少量 Activity 视图代码；与远端 `feature-login` 冲突面小；在 `feature/ui-optimization` 分支按里程碑提交；push 前先告知；**绝不删除任何文件**（旧 PNG 弃用但保留）。
- **构建基线**：项目当前可构建（今日已有 `app-debug.apk` 产物）。第 0 步先本地复现构建 + 采集"改造前"截图。

## 3. 设计系统（设计变量）

### 3.1 调色板（`res/values/colors.xml`）

删除未使用的 AS 默认靛蓝/粉，建立真实 token：

| Token | 值 | 用途 |
|---|---|---|
| `accent_primary` | `#38BDF8` | 主强调（天蓝，接现有 button_primary） |
| `accent_primary_pressed` | `#7DD3FC` | 主强调按下态 |
| `accent_primary_dim` | `#0EA5E9` | 主强调常态填充 |
| `accent_secondary` | `#F472B6` | 次强调（品粉） |
| `accent_secondary_dim` | `#EC4899` | 次强调深色 |
| `scrim` | `#99060A12` | 背景蒙层（统一现有 #77/#88） |
| `surface_glass` | `#990A1018` | 玻璃面板 |
| `surface_glass_soft` | `#660A1018` | 列表项/弱面板 |
| `stroke_hairline` | `#33FFFFFF` | 面板描边 |
| `stroke_soft` | `#22FFFFFF` | 弱描边/分隔 |
| `text_primary` | `#FFFFFFFF` | 主文字 |
| `text_secondary` | `#CCFFFFFF` | 次文字 |
| `text_tertiary` | `#99FFFFFF` | 三级文字 |
| `text_hint` | `#BBFFFFFF` | 提示文字 |

### 3.2 尺寸刻度（`res/values/dimens.xml`）

- 间距：`space_xs=4dp`、`space_sm=8dp`、`space_md=12dp`、`space_lg=16dp`、`space_xl=22dp`
- 屏边距统一：`screen_padding=20dp`（收敛现有 22/18）
- 面板内边距：`panel_padding=16dp`
- 圆角：`radius_sm=8dp`、`radius_md=12dp`、`radius_lg=16dp`
- 字号：`text_display=28sp`、`text_headline=24sp`、`text_title=22sp`、`text_body=15sp`、`text_caption=13sp`
- 控件：`control_primary=66dp`、`control_secondary=54dp`
- `values-w820dp/dimens.xml`（已存在桶）：宽屏/横屏放大屏边距与字号

### 3.3 组件样式（`res/values/styles.xml`）

- 主题：`AppTheme` → `Theme.AppCompat.NoActionBar`，设 `windowBackground`、`colorAccent=accent_primary`、透明状态栏/导航栏。
- 文字样式：`Text.Display`、`Text.Title`、`Text.Body`、`Text.Caption`、`Text.SectionHeader`。
- 按钮样式：`Widget.Button.Primary`、`Widget.Button.Secondary`、`Widget.Button.Pill`。
- 面板样式：`Panel.Glass`（background + padding）。

## 4. 主题与 edge-to-edge

- 换深色 `NoActionBar` 主题，删除各 Activity 内手动 `getSupportActionBar().hide()`。
- 去掉 `FLAG_FULLSCREEN`（旧式全屏，隐藏状态栏）。改为：`statusBarColor=transparent` + 内容延伸至系统栏后 + 顶部按状态栏高度留白（`SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN` + 内容容器 `fitsSystemWindows` 或手动 padding）。纯平台 API，不加依赖。
- 深色底 → 状态栏图标默认浅色，观感正确。

## 5. 四屏改造清单

### 5.1 主播放页 `activity_main.xml` / `MainActivity`
- 圆形播放控件加 **ripple** 反馈。
- 上一首/下一首 **改矢量**（`control_prev.xml` / `control_next.xml`，风格对齐 play/pause），弃用 `playnextbg`/`playpreviousbg` PNG。
- 进度条 thumb 换矢量 `seek_thumb.xml`（弃用 `currentprogress3.png`），进度条配色接 `accent_primary`。
- **新增「当前时间 / 总时长」标签**（`00:00 / 03:21`），随 seekBar 更新。
- 歌词高亮换 `accent_primary`，当前行居中滚动（已有 smoothScroll，微调）。
- 头部标题与导航按钮统一样式（pill）。

### 5.2 在线音乐页 `online_music.xml` / `OnlineMusicActivity`（最大体验提升）
- 把「一个大 `TextView` 塞结果」**改为 `ListView` + 适配器**（复用/新增在线列表项）。
- 补齐三态：**加载中**（`ProgressBar`）、**空/初始提示**、**错误态**（无网络 / 无结果）。
- 搜索框与「搜索 / 随机推荐」按钮走统一样式。
- 点击列表项加入播放/播放列表（沿用现有逻辑接口，不改后端）。

### 5.3 本地音乐 `localmusic.xml` / 播放列表 `playlist.xml`
- 头部统一收进玻璃面板，屏边距/蒙层统一。
- 列表项统一样式；接好**空状态**（已有 `emptyLocalMusic` / `emptyPlaylist`，确认切换逻辑生效）。

### 5.4 列表项 `musicitem.xml`
- 删除未使用的 `imageView`（0dp）。
- 加**专辑占位图**：圆角小图 tile + 矢量音符图标。
- 修正歌名/歌手文字基线与省略。

## 6. 动效

- 全触控元素 ripple（`<ripple>` API 21+ 原生）。
- 保留并微调页面转场（`animator/lefttoleft`、`righttoleft`）。
- 播放/暂停图标切换加淡入淡出（可选增强）。
- 歌词平滑滚动居中；波形动效沿用。

## 7. 资源与图标统一

- 新增矢量：`control_prev`、`control_next`、`seek_thumb`、空状态插画、导航小图标。
- 旧 PNG 控件图**弃用但保留**（不删文件）。

## 8. 多屏适配

- `values-w820dp/dimens.xml` 补屏边距/字号桶。
- edge-to-edge inset 处理。
- 横屏与不同尺寸走查：主播放页（加权歌词列表）、列表页不错位。

## 9. 实施顺序（分阶段，每阶段可独立验证）

0. **验证构建 + 采集改造前基线截图**（四屏）。
1. **设计变量层**：colors / dimens / styles / 主题（不改布局引用前先建 token）。
2. **主题 & edge-to-edge**：换主题、去 FLAG_FULLSCREEN、状态栏沉浸。
3. **图标矢量化**：prev/next/seek thumb + ripple 封装。
4. **主播放页**改造（含时间标签）。
5. **在线音乐页**改列表 + 三态。
6. **本地/播放列表/列表项**统一。
7. **多屏适配 + 动效收尾**。
8. **回归**：四屏"改造后"截图对比，构建通过。

## 10. 风险与回退

- 旧 support 库 + 新 AGP 组合脆弱：以第 0 步实际构建结果为准；若某平台编不过，先同步再决策。
- 每阶段独立提交，便于回退；不删任何文件，PNG 保留可随时回滚。
