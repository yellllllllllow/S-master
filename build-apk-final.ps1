# S-master - 最终构建脚本
param([Switch]$Clean)

$ErrorActionPreference = "Continue"
$start = Get-Date

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "   S-master - APK 构建" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan

# 设置 JDK
$jdkHome = "C:\Users\H\AppData\Local\Temp\jdk\jdk-17.0.12+7"
$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:PATH"

Write-Host "`n🔍 环境验证..." -ForegroundColor Yellow
java -version 2>&1 | Select-Object -First 1
$sdkUser = "$env:USERPROFILE\.android-sdk"
$env:ANDROID_HOME = $sdkUser
$env:ANDROID_SDK_ROOT = $sdkUser
Write-Host "ANDROID_HOME: $sdkUser"

# 验证 SDK
$hasPlatform = Test-Path "$sdkUser\platforms\android-34"
$hasBuildTools = Test-Path "$sdkUser\build-tools\34.0.0"
Write-Host "平台: $(if($hasPlatform){'✅'}else{'❌'}) android-34"
Write-Host "构建工具: $(if($hasBuildTools){'✅'}else{'❌'}) build-tools"

# 创建 local.properties
Set-Content -Path "$PSScriptRoot\local.properties" -Value "sdk.dir=$sdkUser`n" -Force

# 如果之前成功安装过 build-tools，用 find 检查
if (-not $hasBuildTools) {
    $btCandidates = @("C:\Users\H\AppData\Local\Android\Sdk\build-tools", "$env:LOCALAPPDATA\Android\Sdk\build-tools", "$env:ProgramFiles\Android\build-tools")
    foreach ($dir in $btCandidates) {
        if (Test-Path $dir) {
            $bt = Get-ChildItem $dir -Directory | Select-Object -First 1
            if ($bt) { 
                $env:ANDROID_HOME = Split-Path $dir -Parent
                Set-Content -Path "$PSScriptRoot\local.properties" -Value "sdk.dir=$env:ANDROID_HOME`n" -Force
                Write-Host "找到 build-tools: $($bt.FullName)"
                $hasBuildTools = $true
                break
            }
        }
    }
}

# 如果还没有 build-tools，尝试用 sdkmanager
if (-not $hasBuildTools) {
    Write-Host "`n安装 build-tools..." -ForegroundColor Yellow
    $process = Start-Process -FilePath "$sdkUser\cmdline-tools\latest\bin\sdkmanager.bat" -ArgumentList "--sdk_root=$sdkUser","build-tools;34.0.0" -NoNewWindow -RedirectStandardInput ([System.IO.MemoryStream]::new()) -PassThru -Wait
}

Write-Host "`n🔨 开始编译..." -ForegroundColor Green
Push-Location $PSScriptRoot

# 清理旧 build
if ($Clean -and (Test-Path "app\build")) {
    Remove-Item "app\build" -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "已清理旧构建文件" -ForegroundColor Yellow
}

# 运行 gradle
$gradleResult = & .\gradlew.bat assembleDebug --no-daemon 2>&1

if ($LASTEXITCODE -eq 0) {
    $elapsed = (Get-Date) - $start
    Write-Host "`n✅✅✅ 构建成功！耗时 $($elapsed.Minutes)分$($elapsed.Seconds)秒" -ForegroundColor Green
    Write-Host "=======================================" -ForegroundColor Cyan
    
    $apkFiles = Get-ChildItem "app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue
    foreach ($apk in $apkFiles) {
        $size = "{0:N1}" -f ($apk.Length / 1MB)
        Write-Host "📱 APK 文件: $($apk.FullName)" -ForegroundColor Green
        Write-Host "   大小: ${size} MB" -ForegroundColor Green
        Write-Host "   修改时间: $($apk.LastWriteTime)" -ForegroundColor Green
    }
    
    Write-Host "`n📋 复制命令:" -ForegroundColor Yellow
    Write-Host "  Copy-Item '$PSScriptRoot\app\build\outputs\apk\debug\*.apk' 'C:\Users\H\Documents\trae_projects\sheng\s_master-android\'" -ForegroundColor Gray
} else {
    Write-Host "`n❌ 构建失败" -ForegroundColor Red
    # 输出最后 50 行错误
    $gradleResult | Select-Object -Last 50 | ForEach-Object { Write-Host $_ -ForegroundColor Red }
}

Pop-Location
