$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "android-env.ps1")

$repoRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot "android-app"
Push-Location $androidRoot
try {
    & (Join-Path $androidRoot "gradlew.bat") --no-daemon testDebugUnitTest assembleDebug lintDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Android build failed."
    }
} finally {
    Pop-Location
}
