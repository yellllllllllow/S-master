# S-master - 一键部署到 GitHub Actions 自动构建 APK
# 使用方法：在 PowerShell 中运行本脚本

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "  S-master - APK 自动构建部署" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""

# 检查 git
try { git --version 2>$null | Out-Null }
catch {
    Write-Host "❌ 未安装 Git！请先安装 Git：" -ForegroundColor Red
    Write-Host "   https://git-scm.com/download/win" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Git 已安装" -ForegroundColor Green

# 检查是否已有仓库
if (Test-Path ".git") {
    Write-Host "⚠️  当前目录已是 Git 仓库" -ForegroundColor Yellow
} else {
    Write-Host "📦 初始化 Git 仓库..." -ForegroundColor Yellow
    git init
}

Write-Host ""
Write-Host "📋 下一步操作：" -ForegroundColor Cyan
Write-Host "  1. 在 https://github.com 创建新仓库" -ForegroundColor White
Write-Host "  2. 执行以下命令推送代码：" -ForegroundColor White
Write-Host ""
Write-Host "  git add ." -ForegroundColor Green
Write-Host '  git commit -m "初始提交 - S-master"' -ForegroundColor Green
Write-Host "  git remote add origin https://github.com/你的用户名/你的仓库名.git" -ForegroundColor Green
Write-Host "  git branch -M main" -ForegroundColor Green
Write-Host "  git push -u origin main" -ForegroundColor Green
Write-Host ""
Write-Host "⏳ 推送后，GitHub Actions 会自动构建 APK" -ForegroundColor Cyan
Write-Host "📱 构建完成后在 Actions 页面下载 APK" -ForegroundColor Cyan
Write-Host ""
