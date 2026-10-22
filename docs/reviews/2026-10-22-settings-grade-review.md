# 设置与成绩模块审查（2026-10-22）

基线：上游 `develop`，`7f7d9c53`。范围：`:feature:settings`、`:feature:grade`；不改账户切换或体验计划协议，也不操作生产发布。

## 已修改

- 设置页保留“切换账号”“抢先体验”入口，移除两行的下方提示；体验计划的“已加入 / 未加入”使用已有 `SettingsActionRow.value`，显示在右箭头左侧，继承主题的灰色次要文字颜色。
- “切换账号”使用 Miuix `Useful.Refresh`、Material `Outlined.SwitchAccount`；Radiant 使用同现有资源风格的 IconPark `UserToUserTransmission`。抢先体验使用 Material `Outlined.Science`，Radiant 使用 IconPark `Experiment`。图标沿用现有组件的主题着色，不引入依赖。已检查锁定的 Miuix 0.7.2 官方 AAR/源码，没有假设新版文档中的图标 API 在旧版存在。
- 成绩页采用 `AppPageScaffold.lazyContent`，其契约不自动添加水平留白。原成绩项已有 16 dp 边距，汇总卡、加载态和空态缺失。现为这些内容补齐 16 dp，汇总区内的专业选择和常用条件不再重复加边距。
- 成绩列表原先继承 Scaffold 默认 24 dp 间距，现显式设为 8 dp；单项卡片纵向内边距由 16 dp 调为 12 dp、文字间距由 8 dp 调为 6 dp。
- 发现并修复跨专业排名竞态：旧专业请求进行中切换专业，原 `rankLoading` 检查阻止新专业请求，旧结果随后显示到新专业。现在后一次排名请求取消前一次，请求序号限制过期回写，并透传 `CancellationException`；旧任务结束不会提前清除新任务的加载状态。
- “使用常用条件”预设也可能切换专业，现复用相同的专业切换逻辑，取消旧请求、清理旧排名、拉取新专业排名；保留预设只提交一次的行为。

## 验证与边界

- 新增 ViewModel 契约测试 `switching profile cancels the old rank request and loads the selected rank`，通过挂起的两个专业请求验证切换后加载新排名、忽略旧请求、仅保存当前专业排名及保持正确加载状态。
- 新增 `a preset profile switch also discards the previous profile rank`，覆盖预设切换入口和只提交一次预设的契约。
- 两个新增 VectorDrawable 已通过 XML 解析校验；`git diff --check` 通过。
- 按并行协作分工，Gradle、ADB 和真机验证由主代理统一执行；本报告不将新增测试、主题渲染或真机行为标记为已验证。布局仍需在 Material、Miuix、Radiant 和不同字体比例下目视核对。
- 用户提供的测试账号和密码未写入代码或本报告。

## 追加诊断：成绩已加载但汇总暂时显示“暂无”

主代理在测试设备上取得真实请求结果：排名页面 HTTP 200，存在 `gpaSemesterModel`，未跳登录页，解析成功，包含总体绩点、专业排名及学期记录；同一页面等待稳定后，五项汇总均有值。本次记录不包含真实绩点、排名、成绩或账号信息。

可确认的区别是：本学期平均绩点来自已返回成绩列表的本地加权结果；全程绩点、专业排名及更新时间来自随后独立发起的排名 HTML 请求。此前 UI 没有展示 `rankLoading`，请求与 Compose 重绘尚未完成时直接把空状态显示为“暂无”。不能把首次截图里的占位解释为接口没有排名或解析失败。

这次增量修改：

- 成绩或排名正在初次加载时显示进度与“绩点及排名加载中”，依赖排名的字段显示“加载中”。更新既有排名时保留数值并显示更新状态。
- 请求失败原本统一提示“暂无排名信息”，现显示网络、超时等具体可读错误；保留已加载的同专业排名，避免刷新失败把已有数据清空。
- 不修改排名 API、HTML 解析或总体绩点计算公式。真实稳定页面已有正确数据。
- 延迟回归进一步暴露额外传播问题：原先 `totalGradePointAverage` 通过 `snapshotFlow(gpaRankInfo)` 再写一次独立状态，排名数据已到但派生文字仍可能短暂为“暂无”。现改为从 `gpaRankInfo` 直接计算的只读属性，消除重复状态和对额外 Snapshot 通知的依赖。
- 新增可控挂起请求回归 `grades can finish before the independent rank request and keep its loading state`，验证成绩先到、排名仍加载、排名成功后状态更新；新增超时保留排名回归，并将原失败用例改为断言错误提示。

本次增量的静态 `git diff --check` 通过，Gradle 和设备增量验证继续由主代理统一执行。

## 图标来源

- [Miuix 0.7.2 官方 Maven AAR](https://repo.maven.apache.org/maven2/top/yukonga/miuix/kmp/miuix-android/0.7.2/miuix-android-0.7.2.aar)，只用于核对当前依赖提供的类。
- [IconPark UserToUserTransmission](https://github.com/bytedance/IconPark/blob/master/packages/react/src/icons/UserToUserTransmission.tsx)、[IconPark Experiment](https://github.com/bytedance/IconPark/blob/master/packages/react/src/icons/Experiment.tsx)，Apache-2.0，以现有资源相同的 48 × 48 视口、4 px 描边转换为 Android vector。

## 初次审查统一验证

初次审查在 `7f7d9c53` 基线上通过 1,083 项单元测试、Debug / AndroidTest 构建及 4 项设备测试；随后合入最新 develop 和子页返回改动的结果见总报告。各子项的实际验证范围与剩余限制，以[总审查与验证记录](2026-10-22-session-codebase-review.md)为准；本文前述未验证状态指并行子任务交付时。
