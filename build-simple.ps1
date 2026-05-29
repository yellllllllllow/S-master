# S-master - Simple Build Script
$ErrorActionPreference = "Continue"
$start = Get-Date

Write-Host "======================================="
Write-Host "   S-master - APK Build"
Write-Host "======================================="

# Set JDK
$jdkHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:PATH"

Write-Host "Environment check..."
& java -version 2>&1 | Select-Object -First 1

$sdkUser = "$env:USERPROFILE\.android-sdk"
$env:ANDROID_HOME = $sdkUser
$env:ANDROID_SDK_ROOT = $sdkUser

# Create local.properties
Set-Content -Path "$PSScriptRoot\local.properties" -Value "sdk.dir=$sdkUser`n" -Force

Write-Host "Starting build..."
Push-Location $PSScriptRoot

# Clean old build
if (Test-Path "app\build") {
    Remove-Item "app\build" -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "Cleaned old build"
}

# Run gradle
& .\gradlew.bat assembleDebug --no-daemon

if ($LASTEXITCODE -eq 0) {
    $elapsed = (Get-Date) - $start
    Write-Host "Build SUCCESS! Time: $($elapsed.Minutes)m $($elapsed.Seconds)s"

    $apkFiles = Get-ChildItem "app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue
    foreach ($apk in $apkFiles) {
        $size = "{0:N1}" -f ($apk.Length / 1MB)
        Write-Host "APK: $($apk.FullName)"
        Write-Host "Size: ${size} MB"
    }
} else {
    Write-Host "Build FAILED"
}

Pop-Location
