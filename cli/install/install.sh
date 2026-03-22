#!/usr/bin/env bash

set -euo pipefail

REPO="zuccadev-labs/appLoggers"
INSTALL_DIR="${APPLOGGERS_INSTALL_DIR:-}"
REQUESTED_VERSION="${APPLOGGERS_VERSION:-}"
CONFIG_DIR="${APPLOGGERS_CONFIG_DIR:-$HOME/.apploggers}"
CONFIG_FILE_NAME="${APPLOGGERS_CONFIG_FILE:-cli.json}"
CURL_RETRY_MAX="${APPLOGGERS_CURL_RETRY_MAX:-5}"
CURL_RETRY_DELAY="${APPLOGGERS_CURL_RETRY_DELAY:-2}"
CURL_CONNECT_TIMEOUT="${APPLOGGERS_CURL_CONNECT_TIMEOUT:-10}"
CURL_MAX_TIME="${APPLOGGERS_CURL_MAX_TIME:-120}"
CURL_RETRY_MAX_TIME="${APPLOGGERS_CURL_RETRY_MAX_TIME:-300}"

log() {
  printf '[apploggers] %s\n' "$*"
}

fail() {
  printf '[apploggers] ERROR: %s\n' "$*" >&2
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
    case "$REQUESTED_VERSION" in
      apploggers-v*) ;;
      *) fail "APPLOGGERS_VERSION must match apploggers-v*" ;;
    esac
    printf '%s' "$REQUESTED_VERSION"
    return
  fi

  local api_url="https://api.github.com/repos/${REPO}/releases?per_page=100"
  local releases
  releases="$(download_text "$api_url")"
  local tag
  tag="$(printf '%s' "$releases" | grep -o '"tag_name": *"apploggers-v[^"]*"' | head -n 1 | sed 's/.*"\(apploggers-v[^"]*\)"/\1/')"
  [ -n "$tag" ] || fail "unable to resolve latest apploggers release tag"
  printf '%s' "$tag"
}

download_text() {
  local url="$1"
  curl \
    --fail \
    --silent \
    --show-error \
    --location \
    --retry "$CURL_RETRY_MAX" \
    --retry-delay "$CURL_RETRY_DELAY" \
    --retry-max-time "$CURL_RETRY_MAX_TIME" \
    --connect-timeout "$CURL_CONNECT_TIMEOUT" \
    --max-time "$CURL_MAX_TIME" \
    "$url"
}

download_file() {
  local url="$1"
  local out_path="$2"
  curl \
    --fail \
    --silent \
    --show-error \
    --location \
    --retry "$CURL_RETRY_MAX" \
    --retry-delay "$CURL_RETRY_DELAY" \
    --retry-max-time "$CURL_RETRY_MAX_TIME" \
    --connect-timeout "$CURL_CONNECT_TIMEOUT" \
    --max-time "$CURL_MAX_TIME" \
    "$url" \
    -o "$out_path"
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

  fail "sha256 verifier not available (requires sha256sum or shasum)"
}

write_example_config() {
  local config_file="$1"
  cat > "$config_file" <<'EOF'
{
  "_comment": "AppLoggers CLI configuration file. Edit this file to configure your projects.",
  "_docs": "https://github.com/zuccadev-labs/appLoggers/tree/main/docs/ES/cli",
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": [],
      "supabase": {
        "url": "https://your-project.supabase.co",
        "api_key_env": "APPLOGGER_SUPABASE_KEY",
        "schema": "public",
        "logs_table": "app_logs",
        "metrics_table": "app_metrics",
        "timeout_seconds": 15
      }
    }
  ]
}
EOF
}

main() {
  need_cmd curl
  need_cmd mktemp

  local os arch version asset_name checksum_name release_base install_dir config_file tmp_dir tmp_asset tmp_checksum final_path
  os="$(detect_os)"
  arch="$(detect_arch)"
  version="$(resolve_version)"
  asset_name="apploggers-${os}-${arch}"
  checksum_name="${asset_name}.sha256"
  release_base="https://github.com/${REPO}/releases/download/${version}"
  install_dir="$(resolve_install_dir)"
  config_file="${CONFIG_DIR}/${CONFIG_FILE_NAME}"

  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT
  tmp_asset="${tmp_dir}/${asset_name}"
  tmp_checksum="${tmp_dir}/${checksum_name}"
  final_path="${install_dir}/apploggers"

  mkdir -p "$install_dir"
  mkdir -p "$CONFIG_DIR"
  if [ ! -f "$config_file" ]; then
    write_example_config "$config_file"
    log "created config template at ${config_file}"
  fi

  log "installing ${asset_name} from ${version}"
  download_file "${release_base}/${asset_name}" "$tmp_asset"
  download_file "${release_base}/${checksum_name}" "$tmp_checksum"
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
