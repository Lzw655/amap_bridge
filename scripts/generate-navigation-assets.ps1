$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "android-env.ps1")

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & java (Join-Path $repoRoot "tools\NavigationAssetGenerator.java") $repoRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Navigation asset generation failed."
    }
} finally {
    Pop-Location
}
