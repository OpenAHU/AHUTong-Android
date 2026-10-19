# 抢先体验计划与更新渠道

本功能基于 `develop` 实现，不包含 Best-Bites 分支的新功能。体验名单只用于区分更新渠道，成员可以自行加入和退出，无管理员审核。

## 用户流程

- 完成必需隐私流程并登录后，在主页展示“加入抢先体验计划？”弹窗，按钮依次为“拒绝 / 再想想 / 同意”。
- “拒绝”按账号保存，后续不再主动提示；“再想想”本进程不再提示，下次启动可再次提示；“同意”在服务端登记成功后保存。
- 初次选择“同意”以前，抢先体验流程不发送学号哈希。拒绝和暂缓只保存于设备本地。
- 设置 → 应用 → 抢先体验可查看状态、加入、退出或刷新。退出需服务端删除成功，失败可重试。
- 选择按账号哈希保存于安全存储，注销后仍保留；清除全部应用数据或卸载后重置。
- 切换账号、账号过期或退出计划会使旧资格失效，并取消旧更新下载和提示。
- 退出只影响后续更新选择，已安装版本不会自动降级。

## 服务端契约

`subject = SHA256("user:" + 当前学号)`，为 64 位小写十六进制字符串。设备 ID 与游客不参与。

| 请求 | 参数 | 返回 |
| --- | --- | --- |
| GET `/api/early-access` | query `subject` | `{ "joined": true/false }` |
| POST `/api/early-access` | JSON `{ "subject": "..." }` | `{ "joined": true }` |
| DELETE `/api/early-access` | JSON `{ "subject": "..." }` | `{ "joined": false }` |

加入与退出幂等。SQLite 表 `early_access_members(subject PRIMARY KEY)` 只保存哈希。可使用 `AHUTONG_EARLY_ACCESS_DB_PATH` 将数据库放在站点目录之外；生产路径为 `/var/lib/openahu-update/early_access.sqlite3`，目录权限由服务账号独占。

## 更新选择

`GET /api/check_apk_update` 保持旧客户端兼容，缺少 subject 时只返回正式包。客户端只有本机已同意且服务端确认当前账号在名单中，才携带 subject。

会员候选为正式和抢先两条渠道，选择最高 versionCode；同版优先正式。缺少抢先文件或会员库失败时回退正式。返回原元数据字段并增加 `channel: stable/preview`。

- 正式：`static/ahutong.apk` 与现有 `apk_version.txt`、`apk_version_name.txt`、`apk_changelog.txt`、`apk_force.txt`。
- 抢先：同名文件放在 `static/preview/`。
- 两渠道包名和签名应一致，versionCode 统一递增。安装前继续执行现有 SHA-256、包名、版本和签名校验。
- 包是否处于抢先渠道由发布元数据决定，不依赖 APK 访问限制。本次未发布抢先 APK，也未改变正式包及其版本信息。

## 验证

先运行 `adb devices`：检测到一台 vivo V2121A。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.preview.EarlyAccessRepositoryTest' :data:update:testDebugUnitTest :feature:settings:testDebugUnitTest --offline --console=plain
```

Android 相关测试：19 项抢先体验状态/账号竞态测试、34 项更新模块测试、22 项设置模块测试通过。Debug 构建通过。

```powershell
python -B -m unittest discover -s server/update -v
```

后端 21 项测试在隔离本地环境及生产 Python 的临时目录中均通过，覆盖加入/退出、校验、渠道选择、同版优先、故障降级和旧接口兼容。公开 HTTPS 接口用随机临时哈希验证了加入、查询、退出，并清理测试记录；当前未配置抢先包，更新仍返回正式渠道。

真机使用保留数据的覆盖安装完成验证：暂缓后重启再次提示、拒绝后重启不再提示、设置底部入口、加入成功后重启仍能同步资格，以及确认退出后恢复“未加入 · 仅正式更新”。最新版 Debug APK 已安装并启动；测试账号最后恢复未加入，界面停留在抢先体验管理页。弹窗按钮为“拒绝 / 再想想 / 同意”，已删除用户指定的中间介绍段落。
