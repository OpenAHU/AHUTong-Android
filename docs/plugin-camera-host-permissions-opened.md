# 插件相机能力开放：本次改动开放的权限清单

> 范围：**只改安大通宿主本体**（权限、契约接口、装载链）。插件本体（`feature/docscan`）另行开发。
> 插件接入方式见[插件开发指南](plugin-dev-guide.md)。

## 一、Android 系统权限（宿主 Manifest 新增）

| 权限/声明 | 类型 | 说明 |
|---|---|---|
| `android.permission.CAMERA` | 危险权限（运行时授权） | 宿主持有，插件**不能自取**——统一经 `host.camera()` 门控后由宿主 Activity 发起系统弹窗 |
| `<uses-feature android.hardware.camera.any required=false>` | 硬件特性声明 | 无相机设备（平板等）不影响安装 |

**没有开放**（明确拒绝）：相册批量读取、`MANAGE_EXTERNAL_STORAGE`、后台相机、屏幕捕获、
运行期下载 so。相册导入走系统 Photo Picker（零权限），导出走 MediaStore / 插件专属目录。

## 二、插件契约新开放的能力（core/plugin-api）

| 新增 | 门控 | 插件获得什么 |
|---|---|---|
| `PluginCapability.CAMERA`（枚举值） | 声明制 | 安装弹窗显示「相机」标签；未声明调 `camera()` 即抛 `PluginCapabilityDeniedException` |
| `PluginCameraService`（接口） | 经 `PluginHostV2.camera()` | 权限查询/系统弹窗、CameraX 取景预览（`AndroidView` 可嵌）、拍照（EXIF 已校正、主线程回调）、逐帧分析（后台线程、短边≤480px 降采样）、手电筒 |
| `PluginHostV2`（扩展接口） | `host is PluginHostV2` 探测 | 旧宿主优雅降级，直接调会 NoSuchMethodError，探测安全 |
| `pluginFilesDir()` | 须声明 `PLUGIN_STORAGE` | 插件专属二进制目录 `filesDir/ahup_plugins/<插件id>/`，按 id 隔离、卸载连带清理 |
| `pickImage()` | 无门控（零权限） | 系统照片选择器，只读 Uri 回调，取消回调 null |
| `close()` | 无门控 | 插件**关闭自己**：宿主 popBackStack 退出插件页。返回前的二次确认由插件 UI 自理（`BackHandler` 弹自己的确认弹窗，确认后调 `close()`）；旧宿主探测不到 `PluginHostV2` 时退化为系统返回 |

## 三、宿主侧配套改动（装载链，非权限但一并说明）

- **CameraX 四件套**（core/camera2/lifecycle/view 1.4.2）只进 `:app`；插件经父 ClassLoader 共享。
- **`.ahup` 包格式 v2**：包内可选 `lib/<abi>/*.so`（如 OpenCV）；
  签名载荷升级为 `sha256(dex+icon+全部.so 按相对路径排序拼接)`——
  **原生库必须纳入签名信任链**（.so 是可执行代码，不签等于给改包注入留门）。
  v1 包（无 lib/）校验行为完全不变，旧插件零影响。
- `RuntimePluginLoader` 按 `Build.SUPPORTED_ABIS` 传 `librarySearchPath`，插件内 `OpenCVLoader.initLocal()` 可加载随包分发的库。
- 卸载/关闭开发者开关时连带清理 `ahup_plugins/<id>/` 二进制目录。

## 四、隐私与安全纪律

- 相机**只**经 `camera()` 门控开放，插件无绕过路径（无自取权限、无反射口子）。
- 宿主**不做**任何自动上传/云端处理；拍到的 Bitmap 只回给插件本地使用。
  插件要联网另受 `NETWORK` 能力门控，两个能力独立声明。
- 拍照指示灯/隐私提示由 Android 系统承担，宿主不自绘、不隐藏。
- 永久拒绝权限后宿主不再自动弹窗（回调 false，由插件 UI 引导去设置）。
