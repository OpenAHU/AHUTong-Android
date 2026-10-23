# Release / Debug 首页返回性能核查

日期：2026-10-23。基线：官方 `master@72eb6606a626a0a4f8204e9ccc048a879708505c`，版本 `3.4.6 / 304010`。工作分支：`p/MuxYang/fix/payment-credential-retry`。

用户在 Pixel 9 Pro 上反馈 Release 切回主页明显比 Debug 卡，明确路径为“课表 → 主页”，所有主题均有体感。已经修复两个可证实的问题：主页重组同步解密法律同意值，以及自定义背景的毛玻璃忽略“关闭部分玻璃效果”开关。第二项在同一优化构建配置、同一数据目录与背景下，使帧耗时 P50 从 **17.383 ms 降至 7.345 ms**；P95 从 **44.687 ms 降至 41.729 ms**。仍有长帧，不能据此宣称所有主题、所有底栏路径均已无卡顿。

手机正式 Release 的原背景和偏好未修改；最终已恢复正常 Debug 构建和测试前背景状态。支付凭证恢复见 [校园卡凭证自动恢复](campus-card-credential-recovery.md)。

## 变体及现场配置差异

| 项目 | Release | Debug | 依据 |
| --- | --- | --- | --- |
| 包名、数据目录 | `com.ahu.ahutong` | `com.ahu.ahutong.debug` | `app/build.gradle.kts` |
| R8 / 资源裁剪 | 开启 | 关闭 | 同上，两者使用相同业务源码 |
| 数据源 | 启动时清除旧 mock 开关，强制真实数据 | 可使用保存的 mock 设置；现场核对为真实数据 | `AHUApplication.java`、`AHUCache.kt` |
| Native 库 | 可以使用私有目录内的热更新库，并在 IO 检查更新 | 删除热更新缓存、使用内置库、不检查更新 | `sdk/RustSDK.kt` |
| APK 更新检查 | 执行，主体在 IO | 跳过 | `MainActivity.kt`、`MainViewModel.kt` |
| Sentry / HTTP 日志 | 事务采样 0.1；无 Debug HTTP 日志拦截器 | 采样 1.0；开启脱敏 HTTP 日志 | `AHUApplication.java`、`core/network/.../NetworkLogging.kt` |
| 诊断 UI | 无诊断页面、悬浮入口 | 可用诊断入口，首页有 Debug 标志 | 变体 diagnostics contribution、`Home.kt` |
| 现场主题 / 深色模式 | Miuix / 跟随系统 | Miuix / 跟随系统 | 两包 UI 只读核对 |
| 现场默认付款码 / 组件覆盖 | 关闭 / 全部 13 槽跟随 Miuix | 关闭 / 全部 13 槽跟随 Miuix | 两包 UI 只读核对 |
| **现场自定义背景** | **开启，遮罩 50%，关闭部分玻璃效果=开启** | **关闭** | 两包 UI 只读核对；Debug 无背景时相关开关隐藏 |

同版本、同主题并不代表绘制配置相同。两个包的背景、组件覆盖、账号和缓存均独立保存。Sentry 与 HTTP 日志在 Debug 更重，源码证据不支持直接用这两项解释 Release 更卡；Sentry tracing 插桩、自动安装和 runtime optimization 在 Gradle 中也已关闭。

另核对了 APK 内置 Rust SO：正式包 arm64 构件为 4,214,144 字节，本地构建为 4,554,512 字节，hash 不同。发布工作流会从固定 GuiXu-Rust 提交编译，本地标准 Gradle 使用仓库自带构件。这是已确认的产物差异，尚无实验将其证明为卡顿原因。下面每组代码前后实验使用逐字节相同的内置 SO，并在各轮启动日志确认 `builtin`、无下载、进程保持不变；日志中的更新记录 `301` 不等于 SO 源码版本。

## 公共导航路径与修改

底栏 `BottomNavBar` → `Main.selectPrimaryDestination()` → `navigate("home")`，共用 `Navigation.kt` 的一级页面 220 ms 横移转场。没有 Release 专属的主页实现或额外转场时长。主页使用共享的 Discovery / Schedule ViewModel，普通组合状态仍会在重新进入时创建。

### 将法律同意值读取移出主页重组

原 `Main.kt` 在计算 `EarlyAccessInvitationHost.eligible` 时同步读取三项法律同意值。这些读取需要通过 Keystore 解密，会随主页重组重复执行。

现在由 `EarlyAccessInvitationHost` 在 `Dispatchers.IO` 中检查。状态按账号、页面资格及前台 revision 重新创建，初始为未就绪；三项同意均成立后才刷新资格、允许显示邀请。取消正常传播，读取异常则关闭邀请入口。没有新增全局凭据缓存，也没有绕过政策判断。

### 让背景毛玻璃遵守性能开关

`HomeBackgroundStore.reduceGlassEffects` 约定使用纯色表面并跳过实时模糊，但 `appWallpaperFrostedSurface()` 原来在有背景时始终创建 18 dp backdrop blur，未读取 `LocalGlassEffectsReduced`。部分大卡即使先走降级分支，也会经此共享 helper 再次启用模糊。

修复位于 `core/designsystem/.../LiquidGlassSurface.kt`：背景存在且性能开关开启时，返回相同形状的裁剪与不透明 tint 表面；关闭性能开关时保留原有模糊路径，无背景时维持原行为。共享 helper 覆盖卡片、底栏、输入框等 17 处调用。未改动画时长、主题套装或 backdrop host 捕获架构，未修改公共函数签名和依赖。

## 实机方法与校正

设备为 Pixel 9 Pro / caiman、API 37，界面 960×2142、density 360，测量帧间隔约 8.333 ms（120 Hz）。所有命令显式指定由 `adb devices` 确认的 `$pixelSerial`；另一台模拟器没有混入真机采样。

每一阶段先 force-stop 两包，再通过 `am start --activity-single-top` 各启动一次、各预热 3 次。测量交替运行两包各 10 轮“课表 → 主页”，每个主页窗口约 1.6 秒；前后只读核对两包均只有 1 个 MainActivity。正式 Release 始终作为环境参考，代码前后因果比较在同一个测试包数据目录进行。

早期工具每轮直接 `am start`，导致累积到 Release 15 个、Debug 3 个 Activity，出现数百毫秒长帧和随轮次增长的重复读取。该批数据已撤回，不用于用户真实单实例场景的结论。修正工具后，同版本普通 Debug 与正式 Release 的单实例 P50 / P95 为 `9.781 / 75.063 ms` 和 `19.243 / 79.894 ms`。用户继续手动确认 Release 明显更卡，故没有以接近的 P95 否定现场反馈。

优化测试包保留 Release 的 R8、资源裁剪与 `BuildConfig.DEBUG=false`，仅由忽略目录内的临时 init 脚本改变包名为 `.debug`、测试签名和 `-release-test` 后缀，以保留 Debug 数据。正式安装包没有被覆盖，项目 Gradle 配置未改。

帧耗时主口径为 gfxinfo 的 `FrameCompleted - IntendedVsync`，只保留 Flags=0、时间合法且完整落在窗口内的完成帧；超 deadline 由每帧实际 `FrameDeadline` 判断。gfxinfo 使用 MONOTONIC，窗口使用 BOOTTIME，已按每份 trace 的 clock snapshot 转换。独立报告 [FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline) 的应用 surface slice，不与 gfxinfo 分位数混用。调用统计使用官方 [Trace Processor](https://perfetto.dev/docs/analysis/trace-processor) 的 `android.binder`、slice 与线程数据；slice 累计时间可包含子调用，不能相加推导总耗时。

## 对照一：同步 Keystore 读取

两轮共用原 Debug 数据目录，Miuix、背景关闭、付款码关闭，优化构建配置和 Rust SO 一致，只改变法律同意值读取位置。

| 指标（各 10 个主页窗口） | 修改前 `opt-baseline` | 移到 IO 后 `opt-fixed` |
| --- | ---: | ---: |
| 有效帧 / 超 deadline | 259 / 24（9.27%） | 250 / 25（10.00%） |
| gfxinfo P50 / P95 | 8.219 / 49.711 ms | 9.590 / 41.438 ms |
| 最大帧 | 74.872 ms | 91.972 ms |
| 主线程 Keystore Binder | 250 次 / 261.866 ms | 100 次 / 86.824 ms |
| 主线程 >4 ms Compose 重组累计 | 399.679 ms | 201.024 ms |
| 主线程 >4 ms monitor contention 累计 | 57.511 ms | 143.868 ms |
| FrameTimeline P50 / P95 | 6.023 / 31.058 ms | 6.412 / 25.820 ms |
| 同轮正式 Release 参考 P50 / P95 | 19.396 / 85.894 ms | 18.873 / 80.632 ms |

主线程 Keystore 调用减少 60%，符合改动预期；P50、最大帧和 deadline 比例未同步改善，锁等待仍存在，不能将此项单独称为全部卡顿根因。两轮 clock 偏移为 `156023874720965`、`156023874720517 ns`，均来自各自 trace。

## 对照二：背景降级开关

先备份 Debug 的三个目标文件，再临时设置同一张确定生成的 960×2142 渐变图、遮罩 50%、`reduce_glass=true`，其余偏好不变。备份确认 `shared_prefs/home_background.xml`、`files/home_bg.jpg`、`files/home_bg_src.jpg` 原先均不存在；测试背景不包含用户照片。

修改前使用对照一的 `opt-fixed.apk`，修改后仅增加共享毛玻璃 helper 的开关判断，内置 SO 逐字节相同。两轮法律同意值 IO 修复均保留。添加背景后，修改前 P50 从无背景时的 9.590 ms 增至 17.383 ms，RenderThread 的长绘制开销明显增加，方向与现场包配置差异一致。

| 指标（各 10 个主页窗口） | 背景开关修复前 | 背景开关修复后 |
| --- | ---: | ---: |
| 有效帧 / 超 deadline | 276 / 34（12.32%） | 276 / 26（9.42%） |
| gfxinfo P50 / P95 | **17.383 / 44.687 ms** | **7.345 / 41.729 ms** |
| 最大帧 | 83.777 ms | 66.310 ms |
| 去首轮 P50 / P95，仅作敏感性分析 | 18.176 / 45.909 ms | 7.296 / 41.810 ms |
| FrameTimeline 帧数 / P50 / P95 | 279 / 13.279 / 29.174 ms | 277 / 5.714 / 21.866 ms |
| RenderThread >4 ms DrawFrames 次数 / 累计 | 212 / 1,180.136 ms | 40 / 230.766 ms |
| 主线程 >4 ms traversal 次数 / 累计 | 123 / 835.248 ms | 12 / 98.599 ms |
| 主线程 >4 ms Record View draw 次数 / 累计 | 96 / 631.118 ms | 10 / 68.344 ms |
| 主线程 >4 ms Compose 重组累计 | 202.045 ms | 200.954 ms |
| 主线程 Keystore Binder | 95 次 / 74.752 ms | 100 次 / 62.356 ms |
| 同轮正式 Release 参考 P50 / P95 | 19.045 / 81.545 ms | 20.708 / 78.127 ms |

P50 下降约 57.7%，RenderThread 长绘制累计下降约 80.4%，重组累计基本持平，符合去掉背景表面实时模糊的预测。正式参考仍更慢，且没有随测试包同步改善，支持此次收益来自目标代码变化。

两包前后 Activity 数均为 1；测试包在每轮内进程未变，准备阶段均确认 native=builtin、无下载。准备/结束时电池温度记录分别为 `33.2/34.1°C`、`35.3/34.6°C`，后轮准备后等待了数分钟，**这些不是精确测量起点温度，未严格控温**。充电状态保持；环境与调度噪声仍是限制。

两份完整轨迹大小分别为 318,495,070 和 176,256,871 字节；停止后等待进程退出、重新拉取完整文件。clock 偏移分别为 `156023874720558`、`156023874720517 ns`；两份均检查 `stats`，没有 severity 为 error 或 data_loss 的正值。修复前测试包排除 1 个 Flags 非零帧、2 个窗口外帧；修复后分别为 1、3 个。第 5 轮较高帧数窗口保留，未按结果挑选轮次。

### 本地复核命令及证据

以下脚本、APK、原始窗口、UI 脱敏摘要和 trace 均位于 Git 忽略目录 `build/pixel-home-check/`，不随源码提交。`debug` JSON 键在上述两组实验中表示使用 Debug 包名的优化 Release 测试包。

```powershell
python -X utf8 build/pixel-home-check/measure_home.py --serial $pixelSerial --phase prepare --label background-after --cold-start
Get-Content -Raw build/pixel-home-check/navigation.pbtxt | adb -s $pixelSerial shell perfetto --background-wait --txt -c - -o /data/misc/perfetto-traces/ahutong-pixel-background-after.pftrace
python -X utf8 build/pixel-home-check/measure_home.py --serial $pixelSerial --phase measure --label background-after --rounds 10 --boot-minus-mono-ns 156023874720558
# 核对 perfetto PID 后停止，待进程退出并完整 pull，再使用该份 trace 的时钟重算：
python -X utf8 build/pixel-home-check/review_samples.py --label background-after --boot-minus-mono-ns 156023874720517 --clock-source background-after-trace-snapshot
python -X utf8 build/pixel-home-check/analyze_trace.py --label background-after
```

主要证据：`opt-baseline/opt-fixed/background-before/background-after-reviewed-result.json`、相应 `*-analysis.csv` / SQL / pftrace、逐轮 framestats、准备及前后实例检查。早期工具的失效数据单独保留，不计入最终结果。

## 构建、回归及设备恢复

最终 72 项单测全部通过，无失败或跳过：校园卡恢复 9、票据解析 5、401 重试 5、抢先体验仓库 19、core:network 34。Debug 与优化 Release 构建成功；最后一次单测及 Debug 构建日志为 `build/pixel-home-check/final-tests.log`，优化构建日志为 `background-fix-build.log`。

```powershell
adb devices
./gradlew.bat :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.crawler.manager.CampusCardTokenRecoveryTest' --tests 'com.ahu.ahutong.data.crawler.manager.CampusCardCredentialTest' --tests 'com.ahu.ahutong.data.crawler.api.ycard.YcardUnauthorizedRetryTest' --tests 'com.ahu.ahutong.data.preview.EarlyAccessRepositoryTest' :core:network:testDebugUnitTest :app:assembleDebug
```

本机全局 Gradle 镜像 init 会破坏锁定 JitPack 解析。实际成功运行使用同版本原版 Gradle 9.2.0、JDK 17、独立 Gradle 用户目录、只读现有缓存、`build/payment-validation.init.gradle` 和本地临时构件镜像；临时构件 SHA-256 已核对项目 verification metadata。优化测试包额外使用 `build/home-performance-release.init.gradle` 执行 `:app:assembleRelease`。项目依赖、锁、校验 metadata、Gradle 配置及全局设置均未改；具体环境绕行记录见校园卡报告。

最终设备恢复已执行并核验：

```powershell
adb -s $pixelSerial install -r -t app/build/outputs/apk/debug/app-debug.apk
python -X utf8 build/pixel-home-check/background_fixture.py --serial $pixelSerial restore
adb -s $pixelSerial shell am start --activity-single-top -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity
```

恢复工具逐路径核验原状态，输出 `background_settings_and_files_restored_exactly=true`，日志 `pending_restore=false`。设备包再次确认 `3.4.6-debug` 且包含 `DEBUGGABLE` 标志，主页可用。最终真实数据冒烟：账单显示 11 行流水且无错误；充值账户显示数值余额、无“加载失败”；未确认充值、未跳转支付宝，随后回到主页。摘要为 `final-payment-smoke-sanitized.json`，不记录账号、余额或流水内容。

验证范围为 Pixel 的 Miuix“课表 → 主页”、有限轮次的自动导航和上述账户只读加载；尚未完成所有主题、所有底栏组合及用户原照片的严格对照，也未在真实服务主动制造凭证失效。剩余长帧、其他主线程安全存储读取、正式包 native 差异仍需独立证据，不能将本次改动称为完全消除 Release/Debug 的所有差异。手机正式 Release 未被替换。

## develop 交付

按用户要求，交付目标为官方 `upstream/develop`。提交前重新 fetch 并核对，远端 `develop`、`master` 均为 `72eb6606a626a0a4f8204e9ccc048a879708505c`，与上述已验证代码的基线一致。校园卡恢复、两处性能修复、相关测试及说明作为一个 Conventional Commit 交付；不包含本地构建配置、测试工具、账号信息、APK、原始轨迹或其他工作区的改动。
