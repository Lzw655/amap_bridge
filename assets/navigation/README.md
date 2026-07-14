# 导航素材包

本目录包含 Amap Bridge 的原创深色高对比导航素材。所有格式都由同一份 Java2D 几何定义生成，图标主体为白色，强调色为 `#35D07F`，不包含高德商标、地图底图或专有视觉资产。

## 内容

- `svg/`：14 个 128×128 SVG 源文件。
- `png/128/`：对应的 128×128 RGBA 透明 PNG。
- `navigation-sprite-4x4.png`：512×512、4×4 精灵图；未使用的两个格子透明。
- `manifest.json`：每个图标的 wire value、文件名和精灵图坐标。
- `esp-preview-320x240.png`：完整 ESP 屏幕参考图。
- Android VectorDrawable 位于 `android-app/app/src/main/res/drawable/ic_nav_*.xml`。

13 个协议动作是 `straight`、`left`、`right`、`slight_left`、`slight_right`、`sharp_left`、`sharp_right`、`u_turn`、`roundabout`、`arrive`、`merge`、`exit` 和 `unknown`；素材包另含通用 `route` 图标。

## 重新生成

在仓库根目录运行：

```powershell
.\scripts\generate-navigation-assets.ps1
```

脚本使用 Android Studio 内置 JDK 17 或当前 `JAVA_HOME`，不新增第三方图形依赖。生成结束前会自动验证 PNG 尺寸及透明通道、SVG `viewBox`、精灵图尺寸和 manifest 条目数。

这些输出是确定性的。修改图标时只编辑 `tools/NavigationAssetGenerator.java` 中的几何定义，然后重新运行生成脚本，不要分别手工修改 SVG、PNG 和 VectorDrawable。
