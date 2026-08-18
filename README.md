# 🫘 拼豆工坊 BeadCraft

一个 MIUI X 风格的安卓拼豆图纸制作 App —— 拍照/选图，一键生成带色号的拼豆图纸和用料清单。

> 算法与品牌色卡学习自 [Beanify](https://github.com/zhoucanzong/Beanify) 项目，感谢其公开的 CIEDE2000 色彩匹配管线与六品牌色号数据库整理。

## ✨ 功能特性

- 🖼️ **图片转拼豆图纸** — 选择 JPG/PNG/WebP 图片，自动量化为网格图纸
- 🎨 **多品牌色号支持** — MARD 麦德 / Artkal / Perler / Hama / Yant / Nabbi 六大品牌官方色卡，共 ~800 色
- 🔬 **CIEDE2000 色差匹配** — 感知均匀色彩空间匹配，肤色与暗部过渡自然，色号对得上真实豆子
- 🖼️ **全图制作** — 整个画面铺满豆板，适合风景/图案类
- ✂️ **提取主体制作** — 从四边 flood-fill 自动剥离背景，只拼主体，省豆子
- 🧹 **智能去噪** — 小连通域清理，去除杂色碎点
- 📊 **用料清单** — 每个色号需要多少颗，按用量排序，直接照着采购
- 🔍 **可缩放图纸预览** — 双指缩放/拖动，放大可看每格色号，每 10 格加深参考线对齐拼豆板
- 📄 **导出 PNG / 分享** — 带图例（色号+数量）的高清图纸一键保存到相册或分享
- 🌙 **MIUI X 设计语言** — 大圆角卡片、渐变主色、呼吸按压动效、深色模式
- 🔒 **纯本地处理** — 图片不上传，无需网络，无需任何权限

## 📱 安装

从 GitHub Actions 构建产物下载 APK：

1. 进入仓库 **Actions** 页面
2. 选择最新一次 **Build Android APK** 运行
3. 在 Artifacts 中下载 `BeadCraft-APK`
4. 安装 `app-release.apk`（debug 签名，可直接安装）

## 🛠️ 技术栈

| 技术 | 说明 |
|------|------|
| Kotlin | 100% Kotlin |
| Jetpack Compose + Material 3 | MIUI X 风格 UI |
| MVVM + ViewModel + StateFlow | 架构 |
| CIEDE2000 | 颜色匹配算法 |
| GitHub Actions | CI 编译出包 |

## 🧩 图纸生成管线

```
原图
  ├─ 提取主体模式：边缘主色投票 → flood-fill 背景剥离 → 主体包围盒裁剪
  └─ 全图模式：直接使用
→ 盒式降采样到网格（每格平均色）
→ CIEDE2000 最近色量化（品牌色卡）
→ 最大颜色数限制（两遍量化，保留高频色）
→ 小连通域去噪（≤2 格碎块并入邻色）
→ 用料统计 + 图纸渲染
```

## 📁 项目结构

```
BeadCraft/
├── .github/workflows/        # CI 编译工作流
├── app/src/main/
│   ├── assets/beads/         # 六品牌色号 CSV 数据库
│   ├── java/com/beadcraft/pattern/
│   │   ├── engine/           # 核心引擎
│   │   │   ├── ColorSpace.kt      # RGB→Lab / CIEDE2000
│   │   │   ├── BeadDatabase.kt    # 品牌色卡
│   │   │   ├── BeadProcessor.kt   # 图纸生成管线
│   │   │   └── PatternRenderer.kt # 图纸位图渲染/导出
│   │   ├── ui/               # MIUI X 风格 Compose UI
│   │   ├── MainActivity.kt
│   │   └── MainViewModel.kt
│   └── res/                  # 主题 / 图标 / 字符串
├── build.gradle.kts
└── settings.gradle.kts
```

## 📄 License

MIT
