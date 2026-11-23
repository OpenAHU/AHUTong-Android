---
name: ahutong-plugin-dev
description: >-
  Guides development of AHUTong plugins under :core:plugin-api (v3), :core:plugin-platform,
  host plugin loading (PluginRegistry/AhupInstaller/RuntimePluginLoader), and standalone
  plugin modules like :feature:circle. Use when modifying plugin contracts, ClassLoader
  isolation, capability checks, plugin ProGuard rules, or packaging .ahup bundles.
---

# AHUTong Plugin Development (v3 Contract)

## Binary & Contract Boundary (`:core:plugin-api`)

1. `:core:plugin-api` must have **zero third-party dependencies**. Contract signatures may only use JDK types, Android framework types (`Context`, `View`, `Bitmap`, `Uri`), and `com.ahu.ahutong.core.plugin.*` types.
2. Never put Kotlin lambdas (`FunctionN`), `kotlin.Result`, Coroutines (`suspend` / `Flow`), OkHttp, Compose (`@Composable`), or `androidx.lifecycle` types in `:core:plugin-api` public signatures; use SAM/callback interfaces (`PluginUriCallback`, `PluginBooleanCallback`, `PluginBitmapCallback`, `PluginBitmapConsumer`).
3. UI boundary is `AhuPlugin.createContentView(context: Context, host: PluginHostServices): View`. Built-in or runtime Compose plugins wrap Compose inside `ComposeView(context)` and handle system bar insets themselves.
4. Keep `PLUGIN_API_LEVEL` (`core/plugin-api/.../AhuPlugin.kt`) monotonically synchronized with `core/plugin-api/build.gradle.kts` publication version; never make breaking signature removals without incrementing `PLUGIN_API_LEVEL`.
5. Keep `core/plugin-platform/build.gradle.kts` aligned with `gradle/libs.versions.toml` (Compose BOM, Kotlin stdlib, Coroutines, Coil).

## Isolation & Security Model

1. **ClassLoader**: `PluginClassLoader` delegates `com.ahu.ahutong.core.plugin.*` and boot framework packages (`java.*`, `android.*`, etc.) parent-first, and loads all other plugin classes child-first.
2. **Storage & Context**: `PluginContextWrapper` isolates plugin files under `filesDir/ahup_plugins/<id>/` and SharedPreferences under `plugin_<id>__<name>`, and returns `null` for external storage. Never expose host `applicationContext`.
3. **Capabilities**: Declare exact required `PluginCapability` values (`NETWORK`, `PLUGIN_STORAGE`, `CAMERA`). Host enforces capability gates in `PluginHostServicesImpl`; `http()` provides a bare `AhuHttp.plain()` client with zero campus cookies/session interceptors.
4. **Permissions & Manifest**: Runtime plugins must not declare Android permissions or components; host owns `CAMERA` permission and Photo Picker (`pickImage`). Any cleartext HTTP host needed by plugins must be explicitly justified in `app/src/main/res/xml/network_security_config.xml`.
5. **Cleanup**: `onDestroy()` must release cameras, cancel coroutines, and stop animations. Uninstalling or disabling `AhupGate` must clean both `ahup/<id>` and `ahup_plugins/<id>` via `cleanupPluginData`.

## Packaging & R8 Compatibility

1. Host `AhupInstaller` requires **v3 format** (`CURRENT_FORMAT = 3`: `manifest.json`, `plugin.apk`, `icon.png`, signed with RSA/SHA256 over `signaturePayloadV3`, matching `signerCert` and TOFU `signerFingerprint`). Legacy v1/v2 `plugin.dex` packages are rejected at inspect/load time.
2. Never hardcode personal SDK/JDK paths or keystore secrets in plugin packaging scripts or Gradle tasks.
3. Preserve all `com.ahu.ahutong.core.plugin.**` classes, `AhuPlugin` zero-arg constructors (`ServiceLoader`), and host-shared Compose/Kotlin runtime classes (including boxed `androidx.compose.ui.unit.TextUnit` and `Companion`) in `app/proguard-rules.pro`.

## Verification

```powershell
.\gradlew.bat --no-daemon :feature:circle:testDebugUnitTest :feature:circle:lintRelease
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.ahu.ahutong.ui.plugin.*"
```

If R8 rules or Compose `TextUnit` rules in `app/proguard-rules.pro` changed, also run `scripts/verify_plugin_text_unit_abi.py` with local `ui-unit-android` AAR, `kotlin-stdlib`, and `r8.jar`.
