# S-master - Release APK 构建脚本

$ErrorActionPreference = "Continue"
$start = Get-Date

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "   S-master - Release APK 构建" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan

# 设置 JDK
$jdkHome = "C:\Users\H\AppData\Local\Temp\jdk\jdk-17.0.12+7"
$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:PATH"

Write-Host "`n环境验证..." -ForegroundColor Yellow
java -version 2>&1 | Select-Object -First 1

# 设置 Android SDK
$sdkUser = "$env:USERPROFILE\.android-sdk"
$env:ANDROID_HOME = $sdkUser
$env:ANDROID_SDK_ROOT = $sdkUser
Write-Host "ANDROID_HOME: $sdkUser"

# 创建 local.properties
Set-Content -Path "$PSScriptRoot\local.properties" -Value "sdk.dir=$sdkUser`n" -Force

# 如果找不到 SDK，尝试其他位置
if (-not (Test-Path $sdkUser)) {
    $sdkUser = "C:\Users\H\AppData\Local\Android\Sdk"
    if (Test-Path $sdkUser) {
        $env:ANDROID_HOME = $sdkUser
        $env:ANDROID_SDK_ROOT = $sdkUser
        Set-Content -Path "$PSScriptRoot\local.properties" -Value "sdk.dir=$sdkUser`n" -Force
        Write-Host "使用备用 SDK 路径: $sdkUser"
    }
}

Write-Host "`n开始编译 Release APK..." -ForegroundColor Green
Push-Location $PSScriptRoot

# 运行 gradle 构建 Release
$gradleResult = & .\gradlew.bat assembleRelease --no-daemon 2>&1

if ($LASTEXITCODE -eq 0) {
    $elapsed = (Get-Date) - $start
    Write-Host "`n构建成功！耗时 $($elapsed.Minutes)分$($elapsed.Seconds)秒" -ForegroundColor Green
    Write-Host "=======================================" -ForegroundColor Cyan
    
    $apkFiles = Get-ChildItem "app\build\outputs\apk\release\*.apk" -ErrorAction SilentlyContinue
    foreach ($apk in $apkFiles) {
        $size = "{0:N1}" -f ($apk.Length / 1MB)
        Write-Host "APK 文件: $($apk.FullName)" -ForegroundColor Green
        Write-Host "   大小: ${size} MB" -ForegroundColor Green
        Write-Host "   修改时间: $($apk.LastWriteTime)" -ForegroundColor Green
    }
} else {
    Write-Host "`n构建失败" -ForegroundColor Red
    $gradleResult | Select-Object -Last 30 | ForEach-Object { Write-Host $_ }
}

Pop-Location