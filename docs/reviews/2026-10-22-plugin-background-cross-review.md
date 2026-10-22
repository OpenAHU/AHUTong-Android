# 插件、后台、更新及交叉审查（2026-10-22）

基线：`upstream/develop` `7f7d9c53`。在课程/主页审查之后，补充检查本轮电控、设置和成绩修改，以及后台提醒、更新下载和运行期插件的主要调用链。

## 已修复：运行期插件找不到 boxed TextUnit（P1）

证据来自 [Sentry AHUTONG-ANDROID-1J](https://openahu.sentry.io/issues/7804914594/)：`3.4.5+304009` 的 calc 插件 `plugin.apk` 抛出 `NoClassDefFoundError: androidx.compose.ui.unit.TextUnit`，原因为 `ClassNotFoundException`，经过 `PluginClassLoader.kt:36` 的宿主回退和 Material3 文本样式调用链。

现有 `PluginClassLoader` 对非契约/系统类使用 child-first，插件缺少依赖时回退到宿主。`app/proguard-rules.pro` 按特定插件引用面保留宿主共享 ABI，其中有 `TextUnitKt`，却没有 `TextUnit` 本身。Kotlin 的内联值类仍会在泛型/装箱等场景中引用 boxed class；保留顶层函数容器不能保住盒装类型的原名。

仅新增两条精确保留规则：

```proguard
-keep class androidx.compose.ui.unit.TextUnit { *; }
-keep class androidx.compose.ui.unit.TextUnit$Companion { *; }
```

不改变类加载顺序，不对 Compose 或 Kotlin 整包 keep。

### 使用真实 Compose 字节码验证

新脚本 `scripts/verify_plugin_text_unit_abi.py` 读取生产规则中 `androidx.compose.ui.unit` 的精确 keep 项，使用真实 Compose `ui-unit-android:1.9.4` 的 `classes.jar` 运行独立 R8 classfile 缩减与混淆，然后模拟外部插件按原名加载类并调用 `box-impl`、`unbox-impl` 和 `Companion.getUnspecified-XSAIIZE`。基线只移除本次两条规则，其他输入相同。

本机实际命令：

```powershell
python scripts/verify_plugin_text_unit_abi.py --ui-unit-aar C:/Users/MuxYang/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-unit-android/1.9.4/578f8eca8ce0382b139ea10e21ab9c787ac8b553/ui-unit.aar --kotlin-stdlib C:/Users/MuxYang/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.2.21/fa374a986e128314c3db00a20aae55f72a258511/kotlin-stdlib-2.2.21.jar --r8-jar D:/Coding/AndroidSDK/build-tools/36.1.0/lib/d8.jar
```

SDK 工具报告 R8 `9.0.3-dev`。修改规则前，脚本基线和当前规则均因 `ClassNotFoundException: androidx.compose.ui.unit.TextUnit` 失败，退出码 1。增加规则后相同命令退出码 0：

```text
baseline: reproduced ClassNotFoundException: androidx.compose.ui.unit.TextUnit
fixed: TextUnit plugin ABI OK
```

这验证了实际 boxed TextUnit ABI 在本次最小 R8 输入中的保留行为。完整宿主 Release 的 R8 输出、实际 calc 插件包、设备中的插件渲染仍需验证；没有根据这个测试宣称所有插件兼容性均已解决。

## 已修复：卸载插件误删同前缀插件的偏好数据（P2）

真实命名来自 `PluginContextWrapper.getSharedPreferences()`：`plugin_<id>__<name>`；`SharedPrefsPluginStorage` 使用 `name=storage`。兼容的旧版文件为 `plugin_<id>.xml`。

原 `cleanupPluginData()` 使用 `startsWith("plugin_$pluginId")` 选取文件。卸载 `foo` 时，`plugin_foobar.xml` 和 `plugin_foobar__storage.xml` 都会被选中并删除。可用内存中的上述四种文件名直接稳定复现该筛选错误。

修复为旧名精确匹配、新名双下划线边界匹配，去重处理 `.xml` 和 `.xml.bak`，并调用 `Context.deleteSharedPreferences()`，同时清理 Android 的进程内缓存及备份文件。二进制目录继续只删除 `ahup_plugins/<id>`。

新增 `PluginDataCleanupTest` 两条测试覆盖真实 `cleanupPluginData()` 调用，使用临时目录和覆写平台删除 API 的 Context fake：

- 清理 foo 的新旧偏好及私有文件，保留 foobar 的偏好、私有文件和其他文件。
- 处理只剩备份的旧名、对同一偏好文件名只调用一次平台 API，保留其他前缀。

历史限制：插件 ID 允许下划线，含双下划线的 ID 与任意自定义偏好名仍有旧命名空间歧义，例如 `foo` 的 `bar__storage` 与 `foo__bar` 的 `storage`。本次未迁移已有命名空间或改变插件 ID 契约。

涉及文件：

- `app/src/main/java/com/ahu/ahutong/ui/plugin/AhupGate.kt`
- `app/src/test/java/com/ahu/ahutong/ui/plugin/PluginDataCleanupTest.kt`
- `app/proguard-rules.pro`
- `scripts/verify_plugin_text_unit_abi.py`

## 交叉审查结果

- 电控选项/房间请求的取消异常现在重新抛出，旧请求的 `finally` 不再清除新请求的 loading。生产适配器的 `options()` 和 `room()` 都有 `withContext(Dispatchers.IO)`，可在返回 UI dispatcher 时执行及时取消检查；本轮未找到可确定击穿这些保护的真实生产路径。
- 设置中的抢先体验状态使用现有 `SettingsActionRow.value`，组件把 value 放在右箭头左侧，并使用主题的次要文字色，未另造布局。
- 成绩排名修复中的 `selectProfile()` 路径具有取消和 sequence 保护。发现 `applyPresetCandidate()` 直接修改 profile 而不刷新/取消排名的遗漏，已交由成绩子任务补齐；最终结果以其代码和统一测试为准。
- 后台课程提醒会在投递前重新读账号、设置和缓存课表，排程命令也串行化；APK 更新下载有取消、长度、SHA-256、镜像域名/重定向及安装前包名/版本/签名校验。本轮快速审查未得到新的高把握、可复现的高影响缺陷，因此未据覆盖数量增加推测性修改。

本项未独立运行 Gradle；插件清理 JVM 测试、完整 Debug 构建及设备安装启动交主任务统一执行。上述真实 R8 小测试已执行并通过；它与完整 Android 运行验证分别记录。

## 初次审查统一验证

初次审查在 `7f7d9c53` 基线上通过 1,083 项单元测试、Debug / AndroidTest 构建及 4 项设备测试；随后合入最新 develop 和子页返回改动的结果见总报告。各子项的实际验证范围与剩余限制，以[总审查与验证记录](2026-10-22-session-codebase-review.md)为准；本文前述未验证状态指并行子任务交付时。
