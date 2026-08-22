#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID}" -ne 0 ]; then
  echo "Run as root: sudo ./publish-launcher-update.sh <package-dir> <version> [notes]" >&2
  exit 1
fi

PACKAGE_DIR="${1:-}"
VERSION="${2:-}"
NOTES="${3:-GunGloryOnline Launcher update}"
WEB_ROOT="/var/www/gungloryonline"
DOWNLOADS="$WEB_ROOT/downloads"
CHANNEL_DIR="$WEB_ROOT/content/launcher"
MANIFEST="$CHANNEL_DIR/latest-beta.json"

if [ -z "$PACKAGE_DIR" ] || [ -z "$VERSION" ]; then
  echo "Usage: sudo $0 <package-dir> <version> [notes]" >&2
  exit 2
fi
if [ ! -d "$PACKAGE_DIR" ]; then
  echo "Package directory not found: $PACKAGE_DIR" >&2
  exit 3
fi
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must be semver-like, for example 0.2.2" >&2
  exit 4
fi

windows_exe="GunGloryOnline-Launcher-Windows.exe"
linux_deb="GunGloryOnline-Launcher-Ubuntu-Debian.deb"
linux_appimage="GunGloryOnline-Launcher-Linux.AppImage"

for file in "$windows_exe" "$linux_deb" "$linux_appimage"; do
  if [ ! -s "$PACKAGE_DIR/$file" ]; then
    echo "Required launcher package missing: $PACKAGE_DIR/$file" >&2
    exit 5
  fi
done

install -d -m 0755 "$DOWNLOADS" "$CHANNEL_DIR"
for file in "$windows_exe" "$linux_deb" "$linux_appimage"; do
  install -m 0644 "$PACKAGE_DIR/$file" "$DOWNLOADS/$file.new"
  mv -f "$DOWNLOADS/$file.new" "$DOWNLOADS/$file"
done

sha_windows="$(sha256sum "$DOWNLOADS/$windows_exe" | awk '{print $1}')"
sha_deb="$(sha256sum "$DOWNLOADS/$linux_deb" | awk '{print $1}')"
sha_appimage="$(sha256sum "$DOWNLOADS/$linux_appimage" | awk '{print $1}')"

VERSION="$VERSION" NOTES="$NOTES" SHA_WINDOWS="$sha_windows" SHA_DEB="$sha_deb" SHA_APPIMAGE="$sha_appimage" python3 - "$MANIFEST.tmp" <<'PY'
import json
import os
import sys

out = sys.argv[1]
base = "https://ggo.kvicloud.ru/downloads"
payload = {
    "version": os.environ["VERSION"],
    "notes": os.environ["NOTES"],
    "windowsExe": {
        "url": f"{base}/GunGloryOnline-Launcher-Windows.exe",
        "sha256": os.environ["SHA_WINDOWS"],
    },
    "linuxDeb": {
        "url": f"{base}/GunGloryOnline-Launcher-Ubuntu-Debian.deb",
        "sha256": os.environ["SHA_DEB"],
    },
    "linuxAppImage": {
        "url": f"{base}/GunGloryOnline-Launcher-Linux.AppImage",
        "sha256": os.environ["SHA_APPIMAGE"],
    },
}
with open(out, "w", encoding="utf-8") as handle:
    json.dump(payload, handle, indent=2, ensure_ascii=False)
    handle.write("\n")
PY

python3 -m json.tool "$MANIFEST.tmp" >/dev/null
mv -f "$MANIFEST.tmp" "$MANIFEST"
chown -R www-data:www-data "$DOWNLOADS" "$CHANNEL_DIR"
find "$DOWNLOADS" -type f -exec chmod 0644 {} +
find "$CHANNEL_DIR" -type f -exec chmod 0644 {} +

curl --fail --silent --show-error "https://ggo.kvicloud.ru/content/launcher/latest-beta.json" >/tmp/ggo-launcher-update.json
python3 - "$VERSION" <<'PY'
import json
import sys
p=json.load(open('/tmp/ggo-launcher-update.json',encoding='utf-8'))
assert p.get('version') == sys.argv[1], p
for key in ('windowsExe','linuxDeb','linuxAppImage'):
    item=p.get(key) or {}
    assert item.get('url','').startswith('https://ggo.kvicloud.ru/downloads/'), (key,item)
    assert len(item.get('sha256','')) == 64, (key,item)
print('launcher update channel: PASS', p['version'])
PY

echo "Launcher update published: $VERSION"
echo "Manifest: https://ggo.kvicloud.ru/content/launcher/latest-beta.json"
