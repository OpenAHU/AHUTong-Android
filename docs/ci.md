# CI、构建与版本发布

## 三条流水线

| Workflow | 触发 | 职责 |
| --- | --- | --- |
| `checks.yml` / Code checks | 提交、PR、手动；也供发布复用 | diff/凭据检查、发布脚本测试、Rust 测试与 Clippy、Android 单测、Release lint |
| `build.yml` / Build APKs | 提交、PR、手动 | 编译固定 SDK 子模块的 Android SO，构建 Debug 与无签名 Release，核对 SO 来源和 R8 UUID，归档 APK/mapping |
| `publish.yml` / Publish release | 仅手动 `workflow_dispatch` | 通过完整检查后，计算版本、签名、上传 mapping、发布服务器、记录 Sentry release/deploy |

同仓库分支已有 PR 时，自动流程优先检查 PR merge commit，避免 push/PR 重复运行。新提交取消该分支旧的自动检查/构建；发布流程跨两个渠道串行，不取消正在发布的版本。工具链和缓存配置集中在 `.github/actions/setup-android/action.yml`，第三方 Actions 固定提交 SHA。PR 不读取发布 Secrets。外部 fork PR 缓存只读；同仓库 PR 可保存其隔离的缓存，便于后续提交复用编译结果。Gradle 启用 build cache；单测与 Release lint 按顺序执行，先取得测试结论，再进行 Release 分析。

自动构建的两个产物分别是 `AHUTong-ci-debug-apk` 和 `AHUTong-ci-unsigned-release-with-mapping`。后者未签名，用于验证，不能作为正式更新安装。自动流程不会上传 Sentry mapping。

## 发布输入和版本规则

在 Actions → Publish release → Run workflow 选择源分支和以下输入。GitHub 要求此 workflow 文件先存在于仓库默认分支，才提供手动入口；仅放在 develop 时尚不能从默认分支界面启动。

| 输入 | 说明 |
| --- | --- |
| `change` | `major` / `minor` / `patch` / `test`；默认 `test` |
| `changelog` | 必填更新说明，支持实际换行，也可输入字面 `\n` 分行 |
| `force` | 是否强制更新，默认 `false` |

从本地 Gradle 默认版本、服务器正式与 preview 元数据计算 `versionCode=max(三个渠道/本地的值)+1`，不按版本字符串拼接数字，且不超过 Android 的 2100000000 上限。版本规划文件和实际 APK 身份均归档，不自动提交版本改动回 Git。

以正式版 `3.4.2` 为例：major → `4.0.0`，minor → `3.5.0`，patch → `3.4.3`，test → `3.4.3-beta1`。相同目标已有 beta2 时，下一次 test 为 beta3；正式 patch 晋升为 `3.4.3`，不会跳到 `3.4.4`。本地版本若已高于服务器正式版，视为已准备的目标，避免重复递增。不同目标的 preview 仍参与版本码最大值计算。

test 发布到 `/www/openahu.org/update/static/preview/`，其他三种发布到 `/www/openahu.org/update/static/`。两渠道均构建 Release，包名 `com.ahu.ahutong`、生产签名一致，以支持互相覆盖安装。不会用 `.debug` 包作为 preview，也不更改独立 SO 下载端点的 `libahutong_rs.so` / `so_version.txt`。

历史服务器版本名 `X.Y.Z-debugN`、`X.Y.Z-release-betaN` 只作为规划输入，延续其测试序号；新计划一律输出 `X.Y.Z-betaN`。旧命名的计划不能直接改写元数据，须重新规划和构建，保证 APK 内嵌版本一致。2026-10-19 只读核对线上正式版为 `3.4.2/304003`、preview 为 `3.4.3-debug2/304005`；在这份状态下，test 规划为 `3.4.3-beta3/304006`，patch 为 `3.4.3/304006`。实际发布仍以构建前 SSH 读取的最新状态为准。

## 需要配置的 Secrets

仓库 Settings → Secrets and variables → Actions → Repository secrets。不要把值写进 YAML、Git、更新说明或日志。

| Secret | 内容 |
| --- | --- |
| `SENTRY_AUTH_TOKEN` | Sentry PAT：至少 `project:releases` + `org:read`，且用户可访问项目；也可使用 `org:ci` 组织构建令牌。诊断用只读令牌不能发布 |
| `DEPLOY_SSH_TARGET` | `用户@实际可连接的主机名或IPv4`，例如 `www@<host>` |
| `DEPLOY_SSH_PRIVATE_KEY` | 专用无口令 SSH 私钥完整内容，账号需可写上述 static 目录 |
| `DEPLOY_SSH_KNOWN_HOSTS` | 已通过服务器可信渠道核对的 OpenSSH known_hosts 行；非默认端口使用对应 `[host]:port` 项 |
| `ANDROID_KEYSTORE_B64` | 当前生产 APK 签名文件的完整 Base64，支持现有 JKS/PKCS12 文件 |
| `ANDROID_KEYSTORE_PASSWORD` | 签名文件密码 |
| `ANDROID_KEY_ALIAS` | 生产签名 alias |
| `ANDROID_KEY_PASSWORD` | alias 私钥密码；与文件密码相同时填写相同值 |
| `ANDROID_CERT_SHA256` | 现有生产 APK 的签名证书 SHA-256，支持带冒号格式，用于拒绝错误签名文件 |

本地生成签名文件 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\release.jks"))
```

证书指纹使用 Android build-tools 的 `apksigner verify --print-certs existing-production.apk` 中 `Signer #1 certificate SHA-256 digest`。SSH 主机指纹须独立核对；流水线不使用自动接受未知主机或关闭校验。

可选 Actions Variables：`DEPLOY_SSH_PORT` 默认 `22`；`RELEASE_RUNNER` 默认 `ubuntu-latest`。若 `10-7-103-101` 仅是服务器终端 hostname，不能直接当成 GitHub runner 可访问的地址；Secret 填实际可达地址。若服务器仅能由内网访问，将 `RELEASE_RUNNER` 设为可访问该网络的 `self-hosted` Linux x86_64 runner。它需要 Python 3.10+、OpenSSH，以及下载构建工具/依赖和访问 Sentry 的网络。服务器需要 Python 3.10+、OpenSSH 和 static 写权限。

## 发布顺序、产物与失败处理

1. 复用完整 Code checks，未通过不接触发布凭据。
2. 严格核对 SSH 主机，读取两渠道状态与文件指纹，计算本次版本和说明。
3. 编译固定提交的 SDK SO；通过 Gradle `-Prelease.versionCode` / `-Prelease.versionName` 构建。此时 Sentry token 为空，只生成并嵌入 UUID。
4. APK 以 16KiB SO 页面边界 zipalign 后签名，校验最终证书、包名、版本、mapping 与 APK 的 UUID。
5. 在 `AHUTong-release-<run_id>-<run_attempt>` 保存本次签名 APK、mapping、UUID、CLI、摘要和发布计划，保留 90 天；其中没有私钥或密码。
6. 创建 Sentry release，关联完整 Git 提交，上传一次 mapping，再按 APK UUID 回读确认。任何失败都不会替换线上渠道文件。
7. 上传临时包；校验 ZIP、摘要、大小、元数据、全渠道版本码和之前的服务器指纹。备份目标渠道全部已有文件，再替换，版本码文件最后提交。
8. 独立后续任务在服务器发布成功后 finalize Sentry、创建 `release` 环境部署记录，已有相同渠道/版本码记录时不重复创建。

服务器现有 API 根据 APK 现场计算 SHA-256 和 Ed25519 签名，CI 不创建额外 `.sha` / `signature` 文件。目标渠道更新五个现有公开文件，并新增可审计 `release.json`。备份位于 `.ci-backups/<channel>/<old_code>-<time>-<id>/`；其他已有备份、calendars 和独立 SO 文件不改动。

服务器脚本对协作发布者加全局锁，单文件采用同文件系统原子替换；中途异常恢复原有/原缺失文件，并保留备份。现有 API 逐文件读取且不使用该锁，因此整个文件组不保证同时原子可见；断电/强制杀进程也不能视为已完成回滚。构建期间任何渠道发生变化会拒绝部署，需重新计算和构建。

若仅最后的 `record-sentry-deployment` 失败，使用 GitHub **Re-run failed jobs**，只重试记录任务，复用同一次 APK/UUID。不要为了补 Sentry 记录重跑全部发布任务。Sentry 和服务器没有跨系统事务：服务器已发布时，记录失败不会自动撤回可用 APK。旧 APK 的缺失 mapping 仍需原构建资料，新流水线不能补造它。

## 验证

```bash
python3 -B -m unittest discover -s scripts -p 'test_ci_*.py' -v
actionlint .github/workflows/checks.yml .github/workflows/build.yml .github/workflows/publish.yml
```

测试使用临时目录、合成 APK/ZIP 及外部命令/API mock，覆盖版本、两渠道隔离、校验失败、并发状态变化、回滚及 Sentry 记录。它们不代替实际签名、SSH 发布或新 APK 真机安装验证。

参考：[GitHub workflow_dispatch](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch)、[Actions Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)、[zipalign](https://developer.android.com/tools/zipalign)、[Sentry releases](https://docs.sentry.io/cli/releases/)、[Sentry 权限](https://docs.sentry.io/api/permissions/)。

## 独立的圈子插件打包

`packageAhup` 属于插件签名，与上述 APK 发布签名不同。该任务已移除仓库中的旧签名密码和个人 Python/密钥路径。仅在本地打包插件时设置 `AHUP_KEYSTORE`、`AHUP_STORE_PASSWORD`、`AHUP_KEY_ALIAS`、`AHUP_KEY_PASSWORD`；可用 `AHUP_PYTHON` 指定 Python 可执行文件，默认 `python3`。未设置密钥时生成未签名插件；显式设置密钥但文件或凭据缺失会失败。不要用 Gradle `--info`/`--debug` 运行含签名参数的本地任务。

缓存隔离依据：[GitHub cache scope](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching#restrictions-for-accessing-a-cache)。PR 缓存不会作为默认分支的子分支缓存被默认分支恢复；发布依然独立从可信 ref 构建并校验产物。
