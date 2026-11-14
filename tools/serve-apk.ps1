# serve-apk.ps1 - build the APKs and serve them over Wi-Fi so a phone with no
# working USB port can install them from its browser.
#
#   .\tools\serve-apk.ps1              build, copy and serve
#   .\tools\serve-apk.ps1 -NoBuild     copy and serve only
#   .\tools\serve-apk.ps1 -Port 8080   serve on another port
#
# Uses the bundled Gradle 8.13 and JDK 17 so it works without a system install.
#
# If your machine blocks running scripts, use:
#   powershell -ExecutionPolicy Bypass -File .\tools\serve-apk.ps1

param(
    [int]$Port = 8000,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$java = Join-Path $projectRoot 'java17\jdk-17.0.11+9\bin\java.exe'
$gradleLib = Join-Path $projectRoot 'gradle-8.13\gradle-8.13\lib\*'
$dist = Join-Path $projectRoot 'dist'

if (-not $NoBuild) {
    Write-Host "Building APKs..."
    Push-Location $projectRoot
    try {
        & $java -cp $gradleLib org.gradle.launcher.GradleMain assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    } finally {
        Pop-Location
    }
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null

$cameraApk = Get-ChildItem (Join-Path $projectRoot 'camera\build\outputs\apk\debug') -Filter '*.apk' -ErrorAction SilentlyContinue | Select-Object -First 1
$viewerApk = Get-ChildItem (Join-Path $projectRoot 'viewer\build\outputs\apk\debug') -Filter '*.apk' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $cameraApk) { throw "Camera APK not found. Run a build first." }
if (-not $viewerApk) { throw "Viewer APK not found. Run a build first." }

Copy-Item $cameraApk.FullName (Join-Path $dist 'freecam-camera-debug.apk') -Force
Copy-Item $viewerApk.FullName (Join-Path $dist 'freecam-viewer-debug.apk') -Force
Write-Host "APKs refreshed in $dist"

$localIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
    Sort-Object { if ($_.InterfaceAlias -match 'Wi-Fi|WLAN|wireless') { 0 } else { 1 } } |
    Select-Object -First 1 -ExpandProperty IPAddress

Write-Host ""
Write-Host "  Camera APK: http://$localIp`:$Port/freecam-camera-debug.apk"
Write-Host "  Viewer APK: http://$localIp`:$Port/freecam-viewer-debug.apk"
Write-Host ""

$existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Port $Port is already serving. The refreshed APKs are live at the URL above."
    return
}

Set-Location $dist
Write-Host "Serving $dist on port $Port. Open the URL on the phone's browser to install."
python -m http.server $Port --bind 0.0.0.0
