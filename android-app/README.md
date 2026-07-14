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
3. 在“连接调试”页点击“启动投影”，选择 `AmapBridge-ESP32` 并连接。
4. 在“导航模拟”页选择动作并编辑导航字段，点击“发送一次”，或开启每 2 秒自动轮播。
5. 在“ESP 预览”页检查 320×240 屏幕布局；也可打开高德地图开始实际导航。

模拟器支持全部协议动作和以下字段：下一动作距离、道路、总剩余距离、剩余时间、ETA、当前速度、限速和原始文本。输入不合法时不会发布数据或加入 BLE 队列。

## UI tests

启动 API 34 模拟器后，在仓库根目录运行：

```powershell
. .\scripts\android-env.ps1
cd .\android-app
.\gradlew.bat connectedDebugAndroidTest
```

App 只读取包名为 `com.autonavi.minimap` 的通知，不上传或持久化导航内容。
