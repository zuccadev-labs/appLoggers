#!/bin/sh
# One-time setup: configures git to use project hooks
git config core.hooksPath .githooks
echo "Git hooks configured. Conventional commits enforced."
