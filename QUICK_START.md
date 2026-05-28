# 快速开始指南

## 方式一：手动操作（推荐新手）

### 步骤 1：下载项目
下载 `s_master-android-v1.0.zip` 文件

### 步骤 2：创建 GitHub 仓库
1. 打开浏览器，访问 https://github.com
2. 登录您的 GitHub 账号
3. 点击右上角 "+" → "New repository"
4. Repository name 填写：`s_master-android`
5. 选择 Private
6. 点击 "Create repository"

### 步骤 3：上传代码
1. 在新建的仓库页面，点击 "uploading an existing file"
2. 拖拽所有项目文件到上传区域
3. 点击 "Commit changes"

### 步骤 4：触发构建
1. 在仓库页面，点击 "Actions" 标签
2. GitHub 会自动检测到工作流
3. 点击 "I understand my workflows, go ahead and enable them"
4. 等待构建（约 5-10 分钟）
5. 点击构建任务 → "app-debug" 下载 APK

---

## 方式二：安装 Git 后操作

### 安装 Git
**Windows:**
```bash
winget install Git.Git
```
或下载安装包：https://git-scm.com/download/win

**重启电脑后：**

```bash
# 进入项目目录
cd s_master-android

# 初始化
git init
git add .
git commit -m "Initial commit"

# 添加远程仓库（替换 YOUR_USERNAME）
git remote add origin https://github.com/YOUR_USERNAME/S-master.git
git branch -M main
git push -u origin main
```

---

## 下载 APK

构建完成后：
1. 访问您的仓库
2. 点击 "Actions" 标签
3. 点击构建任务
4. 在 Artifacts 部分点击 "app-debug" 下载
5. 解压后安装到手机

---

## 文件清单

```
s_master-android/
├── .github/workflows/android.yml  ← GitHub Actions 配置
├── app/
│   ├── src/main/
│   │   ├── java/com/example/s_master/  ← Java 代码
│   │   └── res/  ← 资源文件
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── README.md  ← 项目说明
├── GITHUB_DEPLOY.md  ← 详细部署指南
└── deploy.sh  ← 部署脚本（需要 GitHub CLI）
```

---

## 需要帮助？

如果遇到问题，请查看：
- `README.md` - 项目说明
- `GITHUB_DEPLOY.md` - 详细部署步骤
