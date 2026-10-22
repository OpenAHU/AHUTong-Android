# 课程表与主页审查（2026-10-22）

基线：`upstream/develop` `7f7d9c53`。工作分支：`p/MuxYang/fix/session-persistence-review`。

本项覆盖本科课表协议入口和学期目录、GMIS 解析与适配、课程周次/冲突展示、课表 ViewModel、主页课程条及一级导航调用链。认证、成绩、设置和电控由并行任务审查。本报告不把静态检查当作真实账号、真机或性能验证。

## 已修复的确定问题

### 1. 研究生主页使用本科课表缓存（P1）

- 原 `Home.kt` 已从研究生快照生成 `graduateGrid`，但今日课程和课程条始终读取 `scheduleViewModel.schedule`，研究生分支只是对这份本科数据调用研究生筛选器。因此研究生主页可能显示为空，或筛出遗留本科缓存中的课程。
- `selectHomeCourses` 直接承担主页实际使用的数据源选择、今日课程和明日课程计算。研究生选择 `graduateGrid.courses`，本科选择原本科缓存。
- `HomeCourseSelectionTest.graduateHomeUsesTheGraduateTimetableEvenWhenUndergraduateCacheExists` 同时提供不同的本科缓存和研究生课表，断言实际显示的来源。

涉及文件：

- `app/src/main/java/com/ahu/ahutong/ui/screen/main/Home.kt`
- `app/src/main/java/com/ahu/ahutong/ui/screen/main/home/HomeCourseSelection.kt`
- `app/src/test/java/com/ahu/ahutong/ui/screen/main/home/HomeCourseSelectionTest.kt`

### 2. 明日课程把未排课的同奇偶周当成有课（P2）

- 原主页明日筛选在 `nextWeek` 不属于精确 `weekIndexes` 时，仍根据 `nextWeek % 2 == startWeek % 2` 接受课程。例如实际排课为第 1、5 周的课程，会在第 3 周的明日课程中错误出现。
- 今日、明日均只接受服务端或自定义课程提供的精确周次。周日的明日使用下一教学周。配置缺失或学期外不显示兜底的周一课程。
- 新增回归测试覆盖不连续周次、周日跨周、配置未就绪及学期外；测试运行结果由统一验证记录补充。

### 3. 课表旧异步结果可以覆盖当前账号或清理后的状态（P1）

- 历史课表预览原本已有 identity 和 revision 检查，但普通 `refreshSchedule()`、后台 `refreshLatestSchedule()` 和 `loadConfig()` 在等待返回后直接发布结果。
- 可证实的触发顺序是：账号 A 发起刷新，切换至账号 B 并显示 B 的状态，A 的结果随后返回。原代码会发布 A 的课程、学期配置和刷新时间，并重排提醒。`clear()` 也没有使未跟踪的普通请求失效。同账号两个普通请求乱序返回时，旧请求同样会覆盖新结果。
- 为这三条路径增加请求开始时的账号/mock identity 及递增 revision 校验；`clear()` 使旧请求失效，并取消配置加载。后台请求的去重按 identity 执行，新账号不再被旧账号的请求阻塞；旧请求的 `finally` 不会清掉新请求的加载状态。
- 普通加载和后台刷新共享课程发布 revision，启动时的旧普通请求也不能覆盖后发的后台刷新。
- 新增 6 个 ViewModel 回归测试：跨账号后台回包、clear 后普通回包、跨账号远端配置、旧请求等待时新账号仍可刷新、同账号普通请求乱序返回、启动普通请求晚于新后台刷新返回。
- 这些保护位于 UI 发布层。Repository/协议缓存写入是否也满足账号隔离，由本次认证审查统一处理；不以 UI 层防护代替缓存层隔离。

涉及文件：

- `feature/schedule/src/main/java/com/ahu/ahutong/ui/state/ScheduleViewModel.kt`
- `feature/schedule/src/test/java/com/ahu/ahutong/testing/ScheduleFakes.kt`
- `feature/schedule/src/test/java/com/ahu/ahutong/ui/state/ScheduleViewModelTest.kt`

## 已检查但未认定为新缺陷的部分

- `MainActivity.checkDayRollover()` 已在 `onResume()` 和前台每分钟守望中检测日期变化并重新 `loadConfig()`。因此不能仅凭 Home 内部 ticker 不写周次，就断言应用缺少跨日刷新。本次保留这条现有保护。
- 本科历史学期目录使用服务端真实 ID，支持页面下拉、脚本目录和培养方案成绩响应补充；历史请求、下一学期请求与当前课表状态分开。GMIS 解析保留精确周次、动态节次和无法放入网格的活动。未拿到新的校方失败响应时，不改猜测性的协议字段。
- `weeklyCourseGroups` 在按周次筛选之后分组；总览冲突标记要求课程时间和精确周次同时重叠，已有测试覆盖单双周互斥。未发现应据本次用户描述直接重写的分组逻辑。
- 主导航 `selectPrimaryDestination()` 的 `navigate()` 前没有等待网络返回；一级页面转场在 `utils/Navigation.kt` 中固定为 220 ms。主页进入后读取缓存，课表页先读缓存再启动后台刷新。不能据此宣称真实设备上的切页耗时已解决。

## 仍需真实环境验证

- 主页切换耗时：需要按主题及“减少玻璃效果”设置对比帧时间/Perfetto，分开观察首次组合、玻璃绘制、缓存读取和后台刷新。主线程仍有部分 MMKV/布局偏好读取；这是待测候选，尚不是已确认 ANR 根因。本轮 Sentry 未提供可归因课程/导航的调用栈；主线程处于 nativePollOnce/epoll 等待的 ANR 样本不能证明导航阻塞。
- 研究生账号真实课表、用户所报课程解析失败的原始响应、窄屏/大字体下的网格和课程详情，尚需设备验证。
- `CrawlerDataSource.getNextSchedule()` 保留既有 `currentSemester.id + 20` 的下学期预览约定；历史目录使用真实 ID 并不能单独证明这个预览约定对所有账号有效。未获得失败响应或服务端约定前，本次未擅自改变它。

## 验证状态

- 已执行本项工作区 `git diff --check`，未发现本项修改的空白错误。
- 本项没有独立运行 Gradle，遵循并行任务统一构建的安排，避免共享工作目录的并发构建。新增 10 个回归测试，交由主任务运行：

```text
adb devices -l
./gradlew.bat :feature:schedule:testDebugUnitTest :app:testDebugUnitTest --tests com.ahu.ahutong.ui.screen.main.home.HomeCourseSelectionTest --console=plain
```

- 主任务已检测到 `emulator-5554`，首次统一构建遇到基线已有 JitPack 依赖解析阻塞。本文写入时尚未得到最终统一构建、测试、安装或启动结果；不能标记为已验证。最终命令、设备状态和结果见主任务统一验证记录。

## 初次审查统一验证

初次审查在 `7f7d9c53` 基线上通过 1,083 项单元测试、Debug / AndroidTest 构建及 4 项设备测试；随后合入最新 develop 和子页返回改动的结果见总报告。各子项的实际验证范围与剩余限制，以[总审查与验证记录](2026-10-22-session-codebase-review.md)为准；本文前述未验证状态指并行子任务交付时。
