# 🚀 S-master - GitHub 自动构建 APK

> 不用装 JDK、不用装 Android Studio，上传到 GitHub 自动出 APK。

## 操作步骤（10分钟）

### 1️⃣ 安装 Git

- 下载安装：https://git-scm.com/download/win
- 安装时全部默认选项即可

### 2️⃣ 创建 GitHub 仓库

1. 打开 https://github.com 并登录
2. 点击右上角 **+** → **New repository**
3. 仓库名填：`s_master-android`
4. 选 **Public**
5. 不要勾选任何初始化选项
6. 点击 **Create repository**

### 3️⃣ 推送代码

打开终端（在 `s_master-android` 目录下右键 → 在终端打开），按顺序执行：

```bash
git init
git add .
git commit -m "初始提交 - S-master"
git remote add origin https://github.com/你的用户名/S-master.git
git branch -M main
git push -u origin main
```

> 把 `你的用户名` 换成你的 GitHub 用户名

### 4️⃣ 等待构建

1. 打开你的 GitHub 仓库页面
2. 点击顶部 **Actions** 标签
3. 你会看到正在运行的 `Build Android APK` 任务
4. 等进度条跑完（约 5-8 分钟）

### 5️⃣ 下载 APK

1. 构建完成后，点击这个任务
2. 在 **Artifacts** 区域看到 `s_master-app-v2`
3. 点击下载 ZIP 文件
4. 解压后得到 `app-debug.apk`

### 6️⃣ 安装到手机

1. 把 APK 传到 vivo Z10T（微信文件传输/数据线/网盘都行）
2. 在手机上打开 APK 文件
3. 如果提示"禁止安装"，去 **设置 → 安全 → 允许未知来源应用** 打开
4. 安装完成后打开应用

---

## 📱 使用说明

### 首次使用

1. **打开 App** → 点击「开始监控」
2. **授权悬浮窗权限** — App 需要显示浮窗来展示建议
3. **授权屏幕录制** — App 需要读取屏幕来识别聊天内容
4. **打开 Soul** — 正常聊天即可
5. **查看建议** — 悬浮窗会自动显示 AI 分析结果
6. **复制使用** — 点击悬浮窗即可复制建议内容

### API Key 配置（可选但推荐）

> 没有 API Key 也能用，App 内置了基础分析引擎

**推荐配置 OpenAI API Key 获得多模态 AI 分析：**

1. 注册/登录 https://platform.openai.com
2. 进入 API Keys 页面创建 Key
3. 在 App 中粘贴 Key，点击保存
4. 之后 AI 会直接分析屏幕截图，自动过滤广告/UI 元素

**支持的 API：**
- OpenAI GPT-4o / GPT-4o-mini（推荐）
- 任何兼容 OpenAI API 格式的服务

---

## 🏗 架构说明

```
S-master 架构：

手机屏幕
   ↓ (MediaProjection API 截屏)
截图获取
   ↓
┌─ 有 API Key ─→ 多模态 AI（GPT-4o）
│                 ├─ OCR 识别聊天内容
│                 ├─ 过滤广告/UI/通知
│                 ├─ 分析对话状态和信号
│                 └─ 生成回复建议
│
└─ 无 API Key ─→ 内置规则引擎
                  └─ 关键词匹配 + 建议模板
```

---

## ❓ 常见问题

**Q: 提示"禁止安装了"怎么办？**
> 进入 **设置 → 安全与隐私 → 更多安全设置 → 安装未知应用**，允许文件管理器安装

**Q: 监控耗电吗？**
> App 只在屏幕内容变化时分析，每 6-10 秒一次，正常使用耗电约 5-8%/小时

**Q: 会不会被 Soul 检测到？**
> App 使用系统级屏幕录制，不注入 Soul 进程，无法被检测

**Q: 隐私安全吗？**
> 所有数据在本地处理。使用 API 时，截图会发送到你配置的 API 服务商（如 OpenAI），不会经过第三方服务器

**Q: 没有 API Key 效果怎么样？**
> 内置引擎覆盖 20+ 常见聊天场景（在吗/晚安/哈哈/约不出来等），基础场景足够用
