# Android App

## Build

在仓库根目录运行：

```powershell
.\scripts\build-android.ps1
```

Debug APK 输出到 `android-app/app/build/outputs/apk/debug/app-debug.apk`。

## Use

1. 安装 APK，并打开“高德导航通知访问”和“附近设备”权限。
2. 烧录并启动 ESP32-S3 联调固件。
3. 点击“启动投影”，选择 `AmapBridge-ESP32` 并连接。
4. 使用“模拟导航”验证 BLE，或打开高德地图开始实际导航。

App 只读取包名为 `com.autonavi.minimap` 的通知，不上传或持久化导航内容。
