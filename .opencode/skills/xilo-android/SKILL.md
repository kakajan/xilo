---
name: xilo-android
description: Use when writing native Android Kotlin/Compose code for Xilo (`android/`). Covers MVVM/StateFlow, Hilt, Room outbox, Navigation 3, Vazirmatn/Inter, and share URLs. Do not use the Flutter xilo-mobile skill.
---

# Xilo Android (native)

Kotlin + Jetpack Compose + Material 3. Application id `ir.xilo.app`, minSdk 24. Visual authority: `openspec/changes/xilo-platform/specs/ui-ux-spec.md`. Native client authority: `openspec/changes/xilo-platform/design.md` (Android section) and `openspec/changes/android-native-production/`.

**Do not** edit, feature, or delete the legacy Flutter app under `mobile/`.

## Architecture

Presentation → ViewModel (`StateFlow`) → repository → Room / Retrofit. Hilt DI. Navigation 3 (`NavKey` in `NavigationKeys.kt`). Chat writes go through a Room outbox + WorkManager.

```
android/app/src/main/java/ir/xilo/app/
├── ui/                 # screens + ViewModels
├── data/remote/        # Retrofit, DTOs, WebSocket
├── data/local/         # Room entities, DAOs, drafts
├── data/repository/
├── data/sync/          # outbox
├── theme/              # Typography, colors, spacing
└── core/util/          # PublicWebUrls, locale, share
```

## Must follow

- Collect flows on screen with `collectAsStateWithLifecycle()`, never `collectAsState()`.
- Lazy lists: always `key` and `contentType`. Size Coil requests (`size` + `crossfade`).
- UI copy via `stringResource` / `strings.xml` (fa default + en/ar/ru/tr). No hardcoded Persian or English in composables.
- Fonts: Vazirmatn for RTL (`fa`, `ar`); Inter for LTR. Primary color token `#1D9BF0`.
- Share URLs are absolute HTTPS: `https://aile.ir/{username}/{slug}` via `PublicWebUrls` / `ShareActions` and `BuildConfig.PUBLIC_WEB_URL`. Never share relative `/p/{slug}`.
- Do not invent APIs. New post types (micro, gallery, video, link) need an OpenSpec change first.
- Cosmetic filters that do not hit the API must be hidden, not shown as working chips.
- Haptics on like; double-tap cover to like (`ui-ux-spec` §10.1).
- Long-press the like control for the 10-emoji picker (REQ-POST-007). Heart still maps to backend `like`.
- FAB opens a post-type sheet (text / article / quote / audio). Continue-draft chip appears when a local compose draft exists.
- Feed pagination is cursor `loadMore` on Room (ANP-2.2 subset). Do not invent Paging 3 RemoteMediator unless tasks.md requires it.
- `applicationId` is `ir.xilo.app`. Emulator launch: `adb shell am start -n ir.xilo.app/.MainActivity`.

## Tests

Unit tests under `android/app/src/test`. After Android UI changes: `./gradlew :app:installDebug` from `android/` and launch `ir.xilo.app/.MainActivity`.
