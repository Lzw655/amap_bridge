# Amap Bridge

Amap Bridge 将 Android 手机上的高德导航通知解析为结构化导航数据，并通过 BLE 发送给 ESP32-S3。

数据链路：

```text
高德地图 → NotificationListenerService → Android App → BLE GATT → ESP32-S3
```

当前 App 提供三个页面：

- **连接调试**：权限引导、ESP32 扫描/连接、当前导航信息和诊断日志。
- **导航模拟**：选择全部 13 种协议动作，编辑道路、距离、时间、ETA、速度和限速，支持单次发送及每 2 秒自动轮播。
- **ESP 预览**：按 320×240 设计坐标模拟最终 ESP 屏幕，缺失字段显示 `--`。

模拟数据、高德通知和 BLE 发送共用同一个导航模型；BLE 已连接时，从模拟器发布的数据会直接发送给 ESP32。

项目目录：

- `android-app/`：Kotlin + Jetpack Compose Android 应用。
- `firmware/`：ESP-IDF NimBLE GATT Server 联调固件。
- `docs/protocol.md`：BLE 协议 v1。
- `assets/navigation/`：SVG、透明 PNG、精灵图、坐标清单和 320×240 参考图。
- `tools/NavigationAssetGenerator.java`：基于 JDK 17 的确定性素材生成器。
- `scripts/`：Windows 环境、构建和烧录脚本。

## 1. 已验证的开发环境

本项目已在以下 Windows 环境完成实际构建验证：

- Windows 10 64 位。
- Android Studio，内置 JDK 17。
- Android SDK Platform 34、Build Tools 34.0.0、Platform Tools/ADB。
- Android Emulator 34.2.13，API 34 x86_64 AVD。
- Gradle 8.6、Android Gradle Plugin 8.4.0、Kotlin 1.9.22。
- ESP-IDF v6.1-dev，目标芯片 `esp32s3`。

本机使用的默认路径为：

```text
Android Studio: C:\Program Files\Android\Android Studio
Android SDK:    %LOCALAPPDATA%\Android\Sdk
ESP-IDF:        %USERPROFILE%\esp\work\esp-idf-github
```

Android 和 ESP-IDF 脚本会在当前 PowerShell 会话中配置环境，不要求修改系统级 `PATH`、`JAVA_HOME` 或 `ANDROID_HOME`。

## 2. 获取并进入项目

打开 PowerShell：

```powershell
cd C:\Users\liu\Desktop\ESP-Brookesia\Repo\amap_bridge
```

后续命令默认都从该目录执行。

## 3. 构建 Android App

运行：

```powershell
.\scripts\build-android.ps1
```

脚本会：

1. 查找 Android Studio 内置 JDK 17。
2. 检查 SDK Platform 34 和 Build Tools 34.0.0。
3. 生成本机专用且不会提交到 Git 的 `local.properties`。
4. 运行单元测试、Debug APK 构建和 Android Lint。

APK 输出位置：

```text
android-app\app\build\outputs\apk\debug\app-debug.apk
```

当前验证结果为 14 个单元测试和 2 个 API 34 仪器测试全部通过，Android Lint 0 errors。

重新生成导航素材：

```powershell
.\scripts\generate-navigation-assets.ps1
```

素材生成器不需要 Pillow、CairoSVG 或其他系统图形工具。生成结果和使用方式见
[`assets/navigation/README.md`](assets/navigation/README.md)。

如果脚本报告缺少 SDK 34 或 Build Tools 34.0.0，请打开：

```text
Android Studio → More Actions → SDK Manager
```

安装以下项目后重新运行脚本：

- Android 14 / API Level 34。
- Android SDK Build-Tools 34.0.0。
- Android SDK Platform-Tools。
- Android Emulator。

## 4. 在 Windows 启动 Android 模拟器

当前已创建并验证的 AVD 名称为 `Pixel_3a_API_34`。

先查看已有 AVD：

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
& "$sdk\emulator\emulator.exe" -list-avds
```

本机稳定启动命令：

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"

Start-Process `
    -FilePath "$sdk\emulator\emulator.exe" `
    -ArgumentList @(
        "-avd", "Pixel_3a_API_34",
        "-no-snapshot-load",
        "-gpu", "swiftshader_indirect"
    )
```

参数说明：

- `-no-snapshot-load`：冷启动，避免损坏或过期的快照导致卡住。
- `-gpu swiftshader_indirect`：使用软件渲染，规避部分 Windows 显卡兼容问题。

当前环境已确认 Windows Hypervisor Platform（WHPX）正常工作，不需要关闭 Hyper-V。

等待模拟器上线：

```powershell
. .\scripts\android-env.ps1
adb wait-for-device
adb devices -l
```

正常输出应包含类似内容：

```text
emulator-5554    device
```

确认 Android 启动完成：

```powershell
adb shell getprop sys.boot_completed
```

输出 `1` 表示系统已完成启动。

## 5. 将 App 安装到模拟器

```powershell
. .\scripts\android-env.ps1

adb install -r .\android-app\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.espressif.amapbridge/.MainActivity
```

也可以让 Gradle 直接构建并安装：

```powershell
. .\scripts\android-env.ps1
cd .\android-app
.\gradlew.bat installDebug
```

模拟器适合验证：

- Compose UI。
- 权限引导页面。
- 高德通知解析单元测试。
- App 内的“模拟导航”数据。
- 320×240 ESP 屏幕预览和全部导航图标。

运行 Compose 仪器测试（需要已启动的 API 34 模拟器）：

```powershell
. .\scripts\android-env.ps1
cd .\android-app
.\gradlew.bat connectedDebugAndroidTest
```

真实高德通知和 ESP32 BLE 联调应使用 Android 真机。当前使用的模拟器版本不作为真实 BLE 验收环境。

## 6. 构建 ESP32-S3 固件

回到仓库根目录：

```powershell
cd C:\Users\liu\Desktop\ESP-Brookesia\Repo\amap_bridge
.\scripts\build-firmware.ps1
```

脚本会：

1. 通过 ESP-IDF 的 `export.ps1` 启用工具链。
2. 设置 UTF-8 环境，避免中文 Windows 控制台编码告警。
3. 明确选择 `esp32s3` target。
4. 使用 ccache 构建固件。
5. 通过 ESP-IDF Component Manager 自动下载官方 `espressif/cjson` 依赖。

固件输出位置：

```text
firmware\build\amap_bridge_firmware.bin
```

当前构建出的固件约 464 KiB，默认应用分区仍有约 55% 空间。

## 7. 烧录 ESP32-S3

连接 ESP32-S3 后查看串口：

```powershell
Get-CimInstance Win32_SerialPort |
    Select-Object DeviceID, Name, PNPDeviceID
```

明确指定串口烧录，例如：

```powershell
.\scripts\flash-firmware.ps1 -Port COM5
```

烧录并打开串口监视器：

```powershell
.\scripts\flash-firmware.ps1 -Port COM5 -Monitor
```

脚本不会把普通系统串口猜测为 ESP32。如果没有检测到 USB 串口，它会停止并等待连接开发板；如果有多个 USB 串口，必须通过 `-Port COMx` 明确选择。

固件启动后会广播：

```text
AmapBridge-ESP32
```

## 8. Android 真机联调

1. 在 Android 真机上安装 Debug APK。
2. 打开 App，授予“附近设备”权限。
3. 点击“授权通知访问”，在系统设置中允许 Amap Bridge 读取通知。
4. 启动已经烧录的 ESP32-S3。
5. 在 App 中点击“启动投影”。
6. 扫描并连接 `AmapBridge-ESP32`。
7. 切换到“导航模拟”，点击“发送一次”，确认 ESP32 串口收到导航 JSON 并返回 ACK。
8. 打开高德地图开始导航，确认导航动作、距离和道路信息可以同步。

App 只读取包名为 `com.autonavi.minimap` 的高德通知，不上传或持久化导航内容。

## 9. 常见问题

### `adb wait-for-device` 一直不返回

这是正常的等待状态，说明还没有模拟器或真机上线。按 `Ctrl+C` 退出，先启动模拟器，再执行：

```powershell
adb kill-server
adb start-server
adb devices -l
```

### 模拟器命令执行后没有窗口

使用本文带 `-no-snapshot-load` 和 `-gpu swiftshader_indirect` 的稳定启动命令，并检查：

```powershell
Get-Process emulator,qemu-system-x86_64 -ErrorAction SilentlyContinue
adb devices -l
```

日志中关于 crash attachment 文件不存在或 DirectSoundCapture 不可用的消息，在本次验证中属于非致命警告；只要 `qemu-system-x86_64` 进程存在且 ADB 显示 `device`，模拟器已经正常运行。

### 首次构建耗时较长

首次 Android 构建需要解析 Compose/Gradle 依赖；首次 ESP-IDF 构建需要下载 cJSON 并编译完整组件。后续构建会使用 Gradle 缓存和 ccache，速度会明显提升。

### BLE 协议

完整 UUID、JSON 字段、消息长度和 ACK/NACK 规则见 [`docs/protocol.md`](docs/protocol.md)。
