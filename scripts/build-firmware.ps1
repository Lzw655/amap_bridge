$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "idf-env.ps1")

$repoRoot = Split-Path -Parent $PSScriptRoot
$firmwareRoot = Join-Path $repoRoot "firmware"
Push-Location $firmwareRoot
try {
    if (-not (Test-Path (Join-Path $firmwareRoot "sdkconfig"))) {
        & python (Join-Path $env:IDF_PATH "tools\idf.py") set-target esp32s3
        if ($LASTEXITCODE -ne 0) { throw "Failed to set ESP32-S3 target." }
    }
    & python (Join-Path $env:IDF_PATH "tools\idf.py") --ccache build
    if ($LASTEXITCODE -ne 0) { throw "ESP-IDF build failed." }
} finally {
    Pop-Location
}
