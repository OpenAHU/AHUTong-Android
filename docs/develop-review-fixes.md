# develop 审查修复：账号隔离、课表重叠与更新降级

基线：`origin/develop` 的 `b15fb733`。分支：`p/ka1/fix/develop-review-regressions`。

## 登录后的账号状态隔离

普通“重新登录”成功后曾直接返回原 MainActivity 的主页，只清理课表 ViewModel。
DiscoveryViewModel 中上一账号的余额、过渡余额和浴室状态仍存在；新账号一卡通请求失败时会继续显示旧值。

所有成功登录入口现在共用 `Activity.restartMainAfterLogin()`，通过新任务与清空任务栈创建 MainActivity，并结束旧宿主。
新的 Activity 从已持久化身份初始化业务数据，旧 Activity 的 ViewModelStore 被清理，其 viewModelScope 也随之取消。
保留原来的登录、取消返回、认证门禁与凭据保存流程。

新增设备回归 `LoginActivityRecreationTest`，在旧 ViewModel 内设置测试余额和浴室状态，
验证重建后的 Activity 与 ViewModel 均为新实例，状态未继承，旧协程作用域已取消。
该测试不读取账号密码，也不执行真实账号切换；它验证两条登录入口共用的生命周期处理。

## 总览课表的重叠课程

总览原先只合并星期、开始节次和长度完全相同的课程，包含关系或部分重叠仍可能遮挡课程。
现在普通周与总览共用同日时间区间的连通分组；总览包含全部周次，组内每门课程保留独立详情入口。
真正的冲突标记仍要求教学周相交，单双周互斥课程只共享显示区域，不误标时间冲突。

回归覆盖包含关系 1–2 / 1–3、部分与传递重叠、相邻节次、不同日期、单双周互斥和共享教学周。

## 抢先更新失败后的正式渠道回退

原先只在候选发现阶段跳过损坏的版本码；选中抢先包后读取其他信息失败会直接返回 500。
现在按版本码降序、同版正式优先依次完整构建候选，包含元数据读取、APK 哈希与签名。
当前候选失败后尝试下一条符合账号资格的渠道，并记录失败日志；普通账号仍只有正式候选。
没有发现包仍返回 404，发现的候选全部构建失败仍返回 500，可选文件缺失时继续沿用默认值。

## 验证

构建前 `adb devices` 检测到一台 vivo V2121A。

```powershell
.\gradlew.bat :feature:schedule:testDebugUnitTest :app:testDebugUnitTest --tests '*LoginNavigationPolicyTest' --tests '*SavedAccountStoreTest' --tests '*AhuSessionContractTest' --tests '*SessionExpiryHookContractTest' :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
```

结果：44 项课表测试、28 项登录和会话测试全部通过；Debug APK 与设备测试 APK 构建成功。
覆盖安装尝试被手机系统拒绝，返回 `INSTALL_FAILED_ABORTED: User rejected permissions`。
随后用户明确选择“先保留修复，不安装”，因此没有继续安装或运行新增设备测试。
设备回归尚未执行，未对真实凭据登录或真机总览布局作已验证声明。

后端执行 `python -B -m unittest discover -s server/update -v`，28 项测试全部通过。
新增回归先在旧实现复现失败，再验证元数据损坏、APK 无法读取、签名失败后的完整降级，
并覆盖不向非体验账号提供抢先包、跳过未选中损坏包及所有候选失败的原有错误响应。
本次推送源码，服务端修复需部署后生效。
