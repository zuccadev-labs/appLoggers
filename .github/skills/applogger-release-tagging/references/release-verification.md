# Release Verification

After pushing the tag, verify:

1. `release.yml` run started.
2. `test` job passed.
3. `publish` job passed.
4. GitHub Release was created.
5. GitHub Packages publication succeeded.
6. JitPack can resolve the tag after indexing delay.

If release fails, inspect:

1. package permissions
2. version passed to Gradle
3. tag/commit mismatch