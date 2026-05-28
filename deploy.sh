#!/bin/bash
# S-master - GitHub 初始化脚本

echo "================================"
echo "  S-master - GitHub 部署脚本"
echo "================================"

# 检查 Git 是否安装
if ! command -v git &> /dev/null; then
    echo "❌ Git 未安装"
    echo "请先安装 Git: https://git-scm.com/downloads"
    exit 1
fi

# 检查 GitHub CLI 是否安装
if ! command -v gh &> /dev/null; then
    echo "⚠️  GitHub CLI 未安装"
    echo ""
    echo "要自动创建仓库，请安装 GitHub CLI:"
    echo "Windows: winget install GitHub.cli"
    echo "或访问: https://cli.github.com/"
    echo ""
    echo "或者手动操作:"
    echo "1. 登录 GitHub"
    echo "2. 创建新仓库 'S-master'"
    echo "3. 按照以下命令推送代码"
    echo ""
    echo "================================"
    echo "  手动部署步骤"
    echo "================================"
    echo ""
    echo "# 1. 初始化 Git 仓库"
    echo "git init"
    echo ""
    echo "# 2. 添加所有文件"
    echo "git add ."
    echo ""
    echo "# 3. 提交"
    echo 'git commit -m "Initial commit: S-master Android 应用"'
    echo ""
    echo "# 4. 添加远程仓库（替换 YOUR_USERNAME 为您的 GitHub 用户名）"
    echo "git remote add origin https://github.com/YOUR_USERNAME/S-master.git"
    echo ""
    echo "# 5. 推送代码"
    echo "git branch -M main"
    echo "git push -u origin main"
    echo ""
    exit 0
fi

# 检查是否已登录 GitHub
echo "检查 GitHub 登录状态..."
if ! gh auth status &> /dev/null; then
    echo "需要登录 GitHub..."
    gh auth login
fi

echo ""
echo "================================"
echo "  创建 GitHub 仓库"
echo "================================"

# 获取用户输入或使用默认名称
read -p "仓库名称 (默认: S-master): " REPO_NAME
REPO_NAME=${REPO_NAME:-S-master}

# 创建私有仓库
echo "创建仓库: $REPO_NAME"
gh repo create "$REPO_NAME" --private --source=. --push

echo ""
echo "================================"
echo "  ✅ 仓库创建成功！"
echo "================================"
echo ""
echo "接下来："
echo "1. 访问 https://github.com/$GITHUB_USER/$REPO_NAME"
echo "2. 点击 'Actions' 查看构建进度"
echo "3. 等待构建完成（约 5-10 分钟）"
echo "4. 在 Actions 页面点击最新构建"
echo "5. 点击 'app-debug' 下载 APK"
echo ""
echo "或者直接在 Releases 页面下载："
echo "https://github.com/$GITHUB_USER/$REPO_NAME/releases"
