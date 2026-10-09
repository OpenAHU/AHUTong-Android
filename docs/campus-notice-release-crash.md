# 校园通知 Release 缓存崩溃

## 原因与影响

在 3.4.1（304001）发布提交 `a8566228` 的 USB 设备 `156497314400125` 上复现：点击校园通知后进程退出，日志为 `ClassCastException: cu4 cannot be cast to o11`。

使用该发布包的 R8 mapping 还原类型，`cu4` 是 Gson `LinkedTreeMap`，`o11` 是 `CampusNoticeSourceStatus`；`x01.d` 对应校园通知页面读取 `sourceStatuses.values` 判断加载错误的代码。三个通知缓存模型缺少完整保留规则，Release 反射解析缓存时失去嵌套类型约束；读取校验仅检查来源键，允许普通 Map 值进入界面。Debug 不启用混淆，因此不暴露此问题。

## 修复与兼容性

- 完整保留 `CampusNotice`、`CampusNoticeSnapshot` 和 `CampusNoticeSourceStatus` 的 Gson 契约，沿用已有 Signature 保留规则，保持字段名和 List/Map 元素类型。
- 在现有 `runCatching` 读取边界内校验来源状态值及其来源 ID；异常缓存不会继续交给界面。
- 旧混淆字段无法匹配的通知缓存沿用既有空快照回退逻辑，随后重新同步通知；登录、课表等其它数据不清除。
- 未调整公开接口、权限或依赖。新增两项 JVM 测试验证空通知列表中的错误状态类型，以及通知和来源状态的稳定 JSON 往返。

## 验证

构建前 `adb devices` 检测到 `156497314400125` 为 `device`。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.notice.*' :app:assembleDebug :app:assembleRelease --console=plain --max-workers=4 '-Dorg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity
```

22 项通知相关 JVM 测试全部通过，Debug 与开启 R8 的 Release 构建通过。Debug 安装返回 `Success`，启动返回 `Status: ok`。

另外对新的 Release 产物使用 SDK zipalign、apksigner 签名，沿用设备现有签名以保留数据，覆盖安装返回 `Success`，冷启动返回 `Status: ok`。在相同账号数据上进入校园通知后显示列表；下拉刷新与打开通知设置正常，进程保持运行，没有新崩溃。关闭 Wi-Fi 和移动数据、强制停止进程再冷启动，确认无默认网络时仍显示缓存通知；测试结束恢复原网络状态。新的 R8 mapping 保留三种通知模型的原类名。

## 修复发布产物

版本名称保持 `3.4.1`，版本码从服务器的 `304001` 增至 `304002`。更新说明：修复校园通知在正式版中因缓存解析异常导致的闪退。

`304002` 再次运行上述 Gradle 命令，22 项测试通过，Debug 和 Release 构建通过。正式包使用原发布证书；用于 USB 设备的 Release 包使用设备现有证书，除签名信息外 APK 内文件内容一致。设备覆盖安装后校园通知列表正常，断网冷启动显示 6 条缓存通知，结束后恢复原网络设置。

正式 APK、R8 mapping、版本与校验信息保存在本地忽略的 `build/verification/notice-hotfix-304002/`，不提交构建产物或签名凭据。上传时以版本码区分备份文件，保留旧 `304001` APK 和元数据。
