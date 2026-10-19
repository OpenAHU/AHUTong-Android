# 课表冲突展示与学期浏览

## 开发基线

本次工作位于独立工作目录的 `p/ka1/feat/schedule-semester-browser` 分支，基线为
`origin/develop` 的 `f73eb017`（已包含账号切换及小组件选择器修复）。
原工作目录使用的 `ba9f6ab1` 是 develop 的旧祖先；本次首次同步时落后 59 个提交。
其他功能的未提交文件未迁入此工作目录。

## 用户可见行为

- 普通周课表先筛选本周课程，再按同一天的重叠节次分组，像总览一样上下平分区域。
  两门、三门同节次以及部分重叠均保留各自点击入口，组右上角显示小字“冲突”。
  空间足够时，部分重叠课程显示自己的节次范围；小分块优先保留课程名，
  其余信息通过点击详情查看。单双周互斥的课程不会误标冲突。
- 原“预览下学期课表”开关改为“浏览学期课表”下拉选单，保留当前学期、
  下学期预览，并加入教务目录提供的历史学期。
- 每个历史学期在 ViewModel 内独立缓存；刷新绕过该缓存，快速切换取消旧请求。
  退出预览取消请求；账号变化或退出登录会清理账号相关预览缓存。
- 历史预览不修改当前学期键、首页使用的当前课表或提醒调度。
  预览按自己的开学日期显示日期；没有可靠开学日期时只显示星期，不套用当前学期日期。
- 学期列表、课表的加载中、加载失败及空课表有对应文案和刷新入口。
  研究生原有 GMIS 学期选择保留。

## 接口与影响范围

已有课表接口为：

`GET /student/for-std/course-table/semester/{id}/print-data?semesterId=...`

课表入口 `GET /student/for-std/course-table` 原先只解析 `currentSemester`。
本次扩展其学期下拉及脚本目录解析，使用服务端真实 ID，不按年份猜算历史 ID。

成绩页的现有全量接口为：

`GET /student/for-std/grade/sheet/info/{studentProfileId}`

其 `GradeResponse.semesterId2studentGrades` 按真实学期 ID 分组，
每个成绩条目也带 `semesterId` 与 `semesterName`。
历史目录合并所有已知学生档案（含主修、微专业等）的原始成绩响应，
而不是只读取经 `GradeMapper` 转换后的列表；后者会丢失没有课程条目的学期键。
课表页中实际提供的学期及开学日期也会合并，空成绩列表的学期键保留。

数据入口遵循项目已有链路：`ScheduleSource → RepositoryScheduleSource →
AHURepository → BaseDataSource`，SDK 当前委托教务 Crawler 完成目录解析。
共享教务协议层复用学生档案身份解析，逐一读取各培养方案的原始响应，
仅向课表功能返回 `ScheduleSemester` 元数据。页面与 ViewModel 不依赖成绩或
评教的 Repository、Mapper、ViewModel，也不读取或修改成绩业务缓存。
学生档案 ID 沿用已有账号级身份缓存管理。

历史目录覆盖实际响应返回的学期 ID，未出成绩学期的完整性不能仅凭成绩目录保证。日期来自课表页实际提供的 startDate；
没有按学期 ID 单独查询日期的接口，也不保证任意学期都会带日期。

历史请求沿用已有下学期请求的参数关系：路径传所选学期 ID，查询参数传当前学期 ID。
已在 USB 设备的现有登录态中验证历史学期列表和 2025-2026-2 课表加载，
显示不同课程及对应日期；这不代表所有账号、所有学期的日期均有覆盖。

公开数据端口 `ScheduleSource` 增加学期目录及指定学期读取方法，
`BaseDataSource`、Repository、SDK 委托和 Debug/Release mock 实现同步适配。
没有改动权限、依赖版本或 Gradle 配置。

## Debug 与验证状态

Debug 新增“课表冲突”场景：周一两门、周二三门、周三部分重叠，周四单双周互斥。
历史学期 mock 提供不同学期的课程和空课表，便于后续直接切换观察。

已补充冲突分组、历史请求隔离、切换竞态、缓存刷新与目录解析的测试。
先前基线验证通过 50 项测试：课表模块 41 项，学期解析及协议契约 9 项。
2026-10-14 已通过 `:app:assembleDebug` 并安装启动到 USB V2121A
设备。设备 mock 已验证两门/三门/部分重叠、独立详情入口、
单双周互斥，以及历史学期切换和空课表；学期状态已移到课表上方。

合并前在最新 develop 基线上执行：

```text
adb devices -l
./gradlew.bat :feature:schedule:testDebugUnitTest :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.crawler.ScheduleSemester*' :app:assembleDebug :app:compileReleaseKotlin --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity
```

最终验证结果：50 项测试全部通过；Debug 构建、Release Kotlin 编译均成功；USB V2121A 安装返回 Success，应用启动成功。构建有已有弃用警告，无编译错误。
