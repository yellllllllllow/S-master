# S master 项目完整汇总

> 生成日期：2026-05-27
> 用途：迁移到新 PC 时的参考文档

---

## 一、项目概览

本项目包含 **两个独立子项目**：

| 子项目 | 路径 | 说明 |
|--------|------|------|
| **s_master** | `s_master/` | Android 恋爱聊天顾问 App（主项目） |
| **s_master-skill-main** | `s_master-skill-main/` | Trae AI Skill — 恋爱聊天策略库（辅助），非构建必需 |

---

## 二、s_master-android（Android App）

### 2.1 项目信息

| 项 | 值 |
|---|-----|
| 包名 | `com.example.s_master` |
| App 名 | S master |
| minSdk | 26 (Android 8.0) |
| targetSdk | 34 (Android 14) |
| compileSdk | 34 |
| 版本 | 2.0 (versionCode 2) |
| Gradle | 8.5 |
| AGP | 8.2.2 |
| JDK | 11 |
| 构建方式 | `gradlew.bat assembleDebug` / `assembleRelease` |

### 2.2 项目结构

```
s_master-android/
├── app/
│   ├── build.gradle              # 模块构建配置（含 Release 签名）
│   ├── proguard-rules.pro        # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml   # 权限 + Service/Receiver 声明
│       ├── java/com/example/s_master/
│       │   ├── MainActivity.java          # 主界面 + 设置面板
│       │   ├── AIService.java             # AI 服务（API调用/模型管理/Prompt）
│       │   ├── ChatMonitorService.java    # 前台服务（MediaProjection截图+通知栏控制）
│       │   ├── FloatingService.java       # 前台服务（结果弹窗覆盖层）
│       │   └── BootReceiver.java          # 开机自启广播接收器
│       └── res/
│           ├── layout/                    # 5 个布局文件
│           ├── drawable/                  # 12 个 drawable 资源
│           ├── values/                    # colors/strings/styles
│           └── mipmap/                    # 应用图标
├── .github/workflows/android.yml   # GitHub Actions 自动构建
├── build.gradle                    # 根项目构建配置
├── settings.gradle                 # 项目设置
├── gradle.properties               # Gradle 属性
├── gradlew / gradlew.bat           # Gradle Wrapper
├── gradle/wrapper/                 # Gradle Wrapper JAR + 配置
├── .gitignore
├── deploy.ps1 / deploy.sh          # 部署脚本
├── build-apk.ps1 / build-apk-final.ps1  # 构建脚本
├── QUICK_START.md / README.md      # 说明文档
└── GITHUB_DEPLOY.md                # GitHub 部署说明
```

### 2.3 核心功能清单

#### 2.3.1 截图 + AI 分析（主功能）
- 通过 `MediaProjection` API 截取屏幕内容
- 将截图发送到 AI（视觉模型，如 GPT-4o、DeepSeek-VL 等）
- AI 分析聊天内容，输出分析 + 3 种风格回复（温柔/幽默/直球）
- 结果以覆盖层弹窗展示，每条可独立复制

#### 2.3.2 通知栏控制
- 持久通知「S master」常驻通知栏
- 通知栏内嵌「📷 开始分析」操作按钮
- 下拉通知栏 → 点击按钮 → 自动截图分析
- 分析中通知文本实时更新状态

#### 2.3.3 结果弹窗
- 浮动覆盖层（`TYPE_APPLICATION_OVERLAY`）
- 显示聊天分析 + 3 个回复选项卡片
- 每个卡片有独立「复制」按钮
- 可拖动位置，30 秒自动关闭
- 支持从右边缘吸附显示

#### 2.3.4 设置面板（底部弹出 BottomSheet）
- 4 种 AI 服务商选择：**DeepSeek / 硅基流动 / OpenAI / 自定义**
- API Key 输入（密码模式）
- 自定义 API 地址（自定义模式可用）
- 「获取模型列表」按钮 — 自动拉取并分类
- 图形模型（视觉）下拉选择 + 测试按钮
- 推理模型（文本）下拉选择 + 测试按钮
- 两种识别模式：**手动截图** / **实时识别**
- 自定义 Prompt 编辑（图形/推理模型各一个，可折叠，可恢复默认）

#### 2.3.5 开机自启
- `BootReceiver` 监听 `BOOT_COMPLETED`
- 读取 `was_running` 状态判断是否需要自动恢复
- 自动启动 MainActivity → 自动恢复 Service

#### 2.3.6 AI 模型管理
- 模型列表自动分类（排除文生图模型如 stable-diffusion/sdxl/flux 等）
- 视觉模型识别（vision/vl/claude-3/qwen-vl 等关键词）
- 模型可用性测试
- 已选模型持久化保存

### 2.4 权限清单

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗覆盖层（结果弹窗） |
| `FOREGROUND_SERVICE` | 前台服务基础权限 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 屏幕录制前台服务 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 弹窗显示前台服务 |
| `INTERNET` | AI API 网络请求 |
| `POST_NOTIFICATIONS` | Android 13+ 通知权限 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `ACCESS_NOTIFICATION_POLICY` | 通知策略（预留） |

### 2.5 服务架构

```
MainActivity
  ├── startService → ChatMonitorService (foregroundServiceType=mediaProjection)
  │     ├── MediaProjection → VirtualDisplay → ImageReader → 截图
  │     ├── 通知栏「📷 开始分析」按钮 → CAPTURE_NOW 广播
  │     ├── AI 分析（AIService.analyzeScreenshot）
  │     └── SUGGESTION 广播 → FloatingService
  │
  └── startService → FloatingService (foregroundServiceType=dataSync)
        ├── 接收 SUGGESTION 广播
        └── showResultPopup() → 覆盖层弹窗

BootReceiver
  └── BOOT_COMPLETED → start MainActivity
```

### 2.6 关键类说明

#### AIService.java
- 4 个内置服务商（DeepSeek/硅基流动/OpenAI/自定义）
- 通过 `AsyncTask` 发起网络请求（OpenAI 兼容 API）
- `fetchModels()` — 获取模型列表并分类
- `analyzeScreenshot()` — 视觉模型分析截图（Base64 编码）
- `analyzeText()` — 文本模型分析聊天内容
- `testModel()` — 测试模型可用性
- `parseResponse()` — 解析「【分析】+【建议一/二/三】」格式
- `getBuiltInSuggestion()` — 无 API Key 时的本地规则回复
- 所有配置通过 `SharedPreferences` 持久化

#### ChatMonitorService.java
- 前台 Service，`foregroundServiceType="mediaProjection"`
- 创建 `VirtualDisplay` 用于屏幕截图
- 通知栏：持久通知 + 「📷 开始分析」action 按钮
- 点击按钮 → `CAPTURE_NOW` 广播 → `takeScreenshot()`
- 分析状态实时更新通知文本
- 手动模式 / 实时循环截图模式

#### FloatingService.java
- 前台 Service，`foregroundServiceType="dataSync"`
- 接收 `SUGGESTION` 广播 → 显示结果弹窗覆盖层
- 弹窗：分析区 + 多选项卡片（各带复制按钮）
- 可拖动，30 秒自动隐藏

#### MainActivity.java
- App 入口，检查通知权限
- 启动 → 请求屏幕录制权限 → 启动双 Service
- 设置面板（BottomSheetDialog）：服务商/Key/模型/Prompt/模式

### 2.7 构建与部署

#### 本地构建
```bash
cd s_master-android
./gradlew.bat assembleDebug    # Debug APK
./gradlew.bat assembleRelease   # Release APK（需 keystore）
```

#### Release 签名
- **keystore**: `s_master.keystore`（已加入 .gitignore）
- **alias**: `s_master`
- **密码**: `s_master123`
- Release APK 输出：`app/build/outputs/apk/release/`

#### GitHub Actions CI
- `.github/workflows/android.yml`
- push 到 main/master 自动构建 Debug APK
- 产物上传到 Actions Artifact，保留 30 天

#### GitHub 仓库
- 远程：`https://github.com/yellllllllllow/S-master.git`
- 分支：main

### 2.8 已排除的文件（.gitignore）
- `*.iml` / `.gradle` / `/build` / `**/build/`
- `/local.properties` / `/.idea` / `.DS_Store`
- `*.apk` / `*.aab` / `*.keystore` / `*.jks`
- `*.log` / `*.hprof` / `*.json`
- `bin/` / `gen/` / `out/` / `.navigation/`

### 2.9 开发环境要求
- Android Studio Hedgehog 或更高
- JDK 11+
- Android SDK 34
- Gradle 8.5 (Wrapper 自带)

---

## 三、s_master-skill-main（AI Skill 库）

### 3.1 说明
这是一个独立的 **Trae Skill / Claude Code Skill**，包含「情圣」恋爱聊天策略的知识库。提供给 AI 加载后可以分析聊天记录、生成回复建议。

**不是 App 构建必需**，但是 App 中 AIService 的 `SYSTEM_PROMPT_VISION` 和 `SYSTEM_PROMPT_TEXT` 的灵感来源。

### 3.2 结构
```
s_master-skill-main/
├── skill/
│   ├── SKILL.md               # 主 Skill 定义（核心策略）
│   ├── references/            # 10 个知识库文档
│   │   ├── stages.md          # 七阶段推进系统
│   │   ├── signals-tools.md   # 方法论工具箱
│   │   ├── advanced-techniques.md
│   │   ├── autopilot-guide.md
│   │   ├── examples-library.md
│   │   ├── mindset-concepts.md
│   │   ├── platform-guide.md
│   │   ├── profile-audit.md
│   │   ├── recovery-playbook.md
│   │   └── user-context.md
│   ├── s_master-upgrade.md
│   ├── 展示面.md / 急.md / 挽回.md / 换一个.md / 自动.md
├── evals/                     # 评测数据
├── demos/                     # 演示素材
├── docs/                      # 文档站点
├── mobile-app/                # PWA 移动端
├── build-skill.sh             # 构建脚本
└── VERSION                    # 版本号
```

### 3.3 使用方式
```bash
# 在 Trae/Claude Code/Cursor 中加载
# 将 s_master-skill.skill 或整个 skill/ 目录放入 AI 的 skills 目录
```

---

## 四、Trae 对话记录摘要

### 4.1 开发关键词
| 阶段 | 内容 |
|------|------|
| **启动** | 创建 Android 悬浮窗 App，实现截图 + AI 分析聊天内容 |
| **交互改版** | 大悬浮窗 → 侧边 Dock 键 → 通知栏控制按钮 |
| **多选项回复** | AI 输出 3 种风格（温柔/幽默/直球），弹窗展示 + 独立复制 |
| **Android 16 兼容** | startForeground() + foregroundServiceType 修复 |
| **模型分类** | 排除文生图模型，精确识别视觉模型 |
| **自定义 Prompt** | 可编辑图形/推理模型的 system prompt |
| **一键复制** | 长按 Dock 复制（已废弃）/ 卡片按钮复制 |
| **模型测试** | 设置面板中测试按钮验证模型可用性 |
| **开机自启** | BootReceiver + was_running 状态恢复 |
| **通知栏控制** | 持久通知 +「开始分析」操作按钮（最新改动） |

### 4.2 关键决策点
1. **交互方式演变**: 大悬浮窗 → Dock 侧边键（不显示）→ **通知栏控制（当前）**
2. **AI 模型选择**: 视觉模型（截图识别）+ 推理模型（文本分析）双模型配置
3. **服务商兼容**: 支持 DeepSeek/硅基流动/OpenAI/自定义，统一 OpenAI 兼容 API
4. **结果展示**: 覆盖层弹窗（TYPE_APPLICATION_OVERLAY），可拖动可复制

### 4.3 已知问题 / 可改进点
1. 覆盖层弹窗在某些 ROM 上可能和 Dock 一样不显示（建议也做通知栏展示结果的后备方案）
2. 当前使用已弃用的 `AsyncTask`，可考虑迁移到 `Coroutine` 或 `ThreadPoolExecutor`
3. Release 构建需要 `s_master.keystore` 文件（已在 .gitignore 中排除，需手动传递）

---

## 五、迁移到新 PC 的步骤

### 5.1 需要复制的文件
**最小必需（Android 项目）：**
```
s_master-android/    # 整个目录
```

**可选（Skill 知识库）：**
```
s_master-skill-main/   # 如果需要在 Trae 中用 AI 分析聊天
```

### 5.2 关键配置文件（需手动设置）
| 文件 | 位置 | 说明 |
|------|------|------|
| `s_master.keystore` | `s_master-android/` | Release 签名密钥（.gitignore 排除，需单独复制） |
| `local.properties` | `s_master-android/` | Android SDK 路径（由 Android Studio 自动生成） |

### 5.3 新 PC 环境准备
1. 安装 Android Studio（推荐 Hedgehog 或更高）
2. 安装 JDK 11+
3. 通过 SDK Manager 安装 Android SDK 34
4. 用 Android Studio 打开 `s_master-android/` 目录
5. 等待 Gradle Sync 完成（会自动下载 Gradle 8.5）
6. 如需 Release 构建，将 `s_master.keystore` 复制到项目根目录

### 5.4 GitHub 备份
项目已关联 GitHub：
```bash
git remote -v
# origin  https://github.com/yellllllllllow/S-master.git
```
新 PC 上直接 `git clone https://github.com/yellllllllllow/S-master.git` 即可。

### 5.5 首次构建验证
```bash
cd s_master-android
./gradlew.bat assembleDebug    # Debug 构建
# 或使用 Android Studio → Build → Build APK(s)
```

---

## 六、联系方式 / 备注

- **GitHub 账号**: yellllllllllow
- **GitHub 仓库**: https://github.com/yellllllllllow/S-master
- **测试设备**: Android 16, 天玑 8400
- **App 名称**: S master
- **包名**: com.example.s_master
