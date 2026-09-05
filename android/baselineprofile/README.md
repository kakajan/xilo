# Baseline Profile (Xilo Android)

A starter profile ships in `app/src/main/baseline-prof.txt` and is packaged with release APKs.

To generate a tighter device profile later:

1. Add a Macrobenchmark / `androidx.baselineprofile` module when a physical device or emulator is available.
2. Run the generator against `ir.xilo.app/.MainActivity` (home feed cold start + scroll).
3. Replace `app/src/main/baseline-prof.txt` with the output. Do not paste full generator logs into chat or PRs.

This folder is documentation-only so AGP 9 app builds stay green without a `:baselineprofile` test module.
