# Release Sequence

Correct order:

1. Complete work locally.
2. Push to `dev`.
3. Open PR `dev -> main`.
4. Merge after green checks.
5. Update local `main`.
6. Create annotated tag from `main`.
7. Push tag.

Typical commands:

1. `git checkout main`
2. `git pull origin main`
3. `git tag -a vX.Y.Z -m "Release X.Y.Z"`
4. `git push origin vX.Y.Z`