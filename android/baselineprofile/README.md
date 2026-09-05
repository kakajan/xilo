# Baseline Profile (Xilo Android)

A checked-in `baseline-prof.txt` was removed because AGP 9 rejected
wildcard class rules that carried `HSP` flags (`expandReleaseArtProfileWildcards`).

To generate a profile later:

1. Add a Macrobenchmark / `androidx.baselineprofile` module when a device is available.
2. Run the generator against `ir.xilo.app/.MainActivity` (home feed cold start + scroll).
3. Place the output at `app/src/main/baseline-prof.txt` using concrete class rules.
   Do not paste full generator logs into chat or PRs.

