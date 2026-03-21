#!/usr/bin/env bash

set -euo pipefail

REPO="devzucca/appLoggers"
INSTALL_DIR="${APPLOGGER_CLI_INSTALL_DIR:-}"
REQUESTED_VERSION="${APPLOGGER_CLI_VERSION:-}"

log() {
  printf '[applogger-cli] %s\n' "$*"
}

fail() {
  printf '[applogger-cli] ERROR: %s\n' "$*" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

detect_os() {
  case "$(uname -s)" in
    Linux) printf 'linux' ;;
    Darwin) printf 'darwin' ;;
    *) fail "unsupported OS: $(uname -s)" ;;
  esac
}

detect_arch() {
  case "$(uname -m)" in
    x86_64|amd64) printf 'amd64' ;;
    arm64|aarch64) printf 'arm64' ;;
    *) fail "unsupported architecture: $(uname -m)" ;;
  esac
}

resolve_version() {
  if [ -n "$REQUESTED_VERSION" ]; then
    printf '%s' "$REQUESTED_VERSION"
    return
  fi

  local api_url="https://api.github.com/repos/${REPO}/releases?per_page=100"
  local releases
  releases="$(curl -fsSL "$api_url")"
  local tag
  tag="$(printf '%s' "$releases" | grep -o '"tag_name": *"applogger-cli-v[^"]*"' | head -n 1 | sed 's/.*"\(applogger-cli-v[^"]*\)"/\1/')"
  [ -n "$tag" ] || fail "unable to resolve latest applogger-cli release tag"
  printf '%s' "$tag"
}

resolve_install_dir() {
  if [ -n "$INSTALL_DIR" ]; then
    printf '%s' "$INSTALL_DIR"
    return
  fi
  if [ -w /usr/local/bin ]; then
    printf '/usr/local/bin'
  else
    printf '%s/.local/bin' "$HOME"
  fi
}

verify_checksum() {
  local file_path="$1"
  local checksum_path="$2"

  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$(dirname "$file_path")" && sha256sum -c "$(basename "$checksum_path")")
    return
  fi

  if command -v shasum >/dev/null 2>&1; then
    local expected actual
    expected="$(awk '{print $1}' "$checksum_path")"
    actual="$(shasum -a 256 "$file_path" | awk '{print $1}')"
    [ "$expected" = "$actual" ] || fail "checksum mismatch for $(basename "$file_path")"
    return
  fi

  log "sha256 verifier not available; skipping checksum verification"
}

main() {
  need_cmd curl
  need_cmd mktemp

  local os arch version asset_name checksum_name release_base install_dir tmp_dir tmp_asset tmp_checksum final_path
  os="$(detect_os)"
  arch="$(detect_arch)"
  version="$(resolve_version)"
  asset_name="applogger-cli-${os}-${arch}"
  checksum_name="${asset_name}.sha256"
  release_base="https://github.com/${REPO}/releases/download/${version}"
  install_dir="$(resolve_install_dir)"

  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT
  tmp_asset="${tmp_dir}/${asset_name}"
  tmp_checksum="${tmp_dir}/${checksum_name}"
  final_path="${install_dir}/applogger-cli"

  mkdir -p "$install_dir"

  log "installing ${asset_name} from ${version}"
  curl -fsSL "${release_base}/${asset_name}" -o "$tmp_asset"
  curl -fsSL "${release_base}/${checksum_name}" -o "$tmp_checksum"
  verify_checksum "$tmp_asset" "$tmp_checksum"

  chmod +x "$tmp_asset"
  mv "$tmp_asset" "$final_path"

  log "installed to ${final_path}"
  if ! printf '%s' ":${PATH}:" | grep -q ":${install_dir}:"; then
    log "${install_dir} is not in PATH for this shell"
    log "add this to your shell profile: export PATH=\"${install_dir}:\$PATH\""
  fi

  "$final_path" version
}

main "$@"