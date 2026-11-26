# AHUTongCLI

通过已授权 ADB 调用本分支 Debug App 的命令行客户端。使用 Python 3.8+ 标准库，支持 Windows、macOS、Linux；不要求模型或 AI Key 才能运行普通业务命令。

这是 **Android companion 模式**：业务使用手机当前账号与既有仓储实现；相机、系统权限、第三方应用等功能通过明确标注的 Android 页面入口执行。它不是脱离手机的全功能桌面移植。

## 安装

1. 用本分支构建、安装并启动 Debug APK，完成 App 必要的协议同意与登录。正式 APK 不包含 CLI provider。
2. 安装 Android platform-tools，开启手机 USB 调试，授权这台电脑。
3. 在 Android 仓库根目录运行 `python cli/ahutong_cli.py help`；也可以用 `python -m pip install ./cli` 安装 `ahu` 和 `AHUTongCLI` 命令。

```sh
ahu --serial DEVICE status --json
ahu catalog --json
ahu pages --json
ahu features --json
ahu schedule --days 7 --refresh
ahu grades --refresh
ahu schedule-raw --json
ahu gmis-schedule --refresh --json
ahu bills-raw --page 1 --size 20 --json
ahu mail-messages --unread
ahu wall-search --query 选课
ahu resources --path "" --json
ahu ask --question "这周有哪些考试？"
ahu open camera
ahu open settings__theme_lab
ahu call get_free_rooms --help
ahu file-upload --input report.pdf --json
ahu mail-attachment-export --message MESSAGE_ID --part 2 --output attachment.pdf
ahu resource-export --path cs/some-file.pdf --output some-file.pdf
ahu calendar-export --year YEAR_ID --output calendar.pdf
```

全局选项 `--serial`、`--user`、`--package`、`--adb`、`--timeout` 可放在命令后。多设备时必须选择 serial。`--json` 输出 JSON；人类可读工具结果优先显示摘要。业务错误退出码 1，参数/取消为 2，ADB/协议故障为 3。超时不会自动重发请求。

响应中的 `appDemoMode` 表明 App 是否处于演示模式；人类可读输出也会显示提示，避免将演示校园数据当成真实查询结果。

## 功能发现与覆盖

`catalog` 从设备实际运行的 App 动态读取全部 AI 工具及额外的业务适配操作，包含参数 JSON Schema、是否写操作、是否需密码以及执行方式；不需要把全部操作再写一遍到命令行解析器里。每个 catalog command 都可直接作为 `ahu COMMAND` 或 `ahu call COMMAND` 运行。短命令如 `schedule` 只是工具别名。

`features` 包含全部首页小工具（含已装插件）、页面和业务命令；`pages` 读取操作目录的页面及补充入口，明确标记为 `android-ui`。**有页面入口不等于已有无界面业务实现**，不能用这两张表声称完全无界面功能对等。用户仍可在打开的页面执行 App 特有操作。

直接业务命令覆盖现有 AI 工具，另补账号/原始课表/学期/GMIS/自定义课/原始账单/邮箱及附件/资料/校园墙/两校区洗衣/笔记导入导出/公开设置/模型配置等。付款、发信、失物发布删除和评教复用现有业务工具。系统日历与通知调度、插件安装、复杂偏好、扫码和相机等通过 Android 页面操作。

文件传输使用同一受保护 provider 的二进制通道，校验大小和 SHA-256，不把整个文件放进 Binder JSON。文件暂存 10 分钟，上传最多 50 MiB；上传返回的 `id` 可交给 `mail-draft-attach --file ID` 或 `note-photo-import --file ID`。导出使用 `--output`，默认拒绝覆盖；只有显式 `--force` 才替换。大文件可用 `--timeout` 延长传输时间。传输完成后可用 `file-close --id ID` 清理暂存。

## 写操作与凭据

写操作先准备并显示完整参数，用户输入 `yes` 后才发送执行请求；自动化脚本可显式传 `--yes`。确认凭据由设备生成，绑定具体命令、参数和账号代号，120 秒有效且只能使用一次；换号、重启或篡改参数均不能复用。执行结果不明时先核查，不自动重试。

密码及 API Key 使用隐藏输入；非交互运行使用 `--secret-env VARIABLE_NAME`。不要把密码放进 `--args` 或参数文件。命令行不导出校方 Cookie、模型 Key 或保存的密码。凭据继续由 App 的 Keystore 保管。

```sh
ahu login --username YOUR_STUDENT_ID
ahu call recharge_electricity --amount 10
ahu ai-configure --baseUrl https://your-provider.example/v1 --model your-model
```

`ask` 不自动批准模型提出的写操作；需要执行时改用明确的业务命令。官方试用资格、模型能力和业务账号类型限制保持与 App 一致。

Provider 仅编译进 Debug，使用 `android.permission.DUMP`，代码内再次验证调用者为 shell/root；不新增监听端口。已授权 ADB 是管理员访问能力，只授权可信电脑。请求不记日志，host 子进程参数不包含请求正文。

## 测试

```sh
python -B -m unittest discover -s cli/tests -v
python cli/check_coverage.py
```

覆盖检查验证 App 内置工具和路由目录的静态对应关系；业务可用性仍需要登录设备、网络及服务端验证。它不把入口可达测试当作业务成功测试。
