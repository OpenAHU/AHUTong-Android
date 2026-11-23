---
name: ahutong-ui-design
description: >-
  Enforces AHUTong UI architecture, :core:designsystem component and theme usage,
  Material/Miuix/Radiant pack compatibility, Liquid Glass backdrop rules, AppHaptics,
  Hilt ViewModel injection, and concise Chinese UI copy. Use when creating or editing
  Compose screens, dialogs, settings rows, home widgets, or UI state holders.
---

# AHUTong UI Design Standard

## Component & Theme Reuse (`:core:designsystem`)

1. Theme every UI surface through `AHUTheme` and `LocalComponentPack` (`Material`, `Miuix`, `Radiant`, plus Theme Park `ComponentSlotId` overrides). Do not bypass component packs with mismatched ad-hoc Material/Miuix styling where a design-system primitive exists.
2. Reuse `:core:designsystem` primitives first:
   - Scaffolds & headers: `AppPageScaffold`, `SecondaryPageScaffold`, `SecondaryPageHeader`, `HeaderScrim`
   - Surfaces & cards: `AppCard`, `GlassCard`, `LiquidGlassSurface`, `AppStateCard`
   - Controls & dialogs: `AppButton`, `AppDialog`, `AppSlider`, `AppTitleIconButton`, `AppToggle`, `LiquidButton`, `LiquidSlider`, `LiquidToggle`
   - Settings rows: `SettingsSection`, `SettingsActionRow`, `SettingsComponents`
3. Respect Liquid Glass and performance flags (`LocalIsLiquidGlassEnabled`, `LocalGlassEffectsReduced`, `LocalLiquidGlassAmbientBackdrop`, `LocalLiquidGlassContentBackdrop`, `LocalGlassReadabilityBoost`). Heavy backdrop capture layers must not trap high-frequency ambient animations (such as fireworks/particles).
4. Use `:core:designsystem` haptics (`AppHaptics`, `HapticIconButton`, `HapticTextButton`, `hapticToggleable`) for tactile controls; keep haptics opt-in via app settings rather than forcing system touch feedback.

## Feature UI Isolation & Navigation

1. Feature screens in `:feature:*` must only observe their own ViewModel state and design-system tokens.
2. Host-owned routing, account dialogs, and `BuildConfig.DEBUG` / app-wide actions are passed from `:app` wrappers via callbacks and display strings (follow `SettingsHub.kt` and `Main.kt` patterns). Never hardcode `:app` route strings inside `:feature:*` ViewModels or screens when avoidable, and never import another `:feature:*` module.
3. Inject ViewModels with `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel` (do not use deprecated `androidx.hilt.navigation.compose.hiltViewModel`).
4. Screen files must never import `data.crawler.*`, `AHUCache`, `PreferencesManager`, or raw `OkHttpClient`.

## UI Copy Rules (`AGENTS.md` §4.3)

- Write setting descriptions, helper captions, empty states, and action labels as concise, direct declarative Chinese text.
- Omit verbose error-handling explanations, colloquialisms, slang, and filler modifiers.
- Keep user-facing error phrasing mapped cleanly in the UI/presentation boundary from `AhuError`, never raw exception class dumps.

## Hygiene & Verification

1. Avoid duplicate imports and unused Compose state/imports.
2. Place new ViewModel unit tests and fake ports in the owning `:feature:<name>/src/test/` directory (`FeatureTestOwnershipTest` rejects feature ViewModel tests placed in `:app`).
3. Verify changes with:

```powershell
.\gradlew.bat --no-daemon :feature:<name>:testDebugUnitTest :app:lintRelease
```
