$ErrorActionPreference = "Stop"
$env:PYTHONUTF8 = "1"

$idfRoot = if ($env:IDF_PATH) { $env:IDF_PATH } else { Join-Path $env:USERPROFILE "esp\work\esp-idf-github" }
$exportScript = Join-Path $idfRoot "export.ps1"
$installScript = Join-Path $idfRoot "install.ps1"

if (-not (Test-Path $exportScript)) {
    throw "ESP-IDF was not found at $idfRoot. Install ESP-IDF and retry."
}

try {
    . $exportScript
    & python (Join-Path $env:IDF_PATH "tools\idf.py") --version
    if ($LASTEXITCODE -ne 0) { throw "ESP-IDF environment check failed." }
} catch {
    Write-Warning "ESP-IDF is incomplete. Attempting to install the ESP32-S3 toolchain."
    if (-not (Test-Path $installScript)) { throw }
    & $installScript esp32s3
    if ($LASTEXITCODE -ne 0) {
        throw "Automatic installation failed. Run '$installScript esp32s3' manually and retry."
    }
    . $exportScript
}
