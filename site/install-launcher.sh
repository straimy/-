#!/usr/bin/env bash
set -euo pipefail

BASE="https://ggo.kvicloud.ru/downloads"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ "$(uname -s)" != "Linux" ]; then
  echo "This install command is for Linux. Use the Windows installer from https://ggo.kvicloud.ru/#download" >&2
  exit 2
fi

if command -v apt-get >/dev/null 2>&1; then
  pkg="$TMP/GunGloryOnline-Launcher-Ubuntu-Debian.deb"
  echo "Downloading GunGloryOnline Launcher for Ubuntu/Debian..."
  curl -fL --retry 4 --retry-all-errors "$BASE/GunGloryOnline-Launcher-Ubuntu-Debian.deb" -o "$pkg"
  echo "Installing launcher..."
  sudo apt-get install -y "$pkg"
  echo "GunGloryOnline Launcher installed/updated."
  exit 0
fi

if command -v dnf >/dev/null 2>&1; then
  pkg="$TMP/GunGloryOnline-Launcher-Fedora-RHEL.rpm"
  echo "Downloading GunGloryOnline Launcher for Fedora/RHEL..."
  curl -fL --retry 4 --retry-all-errors "$BASE/GunGloryOnline-Launcher-Fedora-RHEL.rpm" -o "$pkg"
  sudo dnf install -y "$pkg"
  echo "GunGloryOnline Launcher installed/updated."
  exit 0
fi

pkg="$HOME/Downloads/GunGloryOnline-Launcher-Linux.AppImage"
mkdir -p "$HOME/Downloads"
echo "Downloading universal AppImage..."
curl -fL --retry 4 --retry-all-errors "$BASE/GunGloryOnline-Launcher-Linux.AppImage" -o "$pkg"
chmod +x "$pkg"
echo "Downloaded: $pkg"
echo "Run it with: $pkg"
