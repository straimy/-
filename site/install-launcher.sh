#!/usr/bin/env bash
set -euo pipefail

ROOT="https://ggo.kvicloud.ru"
MANIFEST_URL="$ROOT/content/launcher/latest-beta.json"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ "$(uname -s)" != "Linux" ]; then
  echo "This install command is for Linux. Use the Windows installer from https://ggo.kvicloud.ru/#download" >&2
  exit 2
fi

command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 3; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 3; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 3; }

manifest="$TMP/latest-beta.json"
curl -fL --retry 4 --retry-all-errors "$MANIFEST_URL" -o "$manifest"
python3 -m json.tool "$manifest" >/dev/null

read_manifest() {
  python3 - "$manifest" "$1" <<'PY'
import json, sys
p=json.load(open(sys.argv[1],encoding='utf-8'))
key=sys.argv[2]
if key == 'version':
    value=p.get('version')
else:
    item=p.get(key) or {}
    value=f"{item.get('url','')}\t{item.get('sha256','')}"
if not value:
    raise SystemExit(4)
print(value)
PY
}

verify_package() {
  local file="$1" expected="$2"
  local actual
  actual="$(sha256sum "$file" | awk '{print $1}')"
  if ! printf '%s' "$actual" | grep -Eqi '^[0-9a-f]{64}$'; then
    echo "Could not calculate launcher SHA-256" >&2
    exit 5
  fi
  if [ "${actual,,}" != "${expected,,}" ]; then
    echo "Launcher SHA-256 verification failed" >&2
    echo "Expected: $expected" >&2
    echo "Actual:   $actual" >&2
    exit 6
  fi
}

version="$(read_manifest version)"
echo "GunGloryOnline Launcher latest beta: $version"

if command -v apt-get >/dev/null 2>&1; then
  IFS=$'\t' read -r url expected <<<"$(read_manifest linuxDeb)"
  pkg="$TMP/GunGloryOnline-Launcher-Ubuntu-Debian.deb"
  echo "Downloading GunGloryOnline Launcher $version for Ubuntu/Debian..."
  curl -fL --retry 4 --retry-all-errors "$url" -o "$pkg"
  verify_package "$pkg" "$expected"
  echo "SHA-256: PASS"
  echo "Installing launcher..."
  sudo apt-get install -y "$pkg"
  echo "GunGloryOnline Launcher $version installed/updated."
  exit 0
fi

if command -v dnf >/dev/null 2>&1; then
  # Fedora/RHEL keeps the stable direct package path until the update manifest gets an rpm field.
  pkg="$TMP/GunGloryOnline-Launcher-Fedora-RHEL.rpm"
  echo "Fedora/RHEL updater channel is not published yet. Use the official download page." >&2
  echo "https://ggo.kvicloud.ru/#download" >&2
  exit 7
fi

IFS=$'\t' read -r url expected <<<"$(read_manifest linuxAppImage)"
pkg="$HOME/Downloads/GunGloryOnline-Launcher-Linux.AppImage"
mkdir -p "$HOME/Downloads"
echo "Downloading universal AppImage $version..."
curl -fL --retry 4 --retry-all-errors "$url" -o "$pkg"
verify_package "$pkg" "$expected"
chmod +x "$pkg"
echo "SHA-256: PASS"
echo "Downloaded: $pkg"
echo "Run it with: $pkg"
