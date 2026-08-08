---
name: yukon-auto-update-release
description: AHUTong Android production release publishing workflow. Use when the AIO release workflow routes an Android release here, or when the user explicitly includes the Chinese word 发版 and clearly asks to publish the Android APK; do not use for other platforms, ordinary builds, tests, release-package-only, upload-only, or version-check requests.
---

# Yukon Auto Update Release

This skill publishes an AHUTong Android release APK to the update server. It is intentionally narrow and production-affecting.

## Trigger Gate

Use this skill only when the user explicitly includes `发版` in the request.

Do not use this skill for requests such as "打 release 包", "构建 release", "上传 APK", "更新版本号", "检查版本", or "release" unless the same request also contains `发版`.

## Required Workflow

1. Read this `SKILL.md` completely.
2. Confirm the Android subrepository is on a project-compliant named branch such as `p/Yukon163/feat/<task-name>` or `p/Yukon163/fix/<task-name>`. Never release from the detached HEAD created by submodule initialization, and do not continue from a dirty worktree unless the dirty files are understood.
3. Check whether the Android checkout is shallow. Fetch the exact source, `master`, and required release refs; deepen only until fast-forward, merge-base, and changelog comparisons are reliable. Do not assume the AIO default `--depth 1` contains enough release history.
4. Run `adb devices`.
5. Run `.\gradlew.bat :app:testDebugUnitTest`.
6. Run the helper script from the Android subrepository root. If the user says only `发版`, do not pass a version. If the user says a concrete target such as `发版3.1.4`, pass it as `--version-name 3.1.4`:

   ```powershell
   python .agents\skills\yukon-auto-update-release\scripts\release_publish.py
   ```

7. Use the ignored `config.local.json` in the AIO main worktree. Do not copy it into the Android subrepository or a linked worktree. If it is missing, follow "First-Use Credential Setup" below and never ask the user to paste secrets into the conversation.
8. If the script reports a local/server version mismatch, stop and ask the user to choose one of the two options printed by the script. Do not modify versions, build, or upload until the user confirms.
9. Select the release mode from the explicit target and server version:
   - Newer target: fast-forward the work branch into `master`, commit the version bump, then create or validate `release/{target versionName}` at that exact commit.
   - Same target: require an existing remote release branch, apply every linear work-branch commit beyond `origin/master` to that release branch, keep `versionName`, increment `versionCode`, and atomically push `master` with the release branch.
   - Older target: treat it as rollback and require an existing release branch; never create it from the current work branch.
10. Do not ask the user for release notes by default. Normal releases derive `apk_changelog.txt` from the diff between `release/{previous versionName}` and `release/{target versionName}`. Rollback releases always use generic changelog text unless the user explicitly supplies safe generic notes.
11. After the script succeeds, verify:

   ```powershell
   curl https://openahu.org/api/check_apk_update
   ```

12. Confirm the helper pushed `master` and `release/{target versionName}` for forward and same-version hotfix releases. For rollback releases, commit and push only if an explicit branch-policy decision requires recording the rollback publish metadata.
13. Record the full commit SHA printed for `release/{target versionName}`. When running under the AIO workflow, return that exact branch and commit to the AIO release skill so it can update the Android gitlink through its release-branch gate.

## First-Use Credential Setup

The canonical config is stored in the AIO main worktree, even when the Android release runs from a linked AIO worktree:

```text
<AIO-main>/.agents/skills/yukon-auto-update-release/config.local.json
```

The helper resolves `<AIO-main>` from the AIO Git common directory, so all linked worktrees share one config. `AHUTONG_AIO_ROOT` or `--config` may override discovery for an unusual layout.

If the file is missing, do not collect passwords, private keys, or signing secrets in Codex chat. Ask the user to run this command from the Android subrepository root in a normal interactive terminal:

```powershell
python .agents\skills\yukon-auto-update-release\scripts\release_publish.py --setup-config
```

The setup command creates or validates the ignored AIO-root config and exits without building or publishing. Use `--print-config-fields` only to display the non-secret field names.

Before the first connection, verify the release server's SSH host-key fingerprint through a trusted channel and add it to the operating-system `known_hosts` file. Alternatively, set optional `server.known_hosts_path` to a dedicated verified file. The helper rejects unknown host keys and never accepts them automatically.

## Helper Script Behavior

The helper script is `scripts/release_publish.py`.

It performs these actions:

- Resolves the AIO main worktree and loads its ignored `.agents/skills/yukon-auto-update-release/config.local.json`.
- Creates or validates that AIO-root config only when `--setup-config` is run interactively; setup exits without publishing.
- Never stores the canonical config in the Android subrepository or a linked worktree.
- When run from a Codex shell and the config is missing, exits with setup guidance instead of prompting for secrets.
- Loads SSH host keys from the operating-system trust store and optional `server.known_hosts_path`, then rejects unknown or mismatched server keys.
- Reads local `app/build.gradle.kts` for `versionCode` and `versionName`.
- Reads server `/home/ubuntu/AHUTong/server/update_server/static/apk_version.txt` and, if present, `apk_version_name.txt`.
- Defaults to `versionCode = max(localVersionCode, serverVersionCode) + 1`.
- Defaults to patch-incrementing `versionName`, for example `3.1.5 -> 3.1.6`.
- Accepts explicit `--version-code` and `--version-name` when the user explicitly gives a target version.
- Treats explicit `--version-name` lower than the server `versionName` as rollback publish: build from `release/{versionName}`, keep that display `versionName`, but publish with `versionCode = serverVersionCode + 1` unless a higher explicit `--version-code` is supplied.
- Treats an explicit `--version-name` equal to the server `versionName` as a same-version hotfix. It requires the local `versionName` to match, requires an existing `origin/release/{versionName}`, increments `versionCode`, and rejects work branches with no new commits or merge commits.
- Prepares same-version hotfix commits on temporary local branches. It cherry-picks the complete linear `origin/master..work-branch` range into the release branch and uses one atomic push for `master` and `release/{versionName}`, so a conflict cannot leave only one remote branch updated.
- For normal forward releases, fast-forward merges the current branch into `master`, updates and commits the target version on `master`, pushes `master`, and creates missing `release/{target versionName}` at the resulting `master` commit.
- Rejects an existing normal-release target branch if it does not point to the current `master` commit, preventing release branches from being based on stale feature branch heads or separate release-only version bumps.
- Ensures `release/{previous versionName}` exists for changelog baselines, creating it when needed from `--previous-release-ref`.
- For normal forward releases, switches to `release/{target versionName}` only after the version bump has already been committed on `master`; for rollback releases, switches to the existing rollback release branch before updating versions, building, signing, or uploading.
- Uses `--previous-release-ref` only when the previous release branch is missing; the default is `HEAD~1`, but the agent should pass the actual previous-release baseline if that default is wrong.
- Derives changelog lines from `git log` and `git diff --name-only` over `release/{previous versionName}..release/{target versionName}`.
- Filters hidden/internal subjects and paths such as gray rollout, debug-only controls, or this release skill itself. It keeps safe user-visible subjects from a mixed diff, and uses generic notes such as `修复了一些 bug` and `进行了一些体验优化` when no safe user-visible change remains.
- For rollback publish, writes generic notes and never mentions rollback, downgrade, revert, or the older target version in user-facing changelog text.
- Restores `app/build.gradle.kts` byte-for-byte after rollback publishing, including when building, signing, or uploading fails.
- Builds release with `.\gradlew.bat :app:assembleRelease`.
- Locates Android SDK from `local.properties`, then uses the highest installed build-tools `zipalign` and `apksigner`.
- Passes keystore passwords to `apksigner` through child-process environment variables rather than command-line arguments.
- Uploads the signed APK to `/home/ubuntu/AHUTong/server/update_server/static/ahutong.apk`.
- Backs up the old APK as `ahutong.apk_{version}.bak`, appending a timestamp if that backup already exists.
- Updates `apk_version.txt`, creates or updates `apk_version_name.txt`, and writes the generated or explicitly supplied 1-2 changelog lines to `apk_changelog.txt`.

## Local Release Build Notes

- Use JDK 17+ for release signing. The current signing keystore is PKCS12/PBES2-based; JDK 8 cannot read it reliably.
- Prefer the project wrapper for normal release builds:

  ```powershell
  .\gradlew.bat :app:assembleRelease
  ```

- If the wrapper cannot download but Gradle is already unpacked locally, using the unpacked `gradle.bat` is acceptable for diagnostics only. Do not encode machine-specific Gradle paths in scripts.
- Release builds enable R8 (`isMinifyEnabled = true`), so every Gson model package and every Retrofit API interface used by release-only flows must be kept. At minimum keep:

  ```proguard
  -keep class com.ahu.ahutong.data.model.** { *; }
  -keep class com.ahu.ahutong.data.crawler.model.** { *; }
  -keep class com.ahu.ahutong.data.repository.** { *; }
  -keep class com.ahu.ahutong.data.weather.** { *; }
  -keep interface com.ahu.ahutong.data.crawler.api.jwxt.EvaluationApi { *; }
  -keep class com.ahu.ahutong.data.EvaluationRepository { *; }
  -keep class com.ahu.ahutong.data.AHURepository { *; }
  ```

- When signing manually, align before signing and explicitly pass `--ks-type PKCS12`. From Git Bash/MSYS2 invoke `apksigner.bat` with the `.bat` suffix. Use `apksigner`'s environment-backed password input instead of `pass:<password>` command-line arguments. Do not store keystore passwords, server passwords, or host credentials in this skill or in committed files; use ignored local config or one-off shell input.

## Release Branch and Changelog Rule

Always publish from `release/{target versionName}`. If the branch exists locally, check it out. If it exists only on `origin`, create a local tracking branch.

For normal forward releases, integrate code in this order: current work branch -> `master` -> `release/{target versionName}`. The helper script uses fast-forward merges only. If the current branch cannot fast-forward into `master`, stop and rebase/sync manually before publishing. Commit the version bump on `master` before creating the release branch. Create a missing target release branch from current `master`; if the target release branch already exists but does not point to current `master`, stop instead of publishing from a stale or release-only branch. During the first migration to release branches, also create `release/{previous versionName}` if missing so future changelog diffs have a stable baseline.

For a same-version hotfix, require the user to explicitly name the already-published server version. Start from a clean dedicated work branch based on `origin/master`. Require all work-branch-only commits to be linear, then apply the complete range to the existing remote release branch. Keep `versionName`, increment `versionCode`, and atomically push the prepared `master` and release tips. Do not create a missing same-version release branch, do not publish only the current commit when the work branch contains multiple dependent commits, and do not push either branch if cherry-pick preparation fails.

For rollback releases, where the user explicitly requested a `versionName` lower than the server `versionName`, the target branch must already exist locally or on `origin`. Build the old code from that branch, but keep the published `versionCode` greater than the current server `versionCode`; Android will not install an APK whose `versionCode` is less than or equal to the installed app. Keep the requested `versionName` as display text.

Generate release notes from the two release branches, not from memory or from a free-form user prompt:

```powershell
git log --no-merges --format=%s release/{previous}..release/{target}
git diff --name-only release/{previous}..release/{target}
```

Do not mention hidden gray-release functionality in user-facing changelog text. Filter hidden subjects and paths from mixed diffs while preserving safe user-visible subjects. If no safe user-visible subject or path remains, use generic lines instead, for example:

```text
修复了一些 bug
进行了一些体验优化
```

For rollback releases, use generic changelog text and do not write terms such as `回退`, `回滚`, `降级`, `rollback`, `downgrade`, or the older target version as the reason for the update.

## Version Mismatch Rule

If local and server versions differ during a normal forward release, the script must stop before any mutation and print two choices:

1. Upgrade both local and server to the next patch release, for example local `3.1.4` and server `3.1.5` become `3.1.6`.
2. Abort so the user can rebase/sync to the latest version and rerun the release.

Ask the user which option to use. Do not continue automatically.

This mismatch rule does not apply when the user explicitly requests an older target `versionName` such as `发版3.1.4` while the server is already `3.1.5`; that is rollback publish. In rollback publish, require an existing `release/3.1.4` branch and use `serverVersionCode + 1` as the default `versionCode`.

It also does not apply when the user explicitly requests the same server `versionName`. That is a same-version hotfix publish, not a normal forward release. Require the work branch and server to use that same `versionName`, and default to `max(localVersionCode, serverVersionCode) + 1`.

## Dry Run

Use dry-run to validate config, version parsing, mismatch handling, and tool discovery without modifying files or uploading:

```powershell
python .agents\skills\yukon-auto-update-release\scripts\release_publish.py --dry-run
```

Dry-run requires the AIO-root config and never creates it. It may fetch remote Git refs and read server version metadata, but it must not modify tracked files, merge `master`, create branches, switch branches, build, sign, upload, or modify server files.
