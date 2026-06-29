#!/usr/bin/env bash
set -euo pipefail

# Generates SPDX and CycloneDX SBOMs for SDK (Gradle) and CLI (Go).
# Requires: syft (https://github.com/anchore/syft)

readonly SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly ROOT_DIR="$(dirname "$SCRIPT_DIR")"
readonly OUTPUT_DIR="${1:-${ROOT_DIR}/build/sbom}"

mkdir -p "$OUTPUT_DIR"

echo "==> Generating SDK SBOM (SPDX)..."
syft dir:"${ROOT_DIR}/sdk" \
  --output spdx-json="${OUTPUT_DIR}/sdk-sbom.spdx.json" \
  --select-catalogers "gradle-cataloger" 2>/dev/null || \
  echo "  [warn] syft not found or SDK SBOM generation failed"

echo "==> Generating SDK SBOM (CycloneDX)..."
syft dir:"${ROOT_DIR}/sdk" \
  --output cyclonedx-json="${OUTPUT_DIR}/sdk-sbom.cyclonedx.json" \
  --select-catalogers "gradle-cataloger" 2>/dev/null || \
  echo "  [warn] syft not found or SDK SBOM generation failed"

echo "==> Generating CLI SBOM (SPDX)..."
syft dir:"${ROOT_DIR}/cli" \
  --output spdx-json="${OUTPUT_DIR}/cli-sbom.spdx.json" \
  --select-catalogers "go-module-cataloger" 2>/dev/null || \
  echo "  [warn] syft not found or CLI SBOM generation failed"

echo "==> Generating CLI SBOM (CycloneDX)..."
syft dir:"${ROOT_DIR}/cli" \
  --output cyclonedx-json="${OUTPUT_DIR}/cli-sbom.cyclonedx.json" \
  --select-catalogers "go-module-cataloger" 2>/dev/null || \
  echo "  [warn] syft not found or CLI SBOM generation failed"

echo "==> SBOMs saved to: ${OUTPUT_DIR}"
ls -lh "$OUTPUT_DIR"
