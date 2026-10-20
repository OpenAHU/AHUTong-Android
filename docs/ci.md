# CI、构建与版本发布

## 三条流水线

| Workflow | 触发 | 职责 |
| --- | --- | --- |
| `checks.yml` / Code checks | 提交、PR、手动；也供发布复用 | diff/凭据检查、发布脚本测试、Rust 测试与 Clippy、Android 单测、Release lint |
| `build.yml` / Build APKs | 提交、PR、手动 | 编译固定 SDK 子模块的 Android SO，构建 Debug 与无签名 Release，核对 SO 来源和 R8 UUID，归档 APK/mapping |
| `publish.yml` / 发布版本 | 仅手动 `workflow_dispatch` | 通过完整检查后，计算版本、签名、上传 mapping、发布服务器、回写版本号、记录 Sentry release/deploy |

同仓库分支已有 PR 时，自动流程优先检查 PR merge commit，避免 push/PR 重复运行。新提交取消该分支旧的自动检查/构建；发布流程跨两个渠道串行，不取消正在发布的版本。工具链和缓存配置集中在 `.github/actions/setup-android/action.yml`，第三方 Actions 固定提交 SHA。PR 不读取发布 Secrets。外部 fork PR 缓存只读；同仓库 PR 可保存其隔离的缓存，便于后续提交复用编译结果。Gradle 启用 build cache；单测与 Release lint 按顺序执行，先取得测试结论，再进行 Release 分析。

自动构建的两个产物分别是 `AHUTong-ci-debug-apk` 和 `AHUTong-ci-unsigned-release-with-mapping`。后者未签名，用于验证，不能作为正式更新安装。自动流程不会上传 Sentry mapping。

## 发布输入和版本规则

在 Actions → 发布版本 → Run workflow 选择源分支和以下输入。GitHub 要求此 workflow 文件先存在于仓库默认分支，才提供手动入口；仅放在 develop 时尚不能从默认分支界面启动。要显示中文表单和选项，默认分支也需要包含本次表单修改。

| 输入 | 说明 |
| --- | --- |
| `change` | 中文下拉框显示版本号的递增规则，默认选中测试版；脚本仍接收 `major` / `minor` / `patch` / `test` |
| `changelog` | 必填更新说明，支持实际换行，也可输入字面 `\n` 分行 |
| `force` | 是否强制更新，默认 `false` |

下拉框以正式版 `x.y.z` 为基准提示以下规则。若本地已预置更高目标版本，则使用该目标，不重复递增。

| 选项 | 版本规则 | 正式版为 `3.4.2` 时的示例 |
| --- | --- | --- |
| `major` 主版本 | `x+1`，`y=0`，`z=0` | `4.0.0` |
| `minor` 次版本 | `y+1`，`z=0` | `3.5.0` |
| `patch` 修订版 | `z+1`；对应测试版转正式时不再加一次 | `3.4.3` |
| `test` 测试版 | 下个修订版加 `-betaN`，首次 `N=1`，同一目标后续 `N+1` | `3.4.3-beta1`；已有 beta2 则为 beta3 |

通过 GitHub API 手动触发时仍可传入裸值 `major` / `minor` / `patch` / `test`；表单值会在传给规划脚本前提取相同的英文前缀。版本计算、渠道与部署顺序不变。

`versionCode` 使用 `xyyzzz`：`x` 对应本次实际版本的主版本（0–9），`yy` 对应次版本（0–99，两位补零），`zzz` 是同一主/次版本内的发版序号（三位补零，与版本名的修订号及 beta 序号无关）。正式版和 preview 共用序号，读取本地 Gradle 默认版本及服务器两个渠道的版本码，在相同 `xyy` 前缀下取最大的 `zzz` 再加 1；切换到新的主/次版本且尚无该前缀记录时，`zzz` 从 `000` 开始。例如 `304005 → 304006`，升到 `3.5` 为 `305000`，升到 `4.0` 为 `400000`。已有该目标版本的 preview 时继续递增，不重复归零。

主/次版本超出位数范围、`zzz` 超过 `999`，或新版本码不能高于本地及线上两个渠道的版本码时，规划会报错，要求提升主/次版本；不会把序号进位到次版本或生成倒退的版本码。版本规划文件和实际 APK 身份均归档，通过 Gradle 参数注入 APK 版本，服务器发布成功后再将实际版本写回 Git。

以正式版 `3.4.2` 为例：major → `4.0.0`，minor → `3.5.0`，patch → `3.4.3`，test → `3.4.3-beta1`。相同目标已有 beta2 时，下一次 test 为 beta3；正式 patch 晋升为 `3.4.3`，不会跳到 `3.4.4`。本地版本若已高于服务器正式版，视为已准备的目标，避免重复递增。若 preview 已发布更高的主/次版本，则不能再发布较低主/次版本的更新。

服务器目录从 Secret `DEPLOY_ROOT` 读取：test 发布到 `$DEPLOY_ROOT/preview/`，其他三种发布到 `$DEPLOY_ROOT/`。只配置正式版根目录，不能把 preview 目录填作根目录；preview 子目录由脚本自动选择。两渠道均构建 Release，包名 `com.ahu.ahutong`、生产签名一致，以支持互相覆盖安装。不会用 `.debug` 包作为 preview，也不更改独立 SO 下载端点的 `libahutong_rs.so` / `so_version.txt`。

历史服务器版本名 `X.Y.Z-debugN`、`X.Y.Z-release-betaN` 只作为规划输入，延续其测试序号；新计划一律输出 `X.Y.Z-betaN`。旧命名的计划不能直接改写元数据，须重新规划和构建，保证 APK 内嵌版本一致。2026-10-19 只读核对线上正式版为 `3.4.2/304003`、preview 为 `3.4.3-debug2/304005`；在这份状态下，test 规划为 `3.4.3-beta3/304006`，patch 为 `3.4.3/304006`。实际发布仍以构建前 SSH 读取的最新状态为准。

### 发布成功后回写 master / develop

`sync-published-version` 仅在 `publish` 成功后运行，与 Sentry 部署记录任务独立。它使用本次实际构建和部署的 `version_code` / `version_name`，从远端最新 `master`、`develop` 分别创建仅修改 `app/build.gradle.kts` 两个版本字面量的 Conventional Commit，不把来源分支的功能代码合入这两个分支。注释、Gradle 参数覆盖及其他文件保持不变。

回写使用原子 push；任一分支发生并发更新时，重新读取分支并重试，最多三次，不强推。版本已一致时不创建空提交，分支已有更高版本码时保留该版本，相同版本码却对应不同版本名则报错。只有回写任务获得 `contents: write`，通过仓库 `GITHUB_TOKEN` 提交，无需额外 PAT；该 token 的 push 不会触发普通 push 流水线，见 [GitHub GITHUB_TOKEN 文档](https://docs.github.com/en/actions/concepts/security/github_token)。分支保护仍然生效，需允许该任务直接提交。

如果回写任务失败，APK 已经发布，不会自动撤回；修复权限或冲突后，只重跑 `sync-published-version` 即可，不要重新部署同一 APK。

## 需要配置的 Secrets

仓库 Settings → Secrets and variables → Actions → Repository secrets。不要把值写进 YAML、Git、更新说明或日志。

| 名称（Secret） | 用途 | 要求 | 是否必须 |
| --- | --- | --- | --- |
| `DEPLOY_ROOT` | 服务器正式版部署根目录 | 已存在且 SSH 用户可写的绝对路径；例如 `/srv/app-updates/static`；仅允许英文字母、数字、`_`、`-`、`.`、`/`，不能以 `/` 结尾、不能包含空段或 `.` / `..` 路径段，不能填 preview 子目录 | 是 |
| `DEPLOY_SSH_TARGET` | SSH 登录目标 | `用户@主机名或IPv4`，必须可从发布 runner 连接，不能含 SSH 选项 | 是 |
| `DEPLOY_SSH_PRIVATE_KEY` | SSH 登录私钥 | 完整、无口令的专用私钥，保留首尾标记和换行；对应公钥已安装到目标账号，账号可读写部署根目录 | 是 |
| `DEPLOY_SSH_KNOWN_HOSTS` | 校验服务器身份 | 经可信渠道核对的 OpenSSH known_hosts 行；非默认端口使用 `[host]:port` 格式 | 是 |
| `DEPLOY_SSH_PORT` | SSH 端口 | `1..65535`；优先读取 Secret，兼容原同名 Actions Variable，均未配置时使用 `22` | 否 |
| `SENTRY_AUTH_TOKEN` | 创建版本、上传 mapping、记录部署 | PAT 至少含 `project:releases` + `org:read` 且可访问项目，或使用 `org:ci` 组织构建令牌；只读诊断令牌不可用 | 是 |
| `SENTRY_ORG` | Sentry 组织 | 组织 slug，必须与应用 DSN 所属组织一致 | 是 |
| `SENTRY_PROJECT` | Sentry 项目 | 项目 slug，不是数字 ID；必须与应用 DSN 对应同一项目 | 是 |
| `ANDROID_KEYSTORE_B64` | APK 签名文件 | 现有生产 JKS/PKCS12 文件的完整 Base64，不能新建一个不同签名代替 | 是 |
| `ANDROID_KEYSTORE_PASSWORD` | 解锁签名文件 | 对应签名文件密码 | 是 |
| `ANDROID_KEY_ALIAS` | 选择签名私钥 | 已有生产签名的 alias | 是 |
| `ANDROID_KEY_PASSWORD` | 解锁 alias 私钥 | 对应私钥密码；与签名文件密码相同时填相同值 | 是 |
| `ANDROID_CERT_SHA256` | 拒绝错误签名 | 从现有正式 APK 取得的 SHA-256 证书指纹，64 位十六进制，允许带冒号 | 是 |

以上共 12 项必填 Secrets、1 项可选 Secret。发布开始时会列出缺失的 Secret 名称并停止，不会使用仓库中的部署目录、主机或 Sentry 上传目标作为默认值。构建输出路径、产物名称与 preview 子目录属于固定构建/更新协议，不需要配置为 Secret。

本地生成签名文件 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\release.jks"))
```

证书指纹使用 Android build-tools 的 `apksigner verify --print-certs existing-production.apk` 中 `Signer #1 certificate SHA-256 digest`。SSH 主机指纹须独立核对；流水线不使用自动接受未知主机或关闭校验。

下面这项放在 Actions **Variables**，因为 job 的 `runs-on` 不能读取 Secrets：

| 名称（Variable） | 用途 | 要求 | 是否必须 |
| --- | --- | --- | --- |
| `RELEASE_RUNNER` | 选择执行构建、签名与 SSH 部署的 runner | 默认 `ubuntu-latest`；服务器仅内网可达时，填写具备内网访问能力的 self-hosted Linux x86_64 runner 标签 | 否 |

服务器终端显示的 hostname 不一定能从 GitHub runner 连接，`DEPLOY_SSH_TARGET` 必须填写实际可达的地址。自托管 runner 需要 Python 3.10+、OpenSSH，以及下载构建工具/依赖和访问 Sentry 的网络；服务器需要 Python 3.10+、OpenSSH 和部署根目录读写权限。

## 发布顺序、产物与失败处理

1. 复用完整 Code checks，未通过不接触发布凭据。
2. 严格核对 SSH 主机，读取两渠道状态与文件指纹，计算本次版本和说明。
3. 编译固定提交的 SDK SO；通过 Gradle `-Prelease.versionCode` / `-Prelease.versionName` 构建。此时 Sentry token 为空，只生成并嵌入 UUID。
4. APK 以 16KiB SO 页面边界 zipalign 后签名，校验最终证书、包名、版本、mapping 与 APK 的 UUID。
5. 在 `AHUTong-release-<run_id>-<run_attempt>` 保存本次签名 APK、mapping、UUID、CLI、摘要和发布计划，保留 90 天；其中没有私钥或密码。
6. 创建 Sentry release，关联完整 Git 提交，上传一次 mapping，再按 APK UUID 回读确认。任何失败都不会替换线上渠道文件。
7. 上传临时包；校验 ZIP、摘要、大小、元数据、全渠道版本码和之前的服务器指纹。备份目标渠道全部已有文件，再替换，版本码文件最后提交。
8. 独立后续任务在服务器发布成功后 finalize Sentry、创建 `release` 环境部署记录，已有相同渠道/版本码记录时不重复创建。
9. 独立后续任务将实际版本号提交到 `master`、`develop`，与第 8 步并行；失败后可单独重试。

服务器现有 API 根据 APK 现场计算 SHA-256 和 Ed25519 签名，CI 不创建额外 `.sha` / `signature` 文件。目标渠道更新五个现有公开文件，并新增可审计 `release.json`。备份位于 `.ci-backups/<channel>/<old_code>-<time>-<id>/`；其他已有备份、calendars 和独立 SO 文件不改动。

服务器脚本对协作发布者加全局锁，单文件采用同文件系统原子替换；中途异常恢复原有/原缺失文件，并保留备份。现有 API 逐文件读取且不使用该锁，因此整个文件组不保证同时原子可见；断电/强制杀进程也不能视为已完成回滚。构建期间任何渠道发生变化会拒绝部署，需重新计算和构建。

若仅最后的 `record-sentry-deployment` 失败，使用 GitHub **Re-run failed jobs**，只重试记录任务，复用同一次 APK/UUID。不要为了补 Sentry 记录重跑全部发布任务。Sentry 和服务器没有跨系统事务：服务器已发布时，记录失败不会自动撤回可用 APK。旧 APK 的缺失 mapping 仍需原构建资料，新流水线不能补造它。

## 验证

```bash
python3 -B -m unittest discover -s scripts -p 'test_ci_*.py' -v
actionlint .github/workflows/checks.yml .github/workflows/build.yml .github/workflows/publish.yml
```

测试使用临时目录、合成 APK/ZIP、真实本地裸 Git 仓库及外部命令/API mock，覆盖版本规则、版本字段回写、原子推送与并发重试、两渠道隔离、校验失败、并发状态变化、回滚及 Sentry 记录。它们不代替实际签名、SSH 发布或新 APK 真机安装验证。

参考：[GitHub workflow_dispatch](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch)、[Actions Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)、[zipalign](https://developer.android.com/tools/zipalign)、[Sentry releases](https://docs.sentry.io/cli/releases/)、[Sentry 权限](https://docs.sentry.io/api/permissions/)。

## 独立的圈子插件打包

`packageAhup` 属于插件签名，与上述 APK 发布签名不同。该任务已移除仓库中的旧签名密码和个人 Python/密钥路径。仅在本地打包插件时设置 `AHUP_KEYSTORE`、`AHUP_STORE_PASSWORD`、`AHUP_KEY_ALIAS`、`AHUP_KEY_PASSWORD`；可用 `AHUP_PYTHON` 指定 Python 可执行文件，默认 `python3`。未设置密钥时生成未签名插件；显式设置密钥但文件或凭据缺失会失败。不要用 Gradle `--info`/`--debug` 运行含签名参数的本地任务。

缓存隔离依据：[GitHub cache scope](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching#restrictions-for-accessing-a-cache)。PR 缓存不会作为默认分支的子分支缓存被默认分支恢复；发布依然独立从可信 ref 构建并校验产物。
