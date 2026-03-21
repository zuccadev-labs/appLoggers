# Delivery Matrix

Branch policy:

1. `main` - stable branch, release tags come from here.
2. `dev` - active integration branch.
3. `feature/*` and `fix/*` - optional working branches, but final delivery must reach `dev` before `main`.

Workflow triggers:

1. Push to `dev` - `lint`, `test`, `security`.
2. Pull request - `lint`, `test`, `security`.
3. Push to `main` - full CI, including `e2e`.
4. Push tag `v*` - `test`, `publish`, GitHub Release.

Release rule:

1. Finish work locally.
2. Validate locally.
3. Push to `dev`.
4. Open PR `dev -> main`.
5. Verify merged `main`.
6. Create and push tag from `main`.
