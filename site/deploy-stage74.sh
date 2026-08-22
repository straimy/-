#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID}" -ne 0 ]; then
  echo "Run as root: sudo ./deploy-stage74.sh <package-dir>" >&2
  exit 1
fi

SITE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_DIR="${1:-}"
NOTES="GGO launcher stability, account persistence and client runtime fixes"

if [ -z "$PACKAGE_DIR" ]; then
  echo "Usage: sudo $0 <package-dir>" >&2
  exit 2
fi
PACKAGE_DIR="$(cd "$PACKAGE_DIR" && pwd)"

for file in \
  GunGloryOnline-Launcher-Windows.exe \
  GunGloryOnline-Launcher-Ubuntu-Debian.deb \
  GunGloryOnline-Launcher-Linux.AppImage; do
  test -s "$PACKAGE_DIR/$file" || {
    echo "Required package missing: $PACKAGE_DIR/$file" >&2
    exit 3
  }
done

VERSION="$(dpkg-deb -f "$PACKAGE_DIR/GunGloryOnline-Launcher-Ubuntu-Debian.deb" Version)"
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid Ubuntu package version: $VERSION" >&2
  exit 4
fi

echo "Publishing GunGloryOnline Launcher $VERSION"
chmod +x "$SITE_DIR/install-site.sh" "$SITE_DIR/publish-launcher-update.sh"
"$SITE_DIR/install-site.sh"
"$SITE_DIR/publish-launcher-update.sh" "$PACKAGE_DIR" "$VERSION" "$NOTES"

curl --fail --silent --show-error "https://ggo.kvicloud.ru/install-launcher.sh" >/tmp/ggo-install-launcher.sh
head -n 1 /tmp/ggo-install-launcher.sh | grep -q '^#!/usr/bin/env bash$'
bash -n /tmp/ggo-install-launcher.sh

curl --fail --silent --show-error "https://ggo.kvicloud.ru/content/launcher/latest-beta.json" >/tmp/ggo-launcher-latest.json
VERSION="$VERSION" python3 - <<'PY'
import json, os
p=json.load(open('/tmp/ggo-launcher-latest.json',encoding='utf-8'))
assert p.get('version') == os.environ['VERSION'], p
for key in ('windowsExe','linuxDeb','linuxAppImage'):
    item=p.get(key) or {}
    assert item.get('url','').startswith('https://ggo.kvicloud.ru/downloads/'), (key,item)
    sha=item.get('sha256','')
    assert len(sha)==64 and all(c in '0123456789abcdef' for c in sha.lower()), (key,sha)
print('Stage 74 public update channel: PASS', p['version'])
PY

echo "Stage 74 launcher update deploy: GREEN ($VERSION)"
echo "Ubuntu update command: curl -fsSL https://ggo.kvicloud.ru/install-launcher.sh | bash"
