# 节假日辅助标注

## 显示行为

- 按学期实际日期匹配国家放假安排；日期栏右上角显示“休”，当周对应课程以 45% 不透明度显示，保留点击和课程详情。
- 官方调休补班日期显示“调”，课程保持正常显示；普通周末不自动标注，不推算补课或移动课程。
- 当前查看周有假期课程或调休日期时，在教务课表更新时间下提示自动标注，需要自行结合学校和教师安排判断。
- 首页完整假期说明统一显示在课程卡片轮播下方，按卡片展示的今日或明日日期标注；顶部“下节课／今日空闲”下方不重复说明。没有课程条时不显示该说明。两种桌面组件、标准课前通知及实时倒计时通知均显示相同含义的假期说明，提醒按实际课程开始日期标注。
- 教务课程、下节课筛选、提醒调度继续按原课表运行，不自动删课或关闭提醒。
- 下学期预览缺少可靠开学日期，暂不标注。
- 课表刷新按钮同时请求更新教务课表和节假日；两种数据的错误不会相互阻止。

## 纯网络数据规则

来源是 [NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn) 整理的国务院年度公告 JSON；CDN 请求失败时尝试 GitHub 原始地址。当前实现依赖这份第三方整理数据，未在客户端直接解析政府公告网页，也不能据此确认学校停课或补课。

应用不附带日历，不把日历写入磁盘，不读取旧日历缓存。升级时清除之前的 `schedule_holidays` 存储。请求成功的结果仅在当前进程内共享最多 5 分钟，而且必须仍有 Android 判定为可访问互联网的网络；进程重启后重新联网获取。

每个年度普通请求共享未过期的在线结果；手动刷新尝试重新获取，最小请求间隔 30 秒，失败后的重试间隔 60 秒。主动重新验证开始时撤下该年度旧记录，失败或取消时不回退。缺失或未公布的年份保持无标注，不用其它年份推算。需要十二月日期时也请求下一年，支持公告中的跨年日期。

断网会清空在线状态，前台页面随之撤下标注；到期状态每 30 秒检查一次。活跃的首页和课表每 5 分钟重新查询。桌面组件在状态变化时请求重绘；如果应用进程已经被系统杀死，桌面可能保留最后一次画面，直到现有周期更新或用户刷新。那次更新若无法获取日历，就绘制无假期说明的原课表。通知中的日历说明是发送时的提示快照；合入 develop 后继续采用其系统倒计时，SystemUI 更新时间数字期间不重复查询日历。

后台组件和提醒共用总计 2 秒的查询预算，包括等待其它请求、跨年请求和备用地址，超时后继续原有显示或通知。OkHttp 在读取响应正文期间仍可取消，避免正文阻塞拖延通知。前台 HTTP 单次调用超时 8 秒。

校验公告列表非空、年份匹配、有效日期、布尔放假标志、无重复日期、名称长度及 128 KiB 响应上限；这些校验用于拒绝格式错误和占位数据，不能证明第三方数据永远正确。

## 接口与依赖影响

`:data:schedule` 的 `ScheduleHolidaySource` 公开当前有效日历的 `StateFlow`，前台与后台统一观察撤销状态。该模块公开依赖已有版本的 `kotlinx-coroutines-core:1.10.2`，锁文件同步解析配置，未新增权限或修改其它依赖版本。网络实现位于 `:app`，通过 Hilt 提供给课表和 `:background`。

## 验证

相关 JVM 测试覆盖放假与调休区分、日期范围、手动同时刷新、在线共享与节流、离线无回退、到期撤销、失败与取消撤销、断网后的迟到响应、跨年缺失数据、后台共享超时、实际课程日期和通知原文保留。

构建前检查设备：`adb devices`，设备 `156497314400125` 为 `device`。

```powershell
.\gradlew.bat :data:schedule:testDebugUnitTest :feature:schedule:testDebugUnitTest :background:testDebugUnitTest :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.calendar.*' :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity
```

结果：65 项相关 JVM 测试通过，Debug 构建通过；额外解析 `:data:schedule:releaseCompileClasspath` 成功。初次构建发现新增公开协程依赖缺少锁记录，已更新对应配置；后续构建无需更新锁文件即可成功。HTTP 测试使用模拟服务器验证完整正文、分块响应大小限制和收到响应头后正文阻塞时仍可取消。

安装返回 `Success`，冷启动返回 `Status: ok`。真机确认首页显示当日国庆说明和课程条“休”，关闭 Wi-Fi 和移动数据后两者自动撤下而课程保留；测试结束已恢复此前两项网络状态，联网进入课表后标注恢复，点击刷新后显示正常。检查最终 APK 的 ZIP 条目，`assets/holidays/` 下文件数为 0。两种 Debug 组件未在该设备桌面绑定，本轮未手动触发课前通知；后台验证范围是编译和相关 JVM 测试，不宣称已做组件或通知外观真机验证。

工作基线 `ba9f6ab1` 已存在两处模块边界违规，之前检查已记录，本次不增加豁免或修改无关文件：`R11-designsystem-leaf`（`AppTitleIconButton.kt`）和 `R24-xuexiaotong-feature-no-app-internals`（`WorkWebViewDialog.kt`）。

早期功能分支再次运行 `:app:testDebugUnitTest --tests 'com.ahu.ahutong.architecture.ModuleBoundaryTest'`，两项检查中一项失败，报告仅包含这两处既有违规；上述文件相对早期工作基线没有修改。

## 合入 develop 的验证

基于 `d8f4c47a` 合并功能分支，保留 develop 新的提醒投递状态、到期判断、系统倒计时、研究生课表参数及刷新无变化时保留当前课表的行为。节假日查询结束后再次检查课程是否已开始，避免查询延迟导致过期通知。研究生课表按实际周数生成完整日期，刷新按钮同时请求日历。

重新执行上述 Gradle 命令：88 项相关 JVM 测试全部通过，Debug 构建通过。额外执行模块边界检查，两项检查全部通过；最新 develop 已修复早期基线中的两处违规。

构建前 `adb devices` 仍检测到 `156497314400125` 为 `device`。重新安装返回 `Success`，冷启动返回 `Status: ok`；真机确认首页自动假期说明与课表放假日期说明仍正常显示。原有组件与通知外观验证范围不变；本轮未使用研究生真实账号进行手动验证。

## 首页提醒去重验证

基于 `ec4848b8` 调整首页说明位置。构建前 `adb devices` 检测到设备 `156497314400125` 可用；执行 `.\gradlew.bat :app:assembleDebug --console=plain` 成功，`adb install -r app/build/outputs/apk/debug/app-debug.apk` 返回 `Success`，使用 `adb shell am start -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity` 启动成功。

真机通过现有 Debug 时间模拟验证 `2026-10-05 09:00` 的今日课程与 `2026-10-05 21:00` 的明日课程：首页各只有一条完整自动标注说明，位于课程卡片下方，顶部不再重复，“休”标签保留。另确认无课程条时没有完整说明。验证后已关闭时间模拟并恢复系统时间。
