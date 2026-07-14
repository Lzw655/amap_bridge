# ESP32-S3 联调固件

固件作为 BLE Peripheral/GATT Server，接收 Android App 的导航 JSON，在串口输出解析结果并通过 Notify 返回 ACK。

```powershell
..\scripts\build-firmware.ps1
..\scripts\flash-firmware.ps1 -Port COM5
..\scripts\flash-firmware.ps1 -Port COM5 -Monitor
```

烧录脚本不会猜测普通串口；未显式传入 `-Port` 时，仅在检测到唯一 USB 串口时自动选择。
