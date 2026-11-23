---
name: ahutong-architecture-guard
description: >-
  Enforces AHUTong multi-module architecture, strict dependency boundaries (R1-R31),
  ADR 0001/0002/0003 contracts, single-site HTTP factories, background read-only rules,
  and R8 ProGuard keep rules. Use when adding or modifying Gradle modules, Hilt wiring,
  repositories, ViewModels, network clients, storage, sessions, background workers, or Gson DTOs.
---

# AHUTong Architecture Guard

## Module Placement Rules

1. Place shared domain POJOs, Kotlin data classes, and protocol enums in `:core:model` (zero Android framework imports except `@NonNull`, zero IO/UI/business imports).
2. Place `AhuResult`, `AhuError`, `AppEnvironmentHolder`, `UserNoticeHolder`, diagnostics, and cross-module utilities in `:core:common`.
3. Place OkHttp/Retrofit assembly, DoH (`AliyunDns`), redacted logging (`NetworkLogging`), and campus session interceptors (`CampusHttp`) in `:core:network`.
4. Place preference contracts (`SettingsStore`, `CourseReminderSettings`, etc.) in `:core:storage` and auth/credential contracts (`AhuSession`, `CredentialVault`, `SecureStorage`) in `:core:auth`.
5. Place domain ports, pure policies, and wire DTOs in `:data:<domain>` without importing `:app` internals, `ui.*`, `reminder.*`, `notification.*`, or `appwidget.*`.
6. Place feature ViewModels, Compose UI, and **their unit tests + fakes** in `:feature:<domain>`. Never put feature ViewModel tests in `:app`.
7. Keep `:app` as the composition root (`di/*WiringModule`), NavHost wiring, and remaining legacy adapters (`AHURepository`, `AHUCache`, `data/crawler/*`, `personalization/*`, `assistant/*`).

## Hard Contracts

### 1. Error Boundary (ADR 0001)
- Cross-module APIs return `AhuResult<T>` (`Success` / exhaustible `AhuError`). Never return Kotlin `Result<T>` or `msg`-string failure checks across module boundaries.
- Map thrown exceptions exclusively via `Throwable.toAhuError()`. Map parse/format mismatches to `AhuError.ProtocolChanged`, never `AhuError.Server(-1, ...)` or `AhuError.Unknown`.
- Keep user-facing error phrasing in the UI layer; error types carry only upstream diagnostic text if needed.

### 2. Session Boundary (ADR 0002)
- `AhuSession` and `AhuSessionState` (`Anonymous` / `Authenticated` / `Expired`) are the single source of truth for academic login state.
- Derived domains (student mail, payment QR, campus card sub-flows) must never call `AhuSessionState.markAuthenticated()` or `markExpired()` (enforced by `SessionArchitectureTest`).
- Bounded recovery runs inside `RepositoryAhuSession` / `SessionRefreshCoordinator`; UI screens must not issue competing background password probes.

### 3. Tiered Storage (ADR 0003)
- **Credentials**: `CredentialVault` / `SecureStorage` (AES-GCM + Android Keystore, fail-closed; failure triggers sign-out/re-auth prompt).
- **Settings**: `SettingsStore` (DataStore; read failure falls back to default, write failure retries once).
- **Cache**: Per-user boxed cache (miss falls back to remote source; never sole truth for login or payment decisions).

### 4. Network Single Construction Site (R6-R9)
- Never call `OkHttpClient.Builder(` or `Retrofit.Builder(` outside `com.ahu.ahutong.data.network` (`:core:network`).
- Use `AhuHttp.plain(...)` / `AhuHttp.retrofit(...)` and `CampusHttp` extensions (`campusCookies`, `campusAutoLogin`, `campusSessionRefresh`, `foregroundPaymentQueue`, `withoutCampusSessionRefresh`).
- Never import `okhttp3.logging.*` outside `com.ahu.ahutong.data.network`; HTTP body logging must never ship in release builds.

### 5. Background Read-Only Rule (R28)
- `:background` (`Glance` widgets, `WorkManager`, `AlarmManager` receivers) obtains dependencies exclusively through `BackgroundEntryPoint` narrow read-only ports.
- Never trigger login, silent re-login, remote config sync (`resolveLocalFirst`, `syncRemoteConfig`), credential mutation, or `SettingsStore` writes from `:background`.

### 6. R8 / ProGuard Contracts
- Release builds enable `isMinifyEnabled = true`, R8 full mode, and `-repackageclasses`.
- Any class or enum serialized by Gson/Retrofit or persisted as JSON locally must have a `-keep class ... { *; }` / `-keep enum ... { *; }` rule in `app/proguard-rules.pro` to preserve field names and generic element signatures.

### 7. Zero-Exemption Gate Integrity
- Never add entries to `exceptPrefixes`, weaken `ModuleBoundaryTest`, or place Kotlin sources in `src/main/kotlin` to bypass scanning.
- Fix boundary failures by extracting a port interface into `:core:*` or `:data:*` and binding its implementation in `:app/di/*WiringModule.kt`.

## Verification

Run after any module, boundary, contract, or ProGuard edit:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.ahu.ahutong.architecture.*"
```

Also run `:testDebugUnitTest` for every touched module and `:app:lintRelease`.
