---
name: r8-analyzer
description: Review Android R8/ProGuard rules for Xilo release builds. Use when minifyEnabled fails, release crashes on reflection/serialization, or auditing proguard-rules.pro.
---

# R8 / ProGuard analyzer (Xilo Android)

Release minify lives in `android/app/proguard-rules.pro`. R8 runs on `:app:release` when `minifyEnabled true`.

## Review workflow

1. **Reproduce minimally** — `./gradlew :app:assembleRelease` from `android/`. Capture only the *first* missing-class or keep-rule hint (class name + stack frame), not the full log.
2. **Classify the failure** — serialization DTO, Retrofit interface, Room entity/DAO, Hilt/Dagger generated class, Coil/OkHttp, or app code reached via reflection.
3. **Prefer targeted `-keep`** over broad `-keep class ** { *; }`. Add rules to `proguard-rules.pro`, rebuild release.
4. **Verify** — install release APK (or `minifyReleaseWithR8` + unit tests) and smoke-test auth, feed load, chat send, image load.

## Rules Xilo already needs (do not remove)

| Library | What to keep |
|---------|----------------|
| **Kotlinx Serialization** | `@Serializable` companions, `$$serializer`, `ir.xilo.app.data.remote.dto.**` |
| **Retrofit** | `@retrofit2.http.*` methods on interfaces; `Call`, `Response`, `Continuation` |
| **OkHttp / Okio** | `-dontwarn`; keep interfaces if reflection-used |
| **Room** | `@Entity`, `@Dao`, `RoomDatabase` subclasses |
| **Hilt / Dagger** | `dagger.hilt.**`, `@Inject` fields, `*_HiltModules*`, `*_Factory`, `XiloApplication` |
| **Coil** | If image load breaks in release: `-keep class coil.** { *; }` and model classes passed to requests |

Attributes: keep `Signature`, `*Annotation*`, `InnerClasses`, `EnclosingMethod` for Gson/serialization/reflection.

## Log discipline

- **Never paste full R8 logs** into chat, PRs, or skills — they are huge and leak package noise.
- Summarize: rule added, class kept, one-line reason, build result.
- If stuck, quote ≤10 lines around the *first* `Missing class` or `Warning` that maps to a keep rule.

## Common fixes

- **DTO field renamed but serializer kept** — ensure DTO package `-keep` matches `data.remote.dto`.
- **Retrofit 404 at runtime** — interface method stripped; add `-keepclasseswithmembers` for that API interface.
- **Room "cannot find implementation"** — missing `@Entity` / `@Dao` keep; check KSP generated `*_Impl` not obfuscated incorrectly.
- **Hilt crash on startup** — keep `ir.xilo.app.**_HiltModules**` and application class.

## Baseline Profile

Ship a starter profile at `android/app/src/main/baseline-prof.txt` (Compose/Paging/Room/Coil + `ir.xilo.app` hot packages). Generate a tighter profile later with Macrobenchmark on a device; do not paste full profile generator logs.

## Do not

- Disable minify to "fix" release without understanding the missing class.
- Add `-keep class ** { *; }` for the whole app.
- Commit mapping files or full build logs.
