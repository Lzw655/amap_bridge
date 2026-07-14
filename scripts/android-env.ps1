$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot "android-app"
$studioRoot = "C:\Program Files\Android\Android Studio"
$sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"

if (-not (Test-Path "$studioRoot\jbr\bin\java.exe")) {
    throw "Android Studio bundled JDK was not found at $studioRoot\jbr. Install Android Studio and retry."
}
if (-not (Test-Path "$sdkRoot\platforms\android-34\android.jar")) {
    throw "Android SDK Platform 34 is missing. Install Android 14 (API 34) in SDK Manager."
}
if (-not (Test-Path "$sdkRoot\build-tools\34.0.0")) {
    throw "Android SDK Build-Tools 34.0.0 is missing. Install it in SDK Manager."
}

$env:JAVA_HOME = Join-Path $studioRoot "jbr"
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:Path = "$env:JAVA_HOME\bin;$sdkRoot\platform-tools;$env:Path"

$escapedSdk = $sdkRoot.Replace("\", "\\")
Set-Content -LiteralPath (Join-Path $androidRoot "local.properties") -Value "sdk.dir=$escapedSdk" -Encoding ASCII

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT"
