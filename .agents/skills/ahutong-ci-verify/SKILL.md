---
name: ahutong-ci-verify
description: >-
  Runs AHUTong pre-push and CI verification checks across Python scripts, workflow YAML,
  Rust SDK/GuiXu crates, Gradle unit tests, AndroidTest compilation, Release lint,
  and dependency lock consistency. Use before pushing, when fixing CI failures, or when
  modifying .github/workflows, scripts/, Cargo.toml, or gradle dependency configs.
---

# AHUTong CI & Pre-Push Verification

## Pre-Flight Device Check

Run `adb devices` before Android build/run validation:
- Online device attached: build Debug APK, install, and launch on device.
- No online device: run headless Gradle compile, unit tests, AndroidTest compilation, and Release lint only.

## Verification Pipeline (Matches `.github/workflows/checks.yml`)

Run relevant checks locally before pushing (run all when modifying CI, Gradle, or cross-module boundaries):

1. **Git whitespace & secret hygiene**:
   ```powershell
   git -c core.whitespace=cr-at-eol diff --check
   python scripts/check_repo_hygiene.py
   ```
2. **Python CI & helper tests**:
   ```powershell
   python -B -m unittest discover -s scripts -p "test_ci_*.py" -v
   ```
   When modifying `decrypt_feedback_logs.py` (with `cryptography` installed):
   ```powershell
   python -B -m unittest discover -s scripts -p "test_decrypt_*.py" -v
   ```
3. **Workflow lint** (when `.github/workflows/*.yml` or `.github/actions/**` changes):
   ```powershell
   actionlint .github/workflows/checks.yml .github/workflows/build.yml .github/workflows/publish.yml
   ```
4. **Rust SDK & GuiXu crates** (when `sdk/` or `GuiXu-Rust/` changes):
   ```powershell
   cargo test --manifest-path sdk/Cargo.toml
   cargo test --manifest-path GuiXu-Rust/Cargo.toml
   cargo clippy --manifest-path sdk/Cargo.toml --all-targets -- --allow clippy::invalid_regex
   cargo clippy --manifest-path GuiXu-Rust/Cargo.toml --all-targets
   ```
5. **Android JVM unit tests & AndroidTest compilation**:
   ```powershell
   .\gradlew.bat --no-daemon testDebugUnitTest :app:compileDebugAndroidTestKotlin --stacktrace
   ```
6. **Release lint** (`--no-configuration-cache` required by AGP lint model serialization):
   ```powershell
   .\gradlew.bat --no-daemon --no-configuration-cache :app:lintRelease :feature:circle:lintRelease --stacktrace
   ```

## Dependency Lock & CI Workflow Rules

1. Manage all dependency and plugin versions in `gradle/libs.versions.toml`; never add raw versioned strings in subproject `build.gradle.kts` when a catalog entry exists or should be declared.
2. After changing any dependency or plugin version, regenerate locks with `./gradlew --write-locks`, update `gradle/verification-metadata.xml` if needed, and keep `core/plugin-platform/build.gradle.kts` synchronized.
3. Never commit `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `config.local.json`, raw `BUGLY_APP_ID` (`2c2ccadcad`) literals in Kotlin/Java, or personal machine paths.
4. Preserve pinned commit SHAs on all GitHub Actions in `.github/workflows/*.yml` and `.github/actions/**`.
5. Keep `publish.yml` restricted to manual `workflow_dispatch`; never expose publishing secrets to `pull_request` workflows or perform direct local production releases outside CI.
