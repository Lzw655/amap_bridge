param(
    [string]$Port,
    [switch]$Monitor
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "idf-env.ps1")

if (-not $Port) {
    $usbPorts = @(Get-CimInstance Win32_SerialPort | Where-Object {
        $_.PNPDeviceID -match "^USB" -or $_.Name -match "USB|JTAG|UART|CP210|CH340"
    })
    if ($usbPorts.Count -eq 0) {
        throw "No ESP32-S3 USB serial port was detected. Connect the board and retry with -Port COMx."
    }
    if ($usbPorts.Count -gt 1) {
        $names = ($usbPorts | ForEach-Object DeviceID) -join ", "
        throw "Multiple USB serial ports were detected: $names. Select one explicitly with -Port."
    }
    $Port = $usbPorts[0].DeviceID
}

if ($Port -notmatch "^COM\d+$") {
    throw "Invalid serial port '$Port'; expected COMx."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$firmwareRoot = Join-Path $repoRoot "firmware"
Push-Location $firmwareRoot
try {
    $idf = Join-Path $env:IDF_PATH "tools\idf.py"
    & python $idf --ccache -p $Port -b 921600 build flash
    if ($LASTEXITCODE -ne 0) { throw "ESP32-S3 flash failed." }
    if ($Monitor) {
        & python $idf -p $Port monitor
    }
} finally {
    Pop-Location
}
