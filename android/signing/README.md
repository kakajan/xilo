# Android release signing (Aile)

## Layout

| File | Role | Git |
|------|------|-----|
| `aile-release.jks` | Upload / release keystore (PKCS12) | Tracked |
| `keystore.properties` | Store/key passwords used by Gradle | Tracked (private repo only) |
| `keystore.properties.example` | Template without secrets | Tracked |

## Custody

- Keep this repository **private**. Anyone with the `.jks` and `keystore.properties` can sign APKs as Aile.
- Back up `aile-release.jks` and the passwords offline (password manager / encrypted backup). Losing them means you cannot update the same Play App Signing / sideload identity.
- Alias: `aile` · Algorithm: RSA 2048 · Validity: ~10000 days from creation.

## Build a signed release

From `android/`:

```bash
./gradlew :app:assembleRelease \
  -Pxilo.apiBaseUrl=https://brain.aile.ir/ \
  -Pxilo.wsBaseUrl=wss://brain.aile.ir/ws
```

Output: `app/build/outputs/apk/release/app-release.apk`
