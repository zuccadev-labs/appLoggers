# Merge Policy

Default stance:

1. Merge when the diff is narrow, checks are green, and operational risk is low.
2. Defer when the change touches release-critical or compiler-critical behavior without enough validation.
3. Close only if the update is clearly undesirable or superseded.

Post-merge duty:

1. Ensure `dev` stays healthy.
2. Review whether docs or release notes need adjustment.