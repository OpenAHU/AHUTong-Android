# 多专业考试查询验证

日期：2026-11-07；基线：`origin/develop`（`0f88caf0`）。

## 行为与接口影响

- 从考试入口解析全部专业档案，逐个请求 `/student/for-std/exam-arrange/info/{id}`，全部成功后发布结果。
- `Exam.studentProfile` 保存考试所属档案；复用 `GradeStudentProfile` 的专业名称和培养类型。
- 考试页同时展示各专业，分别排序、折叠已结束考试；搜索覆盖所有专业。空专业保留提示。
- 专业元数据按账号缓存，包含没有考试的专业；旧考试缓存的时间戳失效以触发升级后的首次完整查询。
- 任一专业请求失败时保留已有结果并提示失败；取消协程继续传播。移除无法保证多专业完整性的旧原生考试恢复路径。
- 新字段位于已有模型 keep 规则覆盖范围内。

## 构建与自动化测试

每次构建前运行 SDK 的 `adb devices -l`，检测到一台 USB 设备和一台本地连接设备。安装与手工测试指定 USB 设备 V2121A。

实际成功的 Gradle 任务：

```powershell
.\gradlew.bat -I "$env:TEMP/ahutong-build-repositories.gradle" `
  '-Pkotlin.daemon.jvmargs=-Xmx4096m' --max-workers=2 `
  :feature:grade:testDebugUnitTest `
  :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.crawler.CrawlerDataSourceContractTest' `
  :app:assembleRelease --console=plain
```

使用 JDK 24。Google 仓库连接不稳定，临时初始化脚本将 Google Maven 指向阿里云镜像并保留 JitPack 独占规则。默认 Kotlin 编译器内存不足，成功命令通过参数增加到 4 GiB、限制两个 worker。

结果：`BUILD SUCCESSFUL`；feature:grade 的 30 项测试与爬虫响应契约的 10 项测试全部通过；Release Lint、R8 与资源压缩完成。

新增用例覆盖：全部档案查询及各自归属、同名考试保留、空专业元数据、部分请求失败、登录失效、专业变化触发内容更新，以及缓存恢复时保留空专业。

## 正式包和 USB 验证

使用 Build Tools 37.0.0 执行 zipalign、PKCS12 签名和 apksigner verify。签名校验通过，`adb -s <USB设备> install -r` 返回 `Success`，通过 `am start -n com.ahu.ahutong/.MainActivity` 启动。

产物：`app/build/outputs/apk/release/app-release.apk`。

安装版本：Release 构建，`3.4.9-beta1` / `304016`，沿用 develop 版本信息。

手工验证结果：

- 第一组测试账号登录成功，单专业考试的展示和刷新正常。
- 第二组测试账号登录成功，自动展示主修 46 条考试（其中 45 条已结束）与微专业 1 条考试，与 HAR 一致。
- 专业标题、考试时间、地点和座位正确，未出现专业间混合或丢失。
- 搜索 `FPGA` 只显示对应微专业考试，关闭搜索恢复全部分组。
- 手动刷新后两个专业的内容保持正确。
- 强制停止后重新启动，再次进入考试页，两个专业、已结束数量和座位信息正常恢复。

凭据、HAR、签名文件和构建产物未加入本次源码改动。

## 合入 develop 前的集成验证

同步 `origin/develop` 至 `dfc5252a`（工具列表与页面底部布局更新），考试功能源码无文件冲突。

再次运行 `adb devices -l`，USB 设备 V2121A 状态为 `device`。在同一 JDK、临时镜像和内存参数下运行上述 40 项测试，并将构建目标替换为 `:app:assembleDebug`，结果为 `BUILD SUCCESSFUL`，40 项测试全部通过。

通过 `adb -s <USB设备> install -r app/build/outputs/apk/debug/app-debug.apk` 安装 Debug 包，返回 `Success`；启动 `com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity`，应用进程正常运行并显示首次启动的隐私政策页面。随后返回已验证的正式版应用。
